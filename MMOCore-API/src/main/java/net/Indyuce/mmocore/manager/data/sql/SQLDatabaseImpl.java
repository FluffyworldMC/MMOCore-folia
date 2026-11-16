package net.Indyuce.mmocore.manager.data.sql;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.UtilityMethods;
import io.lumine.mythic.lib.data.queue.DataLoadResult;
import io.lumine.mythic.lib.data.sql.SQLDatabase;
import io.lumine.mythic.lib.data.sql.UpdateRequestBuilder;
import io.lumine.mythic.lib.gson.JsonArray;
import io.lumine.mythic.lib.gson.JsonElement;
import io.lumine.mythic.lib.gson.JsonObject;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.event.PlayerLevelChangeEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.util.MMOCoreUtils;
import net.Indyuce.mmocore.guild.provided.Guild;
import net.Indyuce.mmocore.manager.data.OfflinePlayerData;
import net.Indyuce.mmocore.skill.ClassSkill;
import net.Indyuce.mmocore.skilltree.SkillTreeNode;
import net.Indyuce.mmocore.skilltree.tree.SkillTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SQLDatabaseImpl extends SQLDatabase<PlayerData, OfflinePlayerData> {
    public static final String UUID_FIELD_NAME = "uuid";
    private final SQLClassData classData = new SQLClassData(this);

    public SQLDatabaseImpl() {
        super(MMOCore.plugin, UUID_FIELD_NAME);
    }

    private static final String[] NEW_COLUMNS = new String[]{
            "classes_migrated", "TINYINT DEFAULT 0",
            "times_claimed", "LONGTEXT",
            "is_saved", "TINYINT",
            "skill_reallocation_points", "INT(11)",
            "skill_tree_reallocation_points", "INT(11)",
            "skill_tree_points", "LONGTEXT",
            "skill_tree_levels", "LONGTEXT",
            "unlocked_items", "LONGTEXT",
            "health", "FLOAT",
            "mana", "FLOAT",
            "stamina", "FLOAT",
            "stellium", "FLOAT"};

    @Override
    public void setup() {
        try {
            setupSQL();
        } catch (SQLException exception) {
            // Continuing with a partial migration could discard inactive legacy classes.
            throw new IllegalStateException("Could not initialize MMOCore SQL class storage", exception);
        }
    }

    @Override
    protected void setupSQL() throws SQLException {
        // Fully create table
        executeUpdate("CREATE TABLE IF NOT EXISTS " + userdataTableName + "("
                + UUID_FIELD_NAME + " VARCHAR(36)," +
                "class_points INT(11) DEFAULT 0," +
                "skill_points INT(11) DEFAULT 0," +
                "attribute_points INT(11) DEFAULT 0," +
                "attribute_realloc_points INT(11) DEFAULT 0," +
                "skill_reallocation_points INT(11) DEFAULT 0," +
                "skill_tree_reallocation_points INT(11) DEFAULT 0," +
                "skill_tree_points LONGTEXT," +
                "skill_tree_levels LONGTEXT," +
                "level INT(11) DEFAULT 1," +
                "experience DOUBLE PRECISION DEFAULT 0," +
                "class VARCHAR(20)," +
                "guild VARCHAR(20)," +
                "last_login LONG," +
                "attributes LONGTEXT," +
                "professions LONGTEXT," +
                "times_claimed LONGTEXT," +
                "quests LONGTEXT," +
                "waypoints LONGTEXT," +
                "friends LONGTEXT," +
                "skills LONGTEXT," +
                "bound_skills LONGTEXT," +
                "health FLOAT," +
                "mana FLOAT," +
                "stamina FLOAT," +
                "stellium FLOAT," +
                "unlocked_items LONGTEXT," +
                "class_info LONGTEXT," +
                "is_saved TINYINT," +
                "PRIMARY KEY (uuid));");

        // Add columns that might not be here by default
        for (int i = 0; i < NEW_COLUMNS.length; i += 2) {
            final var columnName = NEW_COLUMNS[i];
            final var dataType = NEW_COLUMNS[i + 1];
            final boolean[] exists = {false};
            executeQuery("SELECT * FROM `information_schema`.`COLUMNS` WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?", result -> {
                exists[0] = result.next();
            }, databaseName, userdataTableName, columnName);
            if (!exists[0]) executeUpdate("ALTER TABLE " + userdataTableName + " ADD COLUMN " + columnName + " " + dataType);
        }

        // Modify exp to be a double precision instead
        executeUpdate("ALTER TABLE `" + userdataTableName + "` MODIFY COLUMN experience DOUBLE PRECISION");
        classData.setup();
    }

    @Override
    protected @NotNull DataLoadResult loadDataFromResultSet(@NotNull PlayerData playerData, @NotNull ResultSet result, boolean force) throws SQLException {

        // Reset stats linked to triggers
        playerData.resetTriggerStats();

        // Reset stats due to attributes
        playerData.resetAttributeStats();

        playerData.setClassPoints(result.getInt("class_points"));
        playerData.setSkillPoints(result.getInt("skill_points"));
        playerData.setSkillReallocationPoints(result.getInt("skill_reallocation_points"));
        playerData.setSkillTreeReallocationPoints(result.getInt("skill_tree_reallocation_points"));
        playerData.setAttributePoints(result.getInt("attribute_points"));
        playerData.setAttributeReallocationPoints(result.getInt("attribute_realloc_points"));

        // Set class before setting level and exp
        // Class determines max level, so default class may apply the wrong level threshold
        // Fixes https://gitlab.com/phoenix-dvpmt/mmocore/-/issues/1192
        if (!isEmpty(result.getString("class")))
            playerData.setClass(MMOCore.plugin.classManager.get(result.getString("class")));
        playerData.setLevel(result.getInt("level"), PlayerLevelChangeEvent.Reason.CHOOSE_PROFILE);
        playerData.setExperience(result.getDouble("experience"));

        if (!isEmpty(result.getString("times_claimed"))) {
            JsonObject json = MythicLib.plugin.getGson().fromJson(result.getString("times_claimed"), JsonObject.class);
            json.entrySet().forEach(entry -> playerData.getItemClaims().put(entry.getKey(), entry.getValue().getAsInt()));
        }
        if (!isEmpty(result.getString("skill_tree_points"))) {
            JsonObject json = MythicLib.plugin.getGson().fromJson(result.getString("skill_tree_points"), JsonObject.class);
            for (SkillTree skillTree : MMOCore.plugin.skillTreeManager.getAll()) {
                playerData.setSkillTreePoints(skillTree.getId(), json.has(skillTree.getId()) ? json.get(skillTree.getId()).getAsInt() : 0);
            }
            playerData.setSkillTreePoints("global", json.has("global") ? json.get("global").getAsInt() : 0);
        }

        if (!isEmpty(result.getString("skill_tree_levels"))) {
            JsonObject json = MythicLib.plugin.getGson().fromJson(result.getString("skill_tree_levels"), JsonObject.class);
            for (SkillTreeNode skillTreeNode : MMOCore.plugin.skillTreeManager.getAllNodes()) {
                playerData.setNodeLevel(skillTreeNode, json.has(skillTreeNode.getFullId()) ? json.get(skillTreeNode.getFullId()).getAsInt() : 0);
            }
        }
        Set<String> unlockedItems = new HashSet<>();
        if (!isEmpty(result.getString("unlocked_items"))) {
            JsonArray unlockedItemsArray = MythicLib.plugin.getGson().fromJson(result.getString("unlocked_items"), JsonArray.class);
            for (JsonElement item : unlockedItemsArray)
                unlockedItems.add(item.getAsString());
        }
        playerData.setUnlockedItems(unlockedItems);
        if (!isEmpty(result.getString("guild"))) {
            final Guild guild = MMOCore.plugin.nativeGuildManager.getGuild(result.getString("guild"));
            if (guild != null && guild.hasMember(playerData.getUniqueId())) playerData.setGuild(guild);
        }
        if (!isEmpty(result.getString("attributes"))) playerData.getAttributes().load(result.getString("attributes"));
        if (!isEmpty(result.getString("professions")))
            playerData.getCollectionSkills().load(result.getString("professions"));
        if (!isEmpty(result.getString("quests"))) playerData.getQuestData().load(result.getString("quests"));
        playerData.getQuestData().updateBossBar();
        if (!isEmpty(result.getString("waypoints")))
            playerData.getWaypoints().addAll(MMOCoreUtils.jsonArrayToList(result.getString("waypoints")));
        if (!isEmpty(result.getString("friends")))
            MMOCoreUtils.jsonArrayToList(result.getString("friends")).forEach(str -> playerData.getFriends().add(UUID.fromString(str)));
        if (!isEmpty(result.getString("skills"))) {
            JsonObject object = MythicLib.plugin.getGson().fromJson(result.getString("skills"), JsonObject.class);
            for (Map.Entry<String, JsonElement> entry : object.entrySet())
                playerData.setSkillLevel(entry.getKey(), entry.getValue().getAsInt());
        }
        if (!isEmpty(result.getString("bound_skills"))) {
            JsonObject object = MythicLib.plugin.getGson().fromJson(result.getString("bound_skills"), JsonObject.class);
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                ClassSkill skill = playerData.getProfess().getSkill(entry.getValue().getAsString());
                if (skill != null) playerData.bindSkill(Integer.parseInt(entry.getKey()), skill);
            }
        }

        /*
         * These should be loaded after to make sure that the
         * MAX_MANA, MAX_STAMINA & MAX_STELLIUM stats are already loaded.
         */
        playerData.loadResources(result.getDouble("health"), result.getDouble("mana"), result.getDouble("stamina"), result.getDouble("stellium"));
        classData.load(playerData, result.getStatement().getConnection());

        UtilityMethods.debug(MMOCore.plugin, "SQL", String.format("{ class: %s, level: %d }", playerData.getProfess().getId(), playerData.getLevel()));
        return new DataLoadResult(false, force);
    }

    @Override
    protected void setupSaveRequest(@NotNull PlayerData playerData, @NotNull UpdateRequestBuilder<PlayerData> builder) {
        builder.appendInt("classes_migrated", 1);
        builder.appendInt("class_points", playerData.getClassPoints());
        builder.appendInt("skill_points", playerData.getSkillPoints());
        builder.appendInt("skill_reallocation_points", playerData.getSkillReallocationPoints());
        builder.appendInt("attribute_points", playerData.getAttributePoints());
        builder.appendInt("attribute_realloc_points", playerData.getAttributeReallocationPoints());
        builder.appendInt("skill_tree_reallocation_points", playerData.getSkillTreeReallocationPoints());
        builder.appendDouble("health", playerData.getLastHealth());
        builder.appendDouble("mana", playerData.getMana());
        builder.appendDouble("stellium", playerData.getStellium());
        builder.appendDouble("stamina", playerData.getStamina());
        builder.appendInt("level", playerData.getLevel());
        builder.appendDouble("experience", playerData.getExperience());
        builder.appendString("class", playerData.getProfess().getId());
        builder.appendLong("last_login", playerData.getLastLogin());
        builder.appendString("guild", playerData.hasGuild() ? playerData.getGuild().getId() : null);
        builder.appendCollection("waypoints", playerData.getWaypoints());
        builder.appendCollection("friends", playerData.getFriends());
        builder.appendObject("bound_skills", playerData.mapBoundSkills());
        builder.appendObject("skills", playerData.mapSkillLevels());
        builder.appendObject("times_claimed", playerData.getItemClaims());
        builder.appendObject("skill_tree_points", playerData.mapSkillTreePoints());
        builder.appendObject("skill_tree_levels", playerData.getNodeLevels());
        builder.appendString("attributes", playerData.getAttributes().toJson().toString());
        builder.appendString("professions", playerData.getCollectionSkills().toJsonString());
        builder.appendString("quests", playerData.getQuestData().toJsonString());
        builder.appendCollection("unlocked_items", playerData.getUnlockedItems());

        UtilityMethods.debug(MMOCore.plugin, "SQL", String.format("{ class: %s, level: %d }", playerData.getProfess().getId(), playerData.getLevel()));
    }

    public void startLiveSync() {
        classData.start();
    }

    public java.util.List<net.Indyuce.mmocore.comp.placeholder.ClassRanking.Entry> getClassLeaderboard(String classId, int limit) throws SQLException {
        return classData.leaderboard(classId, limit);
    }

    @Override
    public void saveData(PlayerData playerData, io.lumine.mythic.lib.profile.SessionUpdateReason reason) {
        try {
            // Save class rows before releasing the cross-server session in the main table.
            classData.save(playerData);
            super.saveData(playerData, reason);
            if (reason != io.lumine.mythic.lib.profile.SessionUpdateReason.AUTOSAVE) classData.forget(playerData);
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save class progression for " + playerData.getEffectiveId(), exception);
        }
    }

    @Override
    public void close() {
        classData.close();
        super.close();
    }

    private boolean isEmpty(@Nullable String str) {
        return str == null
                || str.isEmpty()
                || str.equalsIgnoreCase("null")
                || str.equalsIgnoreCase("{}")
                || str.equalsIgnoreCase("[]");
    }

    @NotNull
    @Override
    public OfflinePlayerData getOffline(UUID uuid) {
        return new SQLOfflinePlayerData(uuid);
    }
}



