package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntersectionCleanerTest {

    private static List<Intersection> cleaned;

    // Runs the cleaner on the real messy file once, before the tests.
    @BeforeAll
    static void cleanTheRealFile() throws IOException {
        cleaned = IntersectionCleaner.cleanFromResource("intersections-legacy.csv");
    }

    // Small helper to pull one intersection out of the result by its id.
    private static Intersection find(String id) {
        return cleaned.stream()
                .filter(i -> id.equals(i.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no intersection with id " + id));
    }

    @Test
    void theFileHas18RowsButOneIsADuplicate() {
        // 18 rows in the file, INT-1005 is written twice, so 17 come out.
        assertEquals(17, cleaned.size());
    }

    @Test
    void trimsThePaddingOnTheDistrict() {
        assertEquals("Downtown", find("INT-1001").district());
    }

    @Test
    void makesTheLowerCaseIdUpperCase() {
        assertEquals("INT-1002", find("INT-1002").id());
    }

    @Test
    void makesTheLowerCaseDistrictProper() {
        assertEquals("Downtown", find("INT-1003").district());
    }

    @Test
    void joinsTheTwoRowsOfInt1005() {
        List<Intersection> bothRows = cleaned.stream()
                .filter(i -> "INT-1005".equals(i.id()))
                .toList();

        assertEquals(1, bothRows.size());
    }

    @Test
    void keepsTheSignalTypeOfTheJoinedRow() {
        assertEquals("roundabout", find("INT-1005").signalType());
        assertEquals(true, find("INT-1005").active());
    }

    @Test
    void aBlankSignalTypeBecomesNull() {
        assertNull(find("INT-1007").signalType());
        assertEquals("Eastside", find("INT-1007").district());
    }

    @Test
    void aBlankDistrictBecomesNull() {
        assertNull(find("INT-1015").district());
        assertEquals("4-way", find("INT-1015").signalType());
    }

    @Test
    void theWordUnknownSignalTypeBecomesNull() {
        assertNull(find("INT-1004").signalType());
    }

    @Test
    void theWordUnknownActiveFlagBecomesNull() {
        assertNull(find("INT-1013").active());
    }

    @Test
    void theYesFlagBecomesTrue() {
        assertEquals(true, find("INT-1001").active());   // Y
        assertEquals(true, find("INT-1002").active());   // yes
        assertEquals(true, find("INT-1007").active());   // 1
        assertEquals(true, find("INT-1016").active());   // YES
    }

    @Test
    void theNoFlagBecomesFalse() {
        assertEquals(false, find("INT-1003").active());  // 0
        assertEquals(false, find("INT-1006").active());  // no
        assertEquals(false, find("INT-1009").active());  // FALSE
        assertEquals(false, find("INT-1017").active());  // n
    }

    @Test
    void everySignalTypeIsLowerCase() {
        assertEquals("4-way", find("INT-1006").signalType());
        assertEquals("pedestrian", find("INT-1014").signalType());
        assertEquals("stop-sign", find("INT-1010").signalType());
    }

    @Test
    void everyDistrictIsProperCased() {
        assertEquals("Midtown", find("INT-1011").district());
        assertEquals("Uptown", find("INT-1008").district());
        assertEquals("Eastside", find("INT-1017").district());
        assertEquals("Westside", find("INT-1013").district());
    }

    @Test
    void everyIdIsUpperCase() {
        for (Intersection i : cleaned) {
            if (i.id() != null) {
                assertEquals(i.id().toUpperCase(), i.id());
            }
        }
    }

    @Test
    void aRowWithMissingColumnsIsLeftOut() {
        List<String[]> broken = List.of(
                new String[]{"INT-2001", "Downtown"},
                new String[]{"INT-2002", "Midtown", "4-way", "Y"});

        List<Intersection> result = IntersectionCleaner.clean(broken);

        assertEquals(1, result.size());
        assertEquals("INT-2002", result.get(0).id());
    }

    @Test
    void theCleanedListHasNoRepeatedIds() {
        long ids = cleaned.stream().map(Intersection::id).distinct().count();

        assertEquals(cleaned.size(), ids);
    }

    @Test
    void theCleanedListIsNotEmpty() {
        assertTrue(!cleaned.isEmpty());
    }
}
