package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DedupeIntersectionsTest {

    @Test
    void joinsTwoRowsWithTheSameId() {
        List<Intersection> rows = List.of(
                new Intersection("INT-1005", "Downtown", "roundabout", true),
                new Intersection("INT-1005", "Downtown", "roundabout", true));

        List<Intersection> result = Dedupe.removeDuplicates(rows);

        assertEquals(1, result.size());
    }

    @Test
    void keepsTheFirstRowOfADuplicate() {
        List<Intersection> rows = List.of(
                new Intersection("INT-1005", "Downtown", "roundabout", true),
                new Intersection("INT-1005", "Downtown", null, false));

        List<Intersection> result = Dedupe.removeDuplicates(rows);

        assertEquals("roundabout", result.get(0).signalType());
        assertEquals(true, result.get(0).active());
    }

    @Test
    void leavesRowsWithDifferentIdsAlone() {
        List<Intersection> rows = List.of(
                new Intersection("INT-1001", "Downtown", "4-way", true),
                new Intersection("INT-1002", "Midtown", "pedestrian", true));

        List<Intersection> result = Dedupe.removeDuplicates(rows);

        assertEquals(2, result.size());
    }

    @Test
    void oneDuplicateInTheMiddleOnlyRemovesOneRow() {
        List<Intersection> rows = List.of(
                new Intersection("INT-1001", "Downtown", "4-way", true),
                new Intersection("INT-1005", "Downtown", "roundabout", true),
                new Intersection("INT-1005", "Downtown", "roundabout", true),
                new Intersection("INT-1006", "Midtown", "4-way", false));

        List<Intersection> result = Dedupe.removeDuplicates(rows);

        assertEquals(3, result.size());
        assertEquals("INT-1001", result.get(0).id());
        assertEquals("INT-1005", result.get(1).id());
        assertEquals("INT-1006", result.get(2).id());
    }

    @Test
    void keepsTheOrderOfTheRows() {
        List<Intersection> rows = List.of(
                new Intersection("INT-1016", "Downtown", "4-way", true),
                new Intersection("INT-1001", "Downtown", "4-way", true));

        List<Intersection> result = Dedupe.removeDuplicates(rows);

        assertEquals("INT-1016", result.get(0).id());
        assertEquals("INT-1001", result.get(1).id());
    }

    @Test
    void rowsWithNoIdAreNotJoined() {
        // Without an id we cannot say the two rows are the same place.
        List<Intersection> rows = List.of(
                new Intersection(null, "Downtown", "4-way", true),
                new Intersection(null, "Midtown", "4-way", true));

        List<Intersection> result = Dedupe.removeDuplicates(rows);

        assertEquals(2, result.size());
    }

    @Test
    void anEmptyListStaysEmpty() {
        assertTrue(Dedupe.removeDuplicates(List.of()).isEmpty());
    }
}
