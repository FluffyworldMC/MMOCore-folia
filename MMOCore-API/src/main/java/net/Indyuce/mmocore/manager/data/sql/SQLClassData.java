package net.Indyuce.mmocore.manager.data.sql;

import io.lumine.mythic.lib.MythicLib;
import io.lumine.mythic.lib.gson.JsonObject;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.Indyuce.mmocore.MMOCore;
import net.Indyuce.mmocore.api.player.PlayerData;
import net.Indyuce.mmocore.api.player.profess.SavedClassInformation;
import net.Indyuce.mmocore.util.Tasks;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

/** Normalized class persistence and external-edit synchronization. */
final class SQLClassData {
    private final SQLDatabaseImpl database;
    private final String table;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private ScheduledTask pollTask;

    SQLClassData(SQLDatabaseImpl database) {
        this.database = database;
        table = database.getUserDataTableName() + "_classes";
        if (!table.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid SQL table name: " + table);
    }

    void setup() throws SQLException {
        String columns = ClassProgress.FIELDS.keySet().stream()
                .map(column -> "`" + column + "` " + ClassProgress.type(column)).collect(Collectors.joining(","));
        database.executeUpdate("CREATE TABLE IF NOT EXISTS `" + table + "` (uuid VARCHAR(36) NOT NULL,"
                + "class_id VARCHAR(100) NOT NULL,player_name VARCHAR(64)," + columns + ", PRIMARY KEY(uuid,class_id)) ENGINE=InnoDB");
        final boolean[] hasName = {false}, hasIndex = {false};
        database.executeQuery("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND COLUMN_NAME='player_name'",
                result -> hasName[0] = result.next(), database.getDatabaseName(), table);
        if (!hasName[0]) database.executeUpdate("ALTER TABLE `" + table + "` ADD COLUMN player_name VARCHAR(64)");
        database.executeQuery("SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=? AND TABLE_NAME=? AND INDEX_NAME='class_level_ranking'",
                result -> hasIndex[0] = result.next(), database.getDatabaseName(), table);
        if (!hasIndex[0]) database.executeUpdate("CREATE INDEX class_level_ranking ON `" + table + "` (class_id,level,experience,uuid)");
        // Each legacy player is migrated once. Existing normalized records always take precedence.
        try (Connection connection = database.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM `" + database.getUserDataTableName()
                     + "` WHERE classes_migrated IS NULL OR classes_migrated=0");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) migrate(connection, result);
        }
    }

    private void migrate(Connection connection, ResultSet result) throws SQLException {
        UUID uuid = UUID.fromString(result.getString("uuid"));
        connection.setAutoCommit(false);
        try {
            String legacy = result.getString("class_info");
            if (legacy != null && !legacy.isEmpty() && !legacy.equals("null")) {
                JsonObject saved = MythicLib.plugin.getGson().fromJson(legacy, JsonObject.class);
                for (var entry : saved.entrySet())
                    if (!entry.getKey().equals(result.getString("class")))
                        ClassProgress.insert(connection, table, uuid, entry.getKey(), entry.getValue().getAsJsonObject());
            }
            String activeClass = result.getString("class");
            if (activeClass != null && !activeClass.isEmpty()) {
                JsonObject active = new JsonObject();
                for (var field : ClassProgress.FIELDS.entrySet()) {
                    String column = field.getKey();
                    if (column.equals("node_levels")) column = "skill_tree_levels";
                    if (column.equals("node_times_claimed")) column = "times_claimed";
                    String raw = result.getString(column);
                    if (ClassProgress.type(field.getKey()).startsWith("LONGTEXT")) {
                        var value = MythicLib.plugin.getGson().fromJson(raw == null || raw.isEmpty() || raw.equals("null")
                                ? (column.equals("unlocked_items") ? "[]" : "{}") : raw, io.lumine.mythic.lib.gson.JsonElement.class);
                        if (column.equals("times_claimed")) {
                            JsonObject nodeClaims = new JsonObject();
                            for (var claim : value.getAsJsonObject().entrySet())
                                if (claim.getKey().startsWith(net.Indyuce.mmocore.skilltree.SkillTreeNode.KEY_PREFIX))
                                    nodeClaims.add(claim.getKey(), claim.getValue());
                            value = nodeClaims;
                        }
                        active.add(field.getValue(), value);
                    }
                    else if (ClassProgress.type(field.getKey()).startsWith("INT"))
                        active.addProperty(field.getValue(), result.getInt(column));
                    else active.addProperty(field.getValue(), result.getDouble(column));
                }
                ClassProgress.insert(connection, table, uuid, activeClass, active);
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + database.getUserDataTableName()
                    + "` SET classes_migrated=1 WHERE uuid=?")) {
                statement.setString(1, uuid.toString());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (Exception exception) {
            connection.rollback();
            throw new SQLException("Could not migrate classes for " + uuid, exception);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private Map<String, JsonObject> read(UUID uuid) throws SQLException {
        try (Connection connection = database.getConnection()) {
            return read(connection, uuid);
        }
    }

    private Map<String, JsonObject> read(Connection connection, UUID uuid) throws SQLException {
        Map<String, JsonObject> rows = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM `" + table + "` WHERE uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.put(result.getString("class_id"), ClassProgress.read(result));
            }
        }
        return rows;
    }

    void load(PlayerData player, Connection connection) throws SQLException {
        if (!player.getMMOPlayerData().isLookup())
            saveName(connection, player);
        Map<String, JsonObject> rows = read(connection, player.getEffectiveId());
        State state = new State(player, rows);
        if (!player.getMMOPlayerData().isLookup()) states.put(player.getEffectiveId(), state);
        for (String id : new java.util.HashSet<>(player.getSavedClasses())) player.unloadClassInfo(id);
        for (var entry : rows.entrySet()) {
            var profess = MMOCore.plugin.classManager.get(entry.getKey());
            if (profess == null) continue;
            SavedClassInformation info = new SavedClassInformation(entry.getValue());
            if (entry.getKey().equals(player.getProfess().getId())) info.restore(profess, player);
            else player.applyClassInfo(profess, info);
        }
    }

    void save(PlayerData player) throws SQLException {
        State state = state(player);
        synchronized (state) {
            Map<String, JsonObject> current = snapshot(player);
            try (Connection connection = database.getConnection()) {
                for (var entry : current.entrySet()) {
                    JsonObject previous = state.rows.get(entry.getKey());
                    if (previous == null) {
                        ClassProgress.insert(connection, table, player.getEffectiveId(), entry.getKey(), entry.getValue());
                        // Use the attempted insert as baseline: an existing DB row will be detected by polling.
                        state.rows.put(entry.getKey(), entry.getValue());
                    } else state.rows.put(entry.getKey(), ClassProgress.save(connection, table,
                            player.getEffectiveId(), entry.getKey(), previous, entry.getValue()));
                }
                saveName(connection, player);
                state.version++;
            }
        }
    }

    private void saveName(Connection connection, PlayerData player) throws SQLException {
        // MythicLib retains this string after logout; getPlayer() can throw during an async save.
        String name = player.getMMOPlayerData().getPlayerName();
        try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + table + "` SET player_name=? WHERE uuid=?")) {
            statement.setString(1, name);
            statement.setString(2, player.getEffectiveId().toString());
            statement.executeUpdate();
        }
    }

    java.util.List<net.Indyuce.mmocore.comp.placeholder.ClassRanking.Entry> leaderboard(String classId, int limit) throws SQLException {
        try (Connection connection = database.getConnection()) {
            return ClassProgress.leaderboard(connection, table, classId, limit);
        }
    }

    private State state(PlayerData player) {
        return states.compute(player.getEffectiveId(), (uuid, old) -> old != null && old.owner == player
                ? old : new State(player, new HashMap<>()));
    }

    private Map<String, JsonObject> snapshot(PlayerData player) {
        Map<String, JsonObject> current = new HashMap<>();
        for (String id : player.getSavedClasses()) current.put(id, ClassProgress.snapshot(player.getClassInfo(id)));
        current.put(player.getProfess().getId(), ClassProgress.snapshot(player));
        return current;
    }

    void start() {
        long interval = Math.max(1, MMOCore.plugin.getConfig().getLong("mysql.live-sync-interval", 1)) * 20L;
        if (!MMOCore.plugin.getConfig().getBoolean("mysql.live-sync", true)) return;
        pollTask = Tasks.runForEachPlayer(interval, interval, player -> {
            PlayerData data = MMOCore.plugin.playerDataManager.getOrNull(player);
            if (data == null || !data.isSessionReady()) return;
            State state = state(data);
            if (!state.pending.compareAndSet(false, true)) return;
            long version;
            synchronized (state) { version = state.version; }
            Bukkit.getAsyncScheduler().runNow(MMOCore.plugin, task -> {
                try {
                    Map<String, JsonObject> rows = read(data.getEffectiveId());
                    if (player.getScheduler().run(MMOCore.plugin, scheduled -> {
                        try {
                            synchronized (state) {
                                if (!data.isSessionReady() || MMOCore.plugin.playerDataManager.getOrNull(player) != data
                                        || states.get(data.getEffectiveId()) != state || state.version != version) return;
                                apply(data, state, rows);
                            }
                        } catch (Exception exception) {
                            MMOCore.plugin.getLogger().log(Level.WARNING, "Could not apply SQL class edits for " + data.getEffectiveId(), exception);
                        } finally { state.pending.set(false); }
                    }, () -> state.pending.set(false)) == null) state.pending.set(false);
                } catch (Exception exception) {
                    state.pending.set(false);
                    MMOCore.plugin.getLogger().log(Level.WARNING, "Could not poll SQL class edits for " + data.getEffectiveId(), exception);
                }
            });
        });
    }

    private void apply(PlayerData player, State state, Map<String, JsonObject> rows) {
        Map<String, JsonObject> live = snapshot(player);
        // lastHealth is a save-time cache. A live EXP/point edit must not heal the player.
        live.get(player.getProfess().getId()).addProperty("health", player.getPlayer().getHealth());
        for (var entry : rows.entrySet()) {
            String id = entry.getKey();
            JsonObject previous = state.rows.get(id), incoming = entry.getValue();
            if (incoming.equals(previous)) continue;
            var profess = MMOCore.plugin.classManager.get(id);
            if (profess == null) continue;
            JsonObject merged = previous == null || !live.containsKey(id) ? incoming
                    : ClassProgress.merge(live.get(id), previous, incoming);
            SavedClassInformation info = new SavedClassInformation(merged);
            if (id.equals(player.getProfess().getId())) info.restore(profess, player);
            else player.applyClassInfo(profess, info);
            state.rows.put(id, incoming);
        }
    }

    void forget(PlayerData player) {
        State state = states.get(player.getEffectiveId());
        if (state != null && state.owner == player) states.remove(player.getEffectiveId(), state);
    }

    void close() {
        Tasks.cancel(pollTask);
        states.clear();
    }

    private static final class State {
        final PlayerData owner;
        final Map<String, JsonObject> rows;
        final AtomicBoolean pending = new AtomicBoolean();
        long version;
        State(PlayerData owner, Map<String, JsonObject> rows) {
            this.owner = owner;
            this.rows = rows;
        }
    }
}
