package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntersectionStoreTest {

    @Test
    void loadsTheCleanRecordsFromTheFile() throws IOException {
        IntersectionStore store = IntersectionStore.loadFromResource("intersections-legacy.csv");

        assertEquals(17, store.all().size());
    }

    @Test
    void findsOneRecordByItsId() throws IOException {
        IntersectionStore store = IntersectionStore.loadFromResource("intersections-legacy.csv");

        Intersection found = store.findById("INT-1001").orElseThrow();

        assertEquals("Downtown", found.district());
    }

    @Test
    void theLookupIgnoresTheIdCasing() throws IOException {
        IntersectionStore store = IntersectionStore.loadFromResource("intersections-legacy.csv");

        assertTrue(store.findById("int-1001").isPresent());
        assertTrue(store.findById("  INT-1001  ").isPresent());
    }

    @Test
    void anUnknownIdGivesNothing() throws IOException {
        IntersectionStore store = IntersectionStore.loadFromResource("intersections-legacy.csv");

        assertTrue(store.findById("INT-9999").isEmpty());
        assertTrue(store.findById("nonsense").isEmpty());
    }

    @Test
    void aNullIdGivesNothing() throws IOException {
        IntersectionStore store = IntersectionStore.loadFromResource("intersections-legacy.csv");

        assertTrue(store.findById(null).isEmpty());
    }

    @Test
    void tellsWhichDistrictsWeHave() throws IOException {
        IntersectionStore store = IntersectionStore.loadFromResource("intersections-legacy.csv");

        // Downtown, Midtown, Uptown, Eastside, Westside
        assertEquals(5, store.districts().size());
        assertTrue(store.districts().contains("Downtown"));
        assertTrue(store.districts().contains("Westside"));
    }

    @Test
    void theDistrictListHasNoBlanksAndNoDuplicates() throws IOException {
        IntersectionStore store = IntersectionStore.loadFromResource("intersections-legacy.csv");

        List<String> districts = store.districts();

        assertEquals(districts.size(), districts.stream().distinct().count());
        for (String district : districts) {
            assertTrue(!district.isBlank(), "a district should not be blank");
        }
    }

    @Test
    void aRecordWithNoDistrictIsStillInTheList() throws IOException {
        IntersectionStore store = IntersectionStore.loadFromResource("intersections-legacy.csv");

        Intersection record = store.all().stream()
                .filter(i -> "INT-1015".equals(i.id()))
                .findFirst()
                .orElseThrow();

        assertNull(record.district());
        assertEquals("4-way", record.signalType());
    }

    @Test
    void aFileThatIsNotThereGivesAClearError() {
        IOException error = assertThrows(IOException.class,
                () -> IntersectionStore.loadFromResource("no-such-file.csv"));

        assertTrue(error.getMessage().contains("no-such-file.csv"));
    }

    @Test
    void canStartWithARecordListInsteadOfAFile() {
        IntersectionStore store = new IntersectionStore(List.of(
                new Intersection("INT-1", "Downtown", "4-way", true)));

        assertEquals(1, store.all().size());
        assertEquals("Downtown", store.districts().get(0));
    }
}