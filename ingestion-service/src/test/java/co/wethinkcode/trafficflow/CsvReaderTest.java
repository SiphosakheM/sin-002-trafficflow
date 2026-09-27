package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvReaderTest {

    // Turns a piece of csv text into a stream we can read.
    private InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsTheRowsOfASmallFile() throws Exception {
        String csv = """
                intersection_id,District ,signal_type,active_flag
                INT-1001, Downtown ,4-way,Y
                INT-1002,Midtown,pedestrian,yes
                """;

        List<String[]> rows = CsvReader.read(stream(csv));

        assertEquals(2, rows.size());
        assertEquals("INT-1001", rows.get(0)[0]);
        assertEquals("INT-1002", rows.get(1)[0]);
    }

    @Test
    void doesNotReadTheHeaderLine() throws Exception {
        String csv = """
                intersection_id,District ,signal_type,active_flag
                INT-1001,Downtown,4-way,Y
                """;

        List<String[]> rows = CsvReader.read(stream(csv));

        assertEquals(1, rows.size());
        assertEquals("INT-1001", rows.get(0)[0]);
    }

    @Test
    void readsTheRealLegacyFile() throws Exception {
        List<String[]> rows = CsvReader.readFromResource("intersections-legacy.csv");

        // The file has 19 lines: 1 header line and 18 data rows.
        assertEquals(18, rows.size());
    }

    @Test
    void theRealFileStartsWithInt1001NotTheHeader() throws Exception {
        List<String[]> rows = CsvReader.readFromResource("intersections-legacy.csv");

        assertEquals("INT-1001", rows.get(0)[0]);
    }

    @Test
    void everyRowHasFourColumns() throws Exception {
        List<String[]> rows = CsvReader.readFromResource("intersections-legacy.csv");

        for (String[] row : rows) {
            assertEquals(4, row.length, "a row should have 4 columns: " + String.join("|", row));
        }
    }

    @Test
    void aFileWithOnlyAHeaderGivesNoRows() throws Exception {
        String csv = "intersection_id,District ,signal_type,active_flag\n";

        List<String[]> rows = CsvReader.read(stream(csv));

        assertTrue(rows.isEmpty());
    }

    @Test
    void theLegacyFileIsInsideTheProject() throws Exception {
        assertNotNull(CsvReader.readFromResource("intersections-legacy.csv"));
    }
}
