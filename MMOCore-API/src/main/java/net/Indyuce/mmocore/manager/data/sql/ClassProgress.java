package net.Indyuce.mmocore.manager.data.sql;

import io.lumine.mythic.lib.gson.Gson;
import io.lumine.mythic.lib.gson.JsonElement;
import io.lumine.mythic.lib.gson.JsonObject;
import net.Indyuce.mmocore.player.ClassDataContainer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** JDBC representation of one (profile UUID, class) record. */
final class ClassProgress {
    private static final Gson GSON = new Gson();

    static java.util.List<net.Indyuce.mmocore.comp.placeholder.ClassRanking.Entry> leaderboard(
            Connection connection, String table, String classId, int limit) throws SQLException {
        java.util.List<net.Indyuce.mmocore.comp.placeholder.ClassRanking.Entry> entries = new java.util.ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT uuid,player_name,level,experience FROM `" + table
                + "` WHERE class_id=? ORDER BY level DESC,experience DESC,uuid ASC LIMIT ?")) {
            statement.setString(1, classId);
            statement.setInt(2, Math.max(1, limit));
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) entries.add(new net.Indyuce.mmocore.comp.placeholder.ClassRanking.Entry(
                        UUID.fromString(result.getString("uuid")), result.getString("player_name"),
                        result.getInt("level"), result.getDouble("experience")));
            }
        }
        return java.util.Collections.unmodifiableList(entries);
    }
    // SQL column -> SavedClassInformation JSON key. Scalar values remain editable SQL numbers.
    static final Map<String, String> FIELDS = new LinkedHashMap<>();
    static {
        for (String key : new String[]{"level", "experience", "skill-points", "attribute-points",
                "attribute-realloc-points", "skill-reallocation-points", "skill-tree-reallocation-points",
                "health", "mana", "stamina", "stellium", "attributes", "skills", "bound-skills",
                "skill-tree-points", "node-levels", "node-times-claimed", "unlocked-items"})
            FIELDS.put(key.replace('-', '_'), key);
        FIELDS.put("attributes", "attribute");
        FIELDS.put("skills", "skill");
    }

    static JsonObject snapshot(ClassDataContainer data) {
        JsonObject json = new JsonObject();
        json.addProperty("level", data.getLevel());
        json.addProperty("experience", data.getExperience());
        json.addProperty("skill-points", data.getSkillPoints());
        json.addProperty("attribute-points", data.getAttributePoints());
        json.addProperty("attribute-realloc-points", data.getAttributeReallocationPoints());
        json.addProperty("skill-reallocation-points", data.getSkillReallocationPoints());
        json.addProperty("skill-tree-reallocation-points", data.getSkillTreeReallocationPoints());
        json.addProperty("health", data.getLastHealth());
        json.addProperty("mana", data.getMana());
        json.addProperty("stamina", data.getStamina());
        json.addProperty("stellium", data.getStellium());
        json.add("attribute", GSON.toJsonTree(data.mapAttributeLevels()));
        json.add("skill", GSON.toJsonTree(data.mapSkillLevels()));
        json.add("bound-skills", GSON.toJsonTree(data.mapBoundSkills()));
        json.add("skill-tree-points", GSON.toJsonTree(data.mapSkillTreePoints()));
        json.add("node-levels", GSON.toJsonTree(data.getNodeLevels()));
        json.add("node-times-claimed", GSON.toJsonTree(data.getNodeTimesClaimed()));
        json.add("unlocked-items", GSON.toJsonTree(data.getUnlockedItems()));
        return json;
    }

    static String type(String column) {
        if (column.equals("experience")) return "DOUBLE NOT NULL DEFAULT 0";
        if (column.equals("health") || column.equals("mana") || column.equals("stamina") || column.equals("stellium"))
            return "DOUBLE NOT NULL DEFAULT 0";
        if (column.equals("level")) return "INT NOT NULL DEFAULT 1";
        if (column.equals("skill_points") || column.equals("attribute_points") || column.equals("attribute_realloc_points")
                || column.equals("skill_reallocation_points") || column.equals("skill_tree_reallocation_points"))
            return "INT NOT NULL DEFAULT 0";
        return "LONGTEXT";
    }

    static JsonObject read(ResultSet result) throws SQLException {
        JsonObject json = new JsonObject();
        for (var field : FIELDS.entrySet()) {
            String column = field.getKey(), key = field.getValue();
            String value = result.getString(column);
            if (type(column).startsWith("LONGTEXT"))
                json.add(key, GSON.fromJson(value == null || value.isEmpty() || value.equals("null")
                        ? (column.equals("unlocked_items") ? "[]" : "{}") : value, JsonElement.class));
            else if (type(column).startsWith("INT")) json.addProperty(key, result.getInt(column));
            else json.addProperty(key, result.getDouble(column));
        }
        return json;
    }

    static void bind(PreparedStatement statement, int index, JsonElement value) throws SQLException {
        if (value == null || value.isJsonNull()) statement.setObject(index, null);
        else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber())
            statement.setBigDecimal(index, value.getAsBigDecimal());
        else statement.setString(index, value.toString());
    }

    static void insert(Connection connection, String table, UUID uuid, String classId, JsonObject data) throws SQLException {
        String columns = FIELDS.keySet().stream().map(s -> "`" + s + "`").collect(Collectors.joining(","));
        String placeholders = FIELDS.keySet().stream().map(s -> "?").collect(Collectors.joining(","));
        // Duplicate rows are deliberately left untouched during migration and first saves.
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO `" + table
                + "` (uuid,class_id," + columns + ") VALUES (?, ?," + placeholders
                + ") ON DUPLICATE KEY UPDATE uuid=VALUES(uuid)")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, classId);
            int i = 3;
            for (String key : FIELDS.values()) {
                JsonElement value = data.get(key);
                if (value == null) {
                    if (key.equals("unlocked-items")) value = new io.lumine.mythic.lib.gson.JsonArray();
                    else if (type(key.replace('-', '_')).startsWith("LONGTEXT")) value = new JsonObject();
                    else value = new io.lumine.mythic.lib.gson.JsonPrimitive(key.equals("level") ? 1 : 0);
                }
                bind(statement, i++, value);
            }
            statement.executeUpdate();
        }
    }

    /** Compare-and-set under a row lock: SQL edits win over stale saves, including JSON with different whitespace. */
    static JsonObject save(Connection connection, String table, UUID uuid, String classId,
                           JsonObject expected, JsonObject current) throws SQLException {
        if (current.equals(expected)) return expected;
        boolean ownTransaction = connection.getAutoCommit();
        if (ownTransaction) connection.setAutoCommit(false);
        try {
            JsonObject stored = null;
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM `" + table
                    + "` WHERE uuid=? AND class_id=? FOR UPDATE")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, classId);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) stored = read(result);
                }
            }
            JsonObject next = expected.deepCopy();
            Map<String, JsonElement> updates = new LinkedHashMap<>();
            if (stored != null) for (var field : FIELDS.entrySet()) {
                String key = field.getValue();
                if (!java.util.Objects.equals(current.get(key), expected.get(key))
                        && java.util.Objects.equals(stored.get(key), expected.get(key))) {
                    updates.put(field.getKey(), current.get(key));
                    next.add(key, current.get(key));
                }
            }
            if (!updates.isEmpty()) {
                String assignments = updates.keySet().stream().map(column -> "`" + column + "`=?")
                        .collect(Collectors.joining(","));
                try (PreparedStatement statement = connection.prepareStatement("UPDATE `" + table + "` SET "
                        + assignments + " WHERE uuid=? AND class_id=?")) {
                    int i = 1;
                    for (JsonElement value : updates.values()) bind(statement, i++, value);
                    statement.setString(i++, uuid.toString());
                    statement.setString(i, classId);
                    statement.executeUpdate();
                }
            }
            if (ownTransaction) connection.commit();
            return next;
        } catch (SQLException | RuntimeException exception) {
            if (ownTransaction) connection.rollback();
            throw exception;
        } finally {
            if (ownTransaction) connection.setAutoCommit(true);
        }
    }

    /** Only changed DB fields replace live values; unrelated in-game progress is preserved. */
    static JsonObject merge(JsonObject live, JsonObject previous, JsonObject database) {
        JsonObject merged = live.deepCopy();
        for (String key : FIELDS.values())
            if (!java.util.Objects.equals(previous.get(key), database.get(key))) merged.add(key, database.get(key));
        return merged;
    }
}
