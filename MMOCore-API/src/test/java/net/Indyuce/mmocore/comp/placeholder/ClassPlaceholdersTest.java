package net.Indyuce.mmocore.comp.placeholder;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ClassPlaceholdersTest {
    @Test
    void parsesMetricsAndClassIdsWithUnderscores() {
        var request = ClassPlaceholderRequest.parse("class_attribute_points_dark_mage");
        assertNotNull(request);
        assertEquals("DARK_MAGE", request.classId);
        assertEquals("attribute_points", request.metric);
        request = ClassPlaceholderRequest.parse("class_attribute_reallocation_points_WARRIOR");
        assertEquals("attribute_reallocation_points", request.metric);
        request = ClassPlaceholderRequest.parse("class_skill_DARK_MAGE:FIRE_BALL");
        assertEquals("DARK_MAGE", request.classId);
        assertEquals("FIRE_BALL", request.key);
    }

    @Test
    void parsesTopNamesWithoutAPlayerAndRejectsInvalidRanks() {
        var request = ClassPlaceholderRequest.parse("class_top_DARK_MAGE_12_name");
        assertEquals("DARK_MAGE", request.classId);
        assertEquals(12, request.rank);
        assertEquals("name", request.metric);
        assertNull(ClassPlaceholderRequest.parse("class_top_MAGE_0_name"));
        assertNull(ClassPlaceholderRequest.parse("class_top_MAGE_-1_name"));
        assertNull(ClassPlaceholderRequest.parse("class_top_MAGE_999999999999_name"));
        assertNull(ClassPlaceholderRequest.parse("class_top_MAGE_first_level"));
        assertNull(ClassPlaceholderRequest.parse("class_top_MAGE_1_password"));
    }

    @Test
    void leavesExistingPlaceholdersUntouchedAndRejectsIncompleteRequests() {
        for (String raw : new String[]{"level", "class_id", "class_points", "class_exp_", "class_top_",
                "class_attribute_WARRIOR", "class_skill_MAGE:"})
            assertNull(ClassPlaceholderRequest.parse(raw), raw);
    }

    @Test
    void liveLevelsReplaceSavedLevelsWithoutDuplicateEntries() {
        UUID warrior = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID mage = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var result = ClassRanking.merge(Arrays.asList(new ClassRanking.Entry(warrior, "Non", 10, 90),
                new ClassRanking.Entry(mage, "Mage", 20, 1)),
                Collections.singletonList(new ClassRanking.Entry(warrior, "Non", 25, 0)), 10);
        assertEquals(2, result.size());
        assertEquals("Non", result.get(0).name);
        assertEquals(25, result.get(0).level);
        // A reset must also replace the old higher saved level.
        result = ClassRanking.merge(result, Collections.singletonList(new ClassRanking.Entry(warrior, "Non", 1, 0)), 10);
        assertEquals("Mage", result.get(0).name);
    }

    @Test
    void sortsLevelThenExperienceThenUuidWithBoundedImmutableResults() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");
        var result = ClassRanking.merge(Arrays.asList(new ClassRanking.Entry(second, "Second", 10, 50),
                new ClassRanking.Entry(first, "First", 10, 50), new ClassRanking.Entry(third, "Third", 10, 99)),
                Collections.emptyList(), 2);
        assertEquals(third, result.get(0).uuid);
        assertEquals(first, result.get(1).uuid);
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
    }

    @Test
    void missingPlayerOrRankDoesNotAccessTheDatabase() {
        var service = new ClassPlaceholders();
        assertEquals("0", service.request(null, "class_level_WARRIOR"));
        assertEquals("-", service.request(null, "class_top_WARRIOR_1_name"));
        assertEquals("0", service.request(null, "class_top_WARRIOR_1_level"));
        assertNull(service.request(null, "class_id"));
    }
}
