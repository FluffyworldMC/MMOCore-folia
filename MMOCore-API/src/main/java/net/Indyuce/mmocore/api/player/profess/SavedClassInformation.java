package net.Indyuce.mmocore.api.player.profess;

import io.lumine.mythic.lib.gson.JsonElement;
import io.lumine.mythic.lib.gson.JsonObject;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.event.PlayerLevelChangeEvent;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.attribute.PlayerAttribute;
import net.Indyuce.mmocore.player.ClassDataContainer;
import net.Indyuce.mmocore.skill.RegisteredSkill;
import net.Indyuce.mmocore.skilltree.SkillTreeNode;
import net.Indyuce.mmocore.skilltree.tree.SkillTree;
import net.Indyuce.mmocore.manager.data.sql.SQLDatabaseImpl;
import io.lumine.mythic.lib.profile.SessionUpdateReason;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;

public class SavedClassInformation implements ClassDataContainer {
    private final int level, skillPoints, attributePoints, attributeReallocationPoints, skillTreeReallocationPoints, skillReallocationPoints;
    private final double experience, health, mana, stellium, stamina;
    private final Map<String, Integer> attributeLevels = new HashMap<>();
    private final Map<String, Integer> skillLevels = new HashMap<>();
    private final Map<String, Integer> skillTreePoints = new HashMap<>();
    private final Map<String, Integer> nodeLevels = new HashMap<>();
    private final Map<String, Integer> nodeTimesClaimed = new HashMap<>();
    private final Map<Integer, String> boundSkills = new HashMap<>();
    private final Set<String> unlockedItems = new HashSet<>();

    /**
     * Used by YAML storage
     */
    public SavedClassInformation(ConfigurationSection config) {
        level = config.getInt("level");
        experience = config.getDouble("experience");
        skillPoints = config.getInt("skill-points");
        attributePoints = config.getInt("attribute-points");
        attributeReallocationPoints = config.getInt("attribute-realloc-points");
        skillReallocationPoints = config.getInt("skill-reallocation-points");
        skillTreeReallocationPoints = config.getInt("skill-tree-reallocation-points");
        health = config.getDouble("health", 20);
        mana = config.getDouble("mana", 0);
        stamina = config.getDouble("stamina", 0);
        stellium = config.getDouble("stellium", 0);
        if (config.contains("attribute"))
            config.getConfigurationSection("attribute").getKeys(false)
                    .forEach(key -> attributeLevels.put(key, config.getInt("attribute." + key)));
        if (config.contains("skill"))
            config.getConfigurationSection("skill").getKeys(false)
                    .forEach(key -> skillLevels.put(key, config.getInt("skill." + key)));
        if (config.contains("skill-tree-points"))
            config.getConfigurationSection("skill-tree-points").getKeys(false)
                    .forEach(key -> skillTreePoints.put(key, config.getInt("skill-tree-points." + key)));
        if (config.contains("node-levels"))
            config.getConfigurationSection("node-levels").getKeys(false)
                    .forEach(key -> nodeLevels.put(key, config.getInt("node-levels." + key)));
        if (config.contains("node-times-claimed"))
            config.getConfigurationSection("node-times-claimed").getKeys(false)
                    .forEach(key -> nodeTimesClaimed.put(key, config.getInt("node-times-claimed." + key)));

        /**
         * 'bound-skills' used to be a list. This condition makes
         * sure that the config is using the newest format.
         */
        if (config.isConfigurationSection("bound-skills"))
            config.getConfigurationSection("bound-skills").getKeys(false)
                    .forEach(key -> boundSkills.put(Integer.parseInt(key), config.getString("bound-skills." + key)));
        unlockedItems.addAll(config.getStringList("unlocked-items"));
    }

    /**
     * Used by SQL storage
     */
    public SavedClassInformation(JsonObject json) {
        level = json.get("level").getAsInt();
        experience = json.get("experience").getAsDouble();
        skillPoints = json.get("skill-points").getAsInt();
        attributePoints = json.get("attribute-points").getAsInt();
        attributeReallocationPoints = json.get("attribute-realloc-points").getAsInt();
        skillReallocationPoints = json.get("skill-reallocation-points").getAsInt();
        skillTreeReallocationPoints = json.get("skill-tree-reallocation-points").getAsInt();
        health = json.has("health") ? json.get("health").getAsDouble() : 20;
        mana = json.has("mana") ? json.get("mana").getAsDouble() : 0;
        stamina = json.has("stamina") ? json.get("stamina").getAsDouble() : 0;
        stellium = json.has("stellium") ? json.get("stellium").getAsDouble() : 0;

        if (json.has("attribute"))
            for (Entry<String, JsonElement> entry : json.getAsJsonObject("attribute").entrySet())
                attributeLevels.put(entry.getKey(), entry.getValue().getAsInt());
        if (json.has("skill"))
            for (Entry<String, JsonElement> entry : json.getAsJsonObject("skill").entrySet())
                skillLevels.put(entry.getKey(), entry.getValue().getAsInt());
        if (json.has("skill-tree-points"))
            for (Entry<String, JsonElement> entry : json.getAsJsonObject("skill-tree-points").entrySet())
                skillTreePoints.put(entry.getKey(), entry.getValue().getAsInt());
        if (json.has("node-levels"))
            for (Entry<String, JsonElement> entry : json.getAsJsonObject("node-levels").entrySet())
                nodeLevels.put(entry.getKey(), entry.getValue().getAsInt());
        if (json.has("node-times-claimed"))
            for (Entry<String, JsonElement> entry : json.getAsJsonObject("node-times-claimed").entrySet())
                nodeTimesClaimed.put(entry.getKey(), entry.getValue().getAsInt());
        //Old system was using a JsonArray. If it saved with the old system the if condition won't be respected.
        if (json.has("bound-skills") && json.get("bound-skills").isJsonObject())
            for (Entry<String, JsonElement> entry : json.getAsJsonObject("bound-skills").entrySet())
                boundSkills.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsString());
        if (json.has("unlocked-items"))
            for (JsonElement unlockedItem : json.get("unlocked-items").getAsJsonArray())
                unlockedItems.add(unlockedItem.getAsString());
    }

    public SavedClassInformation(ClassDataContainer data) {
        this.level = data.getLevel();
        this.skillPoints = data.getSkillPoints();
        this.attributePoints = data.getAttributePoints();
        this.attributeReallocationPoints = data.getAttributeReallocationPoints();
        this.skillTreeReallocationPoints = data.getSkillTreeReallocationPoints();
        this.skillReallocationPoints = data.getSkillReallocationPoints();
        this.experience = data.getExperience();
        this.health = data instanceof PlayerData && ((PlayerData) data).isSessionReady()
                && org.bukkit.Bukkit.isOwnedByCurrentRegion(((PlayerData) data).getPlayer())
                ? ((PlayerData) data).getPlayer().getHealth() : data.getLastHealth();
        this.mana = data.getMana();
        this.stellium = data.getStellium();
        this.stamina = data.getStamina();

        attributeLevels.putAll(data.mapAttributeLevels());
        skillLevels.putAll(data.mapSkillLevels());
        skillTreePoints.putAll(data.mapSkillTreePoints());
        nodeLevels.putAll(data.getNodeLevels());
        nodeTimesClaimed.putAll(data.getNodeTimesClaimed());
        boundSkills.putAll(data.mapBoundSkills());
        unlockedItems.addAll(data.getUnlockedItems());
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public double getExperience() {
        return experience;
    }

    @Override
    public int getSkillPoints() {
        return skillPoints;
    }

    @Override
    public int getAttributePoints() {
        return attributePoints;
    }

    @Override
    public int getAttributeReallocationPoints() {
        return attributeReallocationPoints;
    }

    @Override
    public double getLastHealth() {
        return health;
    }

    @Override
    public double getMana() {
        return mana;
    }

    @Override
    public double getStellium() {
        return stellium;
    }

    @Override
    public double getStamina() {
        return stamina;
    }

    public Set<String> getSkillKeys() {
        return skillLevels.keySet();
    }

    public int getSkillLevel(RegisteredSkill skill) {
        return getSkillLevel(skill.getHandler().getId());
    }

    public int getSkillLevel(String id) {
        return skillLevels.get(id);
    }

    public void registerSkillLevel(RegisteredSkill skill, int level) {
        registerSkillLevel(skill.getHandler().getId(), level);
    }

    @Override
    public Map<String, Integer> mapSkillLevels() {
        return skillLevels;
    }

    @Override
    public Map<String, Integer> mapSkillTreePoints() {
        return skillTreePoints;
    }

    @Deprecated
    public Map<Integer, String> getBoundSkills() {
        return mapBoundSkills();
    }

    @Override
    public int getSkillTreeReallocationPoints() {
        return skillTreeReallocationPoints;
    }

    @Override
    public int getSkillReallocationPoints() {
        return skillReallocationPoints;
    }

    public void registerSkillLevel(String attribute, int level) {
        skillLevels.put(attribute, level);
    }

    public Set<String> getNodeKeys() {
        return nodeLevels.keySet();
    }

    public int getNodeLevel(String node) {
        return nodeLevels.get(node);
    }

    @Override
    public Map<String, Integer> mapAttributeLevels() {
        return attributeLevels;
    }

    @Override
    public Map<Integer, String> mapBoundSkills() {
        return boundSkills;
    }

    @Override
    public Map<String, Integer> getNodeLevels() {
        return nodeLevels;
    }

    @Override
    public Map<String, Integer> getNodeTimesClaimed() {
        return nodeTimesClaimed;
    }

    public Set<String> getSkillTreePointsKeys() {
        return skillTreePoints.keySet();
    }

    public int getSkillTreePoints(String skillTreeId) {
        return skillTreePoints.get(skillTreeId);
    }

    public Set<String> getAttributeKeys() {
        return attributeLevels.keySet();
    }

    public int getAttributeLevel(String id) {
        return attributeLevels.getOrDefault(id, 0);
    }

    public void registerAttributeLevel(PlayerAttribute attribute, int level) {
        registerAttributeLevel(attribute.getId(), level);
    }

    public void registerAttributeLevel(String attribute, int level) {
        attributeLevels.put(attribute, level);
    }

    @Override
    public Set<String> getUnlockedItems() {
        return unlockedItems;
    }

    /**
     * @param profess Target player class
     * @param player  Player changing class
     */
    public void load(@NotNull PlayerClass profess, @NotNull PlayerData player) {
        load(profess, player, true);
    }

    /** Restores database state without saving the outgoing class or sharing its progression. */
    public void restore(@NotNull PlayerClass profess, @NotNull PlayerData player) {
        load(profess, player, false);
    }

    private void load(@NotNull PlayerClass profess, @NotNull PlayerData player, boolean changingClass) {

        // TODO check all classes instead?
        // big hypothesis => last class is always the one with the highest progression
        @Nullable SavedClassInformation lastClassPlayed = null;
        final boolean independentSQL = MMOCore.plugin.playerDataManager.getDatabase() instanceof SQLDatabaseImpl;
        final boolean shareProgress = changingClass && !independentSQL;

        /*
         * Saves current class info inside a SavedClassInformation, only
         * if the class is a real class and not the default one.
         */
        if (changingClass && (independentSQL || !player.getProfess().hasOption(ClassOption.DEFAULT) || MMOCore.plugin.configManager.saveDefaultClassInfo))
            player.applyClassInfo(player.getProfess(), lastClassPlayed = new SavedClassInformation(player));

        ///////////////////////////////////////////////
        // Reset player data
        ///////////////////////////////////////////////

        player.getProfess().resetAdvancement(player, false); // Reset class exp table
        player.resetSkills(); // Reset skills
        // Reset player attributes
        for (PlayerAttribute attribute : MMOCore.plugin.attributeManager.getAll()) {
            attribute.resetAdvancement(player, false);
            player.getAttributes().getInstance(attribute).setBase(0);
        }
        player.resetSkillTrees(); // Reset skill trees

        ///////////////////////////////////////////////
        // Patch player data
        ///////////////////////////////////////////////

        final var conf = MMOCore.plugin.configManager;
        final var targetLevel = shareProgress && lastClassPlayed != null && conf.shareExp
                ? lastClassPlayed.level
                : level;
        final var targetExp = shareProgress && lastClassPlayed != null && conf.shareExp
                ? lastClassPlayed.experience
                : experience;
        final var targetSkillPoints = shareProgress && lastClassPlayed != null && conf.shareSkillPts
                ? lastClassPlayed.skillPoints + lastClassPlayed.countSpentSkillPoints() - this.countSpentSkillPoints()
                : this.skillPoints;
        final var targetAttributePoints = shareProgress && lastClassPlayed != null && conf.shareAttributePts
                ? lastClassPlayed.attributePoints + lastClassPlayed.countSpentAttributePoints() - this.countSpentAttributePoints()
                : this.attributePoints;
        final var targetSkillReallocationPoints = shareProgress && lastClassPlayed != null && conf.shareSkillReallocPts
                ? lastClassPlayed.skillReallocationPoints
                : this.skillReallocationPoints;
        final var targetAttributeReallocationPoints = shareProgress && lastClassPlayed != null && conf.shareAttributeReallocPts
                ? lastClassPlayed.attributeReallocationPoints
                : this.attributeReallocationPoints;

        ///////////////////////////////////////////////
        // Apply player data
        ///////////////////////////////////////////////

        // Class needs to be set before level, otherwise
        // max level differences may break the previous class level
        // Fixes https://gitlab.com/phoenix-dvpmt/mmocore/-/work_items/1213
        player.setClass(profess);
        player.setLevel(targetLevel, changingClass ? PlayerLevelChangeEvent.Reason.CHOOSE_CLASS : PlayerLevelChangeEvent.Reason.CHOOSE_PROFILE);
        player.setExperience(targetExp);
        player.setSkillPoints(targetSkillPoints);
        player.setAttributePoints(targetAttributePoints);
        player.setSkillReallocationPoints(targetSkillReallocationPoints);
        player.setAttributeReallocationPoints(targetAttributeReallocationPoints);
        player.setSkillTreeReallocationPoints(skillTreeReallocationPoints);
        player.setUnlockedItems(unlockedItems);
        for (int slot : boundSkills.keySet()) {
            final var skill = profess.getSkill(boundSkills.get(slot));
            if (skill == null) {
                MMOCore.plugin.getLogger().log(Level.WARNING, "Tried binding skill '" + boundSkills.get(slot) + "' to slot " + slot + " for player " + player.getPlayer().getName() + ", but skill no longer exists in class " + profess.getId());
                continue;
            }

            player.bindSkill(slot, skill);
        }

        skillLevels.forEach(player::setSkillLevel);
        attributeLevels.forEach((id, pts) -> player.getAttributes().getInstance(id).setBase(pts));

        // Careful, the global points must not be forgotten.
        player.setSkillTreePoints("global", skillTreePoints.getOrDefault("global", 0));
        for (SkillTree skillTree : profess.getSkillTrees()) {
            player.setSkillTreePoints(skillTree.getId(), skillTreePoints.getOrDefault(skillTree.getId(), 0));
            for (SkillTreeNode node : skillTree.getNodes())
                player.setNodeLevel(node, nodeLevels.getOrDefault(node.getFullId(), 0));

            skillTree.resolveStates(player);
        }

        // Add the values to the times claimed table and claims the corresponding stat triggers.
        nodeTimesClaimed.forEach(player::setClaims);

        // Unload current class information
        player.unloadClassInfo(profess);

        // This needs to be done at the end to make sure the MAX_HEALTH/MAX_MANA/... stats are loaded.
        player.loadResources(health, mana, stamina, stellium);
        player.applyTemporaryTriggers();
        player.getStats().updateStats();
        if (changingClass && independentSQL && player.isSessionReady())
            MMOCore.plugin.playerDataManager.saveData(player, SessionUpdateReason.AUTOSAVE);
    }

    //region Player data progress synchronizer

    private int countSpentSkillPoints() {
        // Skill levels, not points spent, hence the -1's
        return this.skillLevels.values().stream().map(i -> i - 1).mapToInt(Integer::intValue).sum();
    }

    private int countSpentAttributePoints() {
        return this.attributeLevels.values().stream().mapToInt(Integer::intValue).sum();
    }

    // TODO skill trees? not used in SAO for now

    //endregion
}

