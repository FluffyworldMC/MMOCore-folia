package net.Indyuce.mmocore.manager.data.sql;

import io.lumine.mythic.lib.gson.Gson;
import io.lumine.mythic.lib.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ClassProgressTest {
    private Connection connection;
    private final UUID uuid = UUID.randomUUID();
    private final Gson gson = new Gson();

    @BeforeEach
    public void setup() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:" + uuid + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        String columns = ClassProgress.FIELDS.keySet().stream()
                .map(column -> "`" + column + "` " + ClassProgress.type(column)).collect(Collectors.joining(","));
        connection.createStatement().execute("CREATE TABLE classes (uuid VARCHAR(36), class_id VARCHAR(100),player_name VARCHAR(64),"
                + columns + ", PRIMARY KEY(uuid,class_id))");
    }

    @AfterEach
    public void close() throws Exception { connection.close(); }

    private JsonObject progress(int level, double exp, int points) {
        JsonObject data = gson.fromJson("{\"level\":1,\"experience\":0,\"skill-points\":2,\"attribute-points\":0,"
                + "\"attribute-realloc-points\":0,\"skill-reallocation-points\":0,\"skill-tree-reallocation-points\":0,"
                + "\"health\":20,\"mana\":30,\"stamina\":40,\"stellium\":50,\"attribute\":{\"strength\":4},"
                + "\"skill\":{\"SLASH\":3},\"bound-skills\":{\"1\":\"SLASH\"},\"skill-tree-points\":{\"global\":6},"
                + "\"node-levels\":{\"warrior.root\":2},\"node-times-claimed\":{\"node_warrior.root\":2},"
                + "\"unlocked-items\":[\"skill:slash\"]}", JsonObject.class);
        data.addProperty("level", level);
        data.addProperty("experience", exp);
        data.addProperty("attribute-points", points);
        return data;
    }

    private JsonObject read(String classId) throws Exception {
        try (var statement = connection.prepareStatement("SELECT * FROM classes WHERE uuid=? AND class_id=?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, classId);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return ClassProgress.read(result);
            }
        }
    }

    @Test
    public void warriorAndMageKeepSeparateProgressAndAllFieldsRoundTrip() throws Exception {
        JsonObject warrior = progress(35, 1234.5, 12), mage = progress(8, 42.25, 3);
        ClassProgress.insert(connection, "classes", uuid, "WARRIOR", warrior);
        ClassProgress.insert(connection, "classes", uuid, "MAGE", mage);
        assertEquals(warrior, read("WARRIOR"));
        assertEquals(mage, read("MAGE"));
        JsonObject leveledMage = progress(9, 14.5, 4);
        ClassProgress.save(connection, "classes", uuid, "MAGE", mage, leveledMage);
        assertEquals(warrior, read("WARRIOR"));
        assertEquals(leveledMage, read("MAGE"));
    }

    @Test
    public void migrationDoesNotOverwriteExistingClassEditsAndHandlesOlderMissingFields() throws Exception {
        JsonObject existing = progress(50, 999, 30);
        ClassProgress.insert(connection, "classes", uuid, "WARRIOR", existing);
        ClassProgress.insert(connection, "classes", uuid, "WARRIOR", progress(1, 0, 0));
        assertEquals(existing, read("WARRIOR"));
        JsonObject legacy = progress(5, 10, 3);
        legacy.remove("node-times-claimed");
        legacy.remove("unlocked-items");
        ClassProgress.insert(connection, "classes", uuid, "MAGE", legacy);
        assertTrue(read("MAGE").getAsJsonObject("node-times-claimed").entrySet().isEmpty());
        assertEquals(0, read("MAGE").getAsJsonArray("unlocked-items").size());
    }

    @Test
    public void externalExpEditWinsOverAutosaveWithoutLosingOtherGameProgress() throws Exception {
        JsonObject baseline = progress(10, 100, 5);
        ClassProgress.insert(connection, "classes", uuid, "WARRIOR", baseline);
        connection.createStatement().executeUpdate("UPDATE classes SET experience=900 WHERE class_id='WARRIOR'");
        JsonObject live = progress(10, 120, 6);
        JsonObject next = ClassProgress.save(connection, "classes", uuid, "WARRIOR", baseline, live);
        assertEquals(900, read("WARRIOR").get("experience").getAsDouble(), 0);
        assertEquals(6, read("WARRIOR").get("attribute-points").getAsInt());
        // Retaining the old baseline for rejected writes lets the next poll see the external edit.
        assertEquals(100, next.get("experience").getAsDouble(), 0);
        JsonObject merged = ClassProgress.merge(live, next, read("WARRIOR"));
        assertEquals(900, merged.get("experience").getAsDouble(), 0);
        assertEquals(6, merged.get("attribute-points").getAsInt());
    }

    @Test
    public void externalInactiveClassEditSurvivesStaleSave() throws Exception {
        JsonObject warrior = progress(30, 123, 15), mage = progress(10, 22, 4);
        ClassProgress.insert(connection, "classes", uuid, "WARRIOR", warrior);
        ClassProgress.insert(connection, "classes", uuid, "MAGE", mage);
        connection.createStatement().executeUpdate("UPDATE classes SET attribute_points=99 WHERE class_id='WARRIOR'");
        ClassProgress.save(connection, "classes", uuid, "WARRIOR", warrior, warrior);
        assertEquals(99, read("WARRIOR").get("attribute-points").getAsInt());
        assertEquals(mage, read("MAGE"));
    }

    @Test
    public void unchangedDatabaseDoesNotRewindUnsavedGameExp() {
        JsonObject previous = progress(10, 100, 5), live = progress(11, 50, 8);
        assertEquals(live, ClassProgress.merge(live, previous, previous));
        JsonObject edited = previous.deepCopy();
        edited.addProperty("skill-points", 77);
        JsonObject merged = ClassProgress.merge(live, previous, edited);
        assertEquals(11, merged.get("level").getAsInt());
        assertEquals(50, merged.get("experience").getAsDouble(), 0);
        assertEquals(77, merged.get("skill-points").getAsInt());
    }

    @Test
    public void emptyJsonCanBeSavedAndNullColumnsBecomeEmptyContainers() throws Exception {
        JsonObject baseline = progress(10, 100, 5);
        ClassProgress.insert(connection, "classes", uuid, "WARRIOR", baseline);
        JsonObject reset = baseline.deepCopy();
        reset.add("attribute", new JsonObject());
        ClassProgress.save(connection, "classes", uuid, "WARRIOR", baseline, reset);
        assertTrue(read("WARRIOR").getAsJsonObject("attribute").entrySet().isEmpty());
        connection.createStatement().executeUpdate("UPDATE classes SET skills=NULL, unlocked_items=NULL");
        assertTrue(read("WARRIOR").getAsJsonObject("skill").entrySet().isEmpty());
        assertEquals(0, read("WARRIOR").getAsJsonArray("unlocked-items").size());
        JsonObject cleared = read("WARRIOR"), changed = cleared.deepCopy();
        changed.add("skill", gson.fromJson("{\"SLASH\":5}", JsonObject.class));
        ClassProgress.save(connection, "classes", uuid, "WARRIOR", cleared, changed);
        assertEquals(5, read("WARRIOR").getAsJsonObject("skill").get("SLASH").getAsInt());
    }

    @Test
    public void formattedSqlJsonCanBeSavedAfterPolling() throws Exception {
        JsonObject baseline = progress(10, 100, 5);
        ClassProgress.insert(connection, "classes", uuid, "WARRIOR", baseline);
        connection.createStatement().executeUpdate("UPDATE classes SET attributes='{ \"strength\" : 9 }'");
        JsonObject polled = read("WARRIOR"), changed = polled.deepCopy();
        changed.getAsJsonObject("attribute").addProperty("strength", 10);
        ClassProgress.save(connection, "classes", uuid, "WARRIOR", polled, changed);
        assertEquals(10, read("WARRIOR").getAsJsonObject("attribute").get("strength").getAsInt());
    }

    @Test
    public void leaderboardIncludesInactiveClassesWithPlayerNamesAndLimitsResults() throws Exception {
        UUID other = UUID.randomUUID();
        ClassProgress.insert(connection, "classes", uuid, "WARRIOR", progress(35, 100, 5));
        ClassProgress.insert(connection, "classes", uuid, "MAGE", progress(2, 10, 1));
        ClassProgress.insert(connection, "classes", other, "WARRIOR", progress(35, 500, 5));
        try (var statement = connection.prepareStatement("UPDATE classes SET player_name=? WHERE uuid=?")) {
            statement.setString(1, "Non");
            statement.setString(2, uuid.toString());
            statement.executeUpdate();
            statement.setString(1, "Other");
            statement.setString(2, other.toString());
            statement.executeUpdate();
        }
        var top = ClassProgress.leaderboard(connection, "classes", "WARRIOR", 1);
        assertEquals(1, top.size());
        assertEquals("Other", top.get(0).name);
        assertEquals(35, top.get(0).level);
        assertEquals("Non", ClassProgress.leaderboard(connection, "classes", "MAGE", 10).get(0).name);
        assertTrue(ClassProgress.leaderboard(connection, "classes", "ASSASSIN", 10).isEmpty());
        assertTrue(ClassProgress.leaderboard(connection, "classes", "WARRIOR' OR 1=1 --", 10).isEmpty());
    }
}
