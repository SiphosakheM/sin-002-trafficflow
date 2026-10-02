package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the json we send to the other services.
 * intersection-service will read this json, so the field names are a promise.
 */
class IntersectionJsonTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void writesTheFourFieldNames() throws IOException {
        Intersection record = new Intersection("INT-1001", "Downtown", "4-way", true);

        String text = json.writeValueAsString(record);

        assertTrue(text.contains("\"id\":\"INT-1001\""));
        assertTrue(text.contains("\"district\":\"Downtown\""));
        assertTrue(text.contains("\"signalType\":\"4-way\""));
        assertTrue(text.contains("\"active\":true"));
    }

    @Test
    void keepsAMissingValueAsJsonNull() throws IOException {
        Intersection record = new Intersection("INT-1015", null, "4-way", true);

        String text = json.writeValueAsString(record);

        assertTrue(text.contains("\"district\":null"));
        assertTrue(text.contains("\"signalType\":\"4-way\""));
    }

    @Test
    void keepsAMissingSignalTypeAsJsonNull() throws IOException {
        Intersection record = new Intersection("INT-1007", "Eastside", null, true);

        String text = json.writeValueAsString(record);

        assertTrue(text.contains("\"signalType\":null"));
    }

    @Test
    void keepsAMissingActiveFlagAsJsonNull() throws IOException {
        Intersection record = new Intersection("INT-1013", "Westside", null, null);

        String text = json.writeValueAsString(record);

        assertTrue(text.contains("\"active\":null"));
    }

    @Test
    void theWholeStoreBecomesAJsonArray() throws IOException {
        IntersectionStore store = IntersectionStore.loadFromResource("intersections-legacy.csv");

        String text = json.writeValueAsString(store.all());

        assertTrue(text.startsWith("["));
        assertTrue(text.endsWith("]"));
    }

    @Test
    void theJsonArrayHasOneEntryForEachRecord() throws IOException {
        IntersectionStore store = IntersectionStore.loadFromResource("intersections-legacy.csv");

        String text = json.writeValueAsString(store.all());

        // Every record starts with {"id":" and none of them has an id that is null.
        assertEquals(17, text.split("\\{\"id\":", -1).length - 1);
    }

    @Test
    void theJsonHasNoUpperCaseIdLeft() throws IOException {
        IntersectionStore store = IntersectionStore.loadFromResource("intersections-legacy.csv");

        String text = json.writeValueAsString(store.all());

        assertTrue(!text.contains("\"id\":\"int-"));
        assertTrue(!text.contains("\"district\":\"downtown\""));
    }

    @Test
    void anEmptyStoreBecomesAnEmptyJsonArray() throws IOException {
        String text = json.writeValueAsString(List.of());

        assertEquals("[]", text);
    }
}