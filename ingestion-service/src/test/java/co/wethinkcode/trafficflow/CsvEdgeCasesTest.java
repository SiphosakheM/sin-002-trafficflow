package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The odd cases a real legacy export can throw at us.
 * None of these are in the file we were given, but a future export could
 * have them, so we check the reader can cope.
 */
class CsvEdgeCasesTest {

    private static final String HEADER = "intersection_id,District ,signal_type,active_flag\n";

    private InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void anEmptyFileGivesNoRows() throws IOException {
        List<String[]> rows = CsvReader.read(stream(""));

        assertTrue(rows.isEmpty());
    }

    @Test
    void aFileWithOnlySpacesGivesNoRows() throws IOException {
        List<String[]> rows = CsvReader.read(stream("\n\n   \n"));

        assertTrue(rows.isEmpty());
    }

    @Test
    void blankLinesBetweenRowsAreLeftOut() throws IOException {
        String csv = HEADER + "INT-1001,Downtown,4-way,Y\n\nINT-1002,Midtown,4-way,N\n";

        List<String[]> rows = CsvReader.read(stream(csv));

        assertEquals(2, rows.size());
    }

    @Test
    void aCommaInsideQuotesStaysInOneCell() throws IOException {
        String csv = HEADER + "\"INT-1001\",\"Downtown, Central\",4-way,Y\n";

        List<String[]> rows = CsvReader.read(stream(csv));

        assertEquals("Downtown, Central", rows.get(0)[1]);
    }

    @Test
    void aDistrictWithACommaGetsCleanedAndKeptWhole() throws IOException {
        String csv = HEADER + "\"INT-1001\",\"Downtown, Central\",4-way,Y\n";

        List<Intersection> result = IntersectionCleaner.clean(CsvReader.read(stream(csv)));

        assertEquals("Downtown, central", result.get(0).district());
    }

    @Test
    void aRowWithExtraColumnsIsStillCleaned() throws IOException {
        String csv = HEADER + "INT-1001,Downtown,4-way,Y,extra,more\n";

        List<Intersection> result = IntersectionCleaner.clean(CsvReader.read(stream(csv)));

        assertEquals(1, result.size());
        assertEquals("INT-1001", result.get(0).id());
    }

    @Test
    void aRowWithMissingColumnsIsSkippedAndTheRestStillWork() throws IOException {
        String csv = HEADER + "INT-1001,Downtown\nINT-1002,Midtown,4-way,N\n";

        List<Intersection> result = IntersectionCleaner.clean(CsvReader.read(stream(csv)));

        assertEquals(1, result.size());
        assertEquals("INT-1002", result.get(0).id());
    }

    @Test
    void windowsLineEndingsDoNotBreakTheLastColumn() throws IOException {
        String csv = "intersection_id,District ,signal_type,active_flag\r\nINT-1001,Downtown,4-way,Y\r\n";

        List<Intersection> result = IntersectionCleaner.clean(CsvReader.read(stream(csv)));

        assertEquals(1, result.size());
        assertEquals(true, result.get(0).active());
    }

    @Test
    void noHeaderLineMeansTheFirstRowIsStillRead() throws IOException {
        // A file with no header at all would lose its first row, so we say so.
        String csv = "INT-1001,Downtown,4-way,Y\nINT-1002,Midtown,4-way,N\n";

        List<String[]> rows = CsvReader.read(stream(csv));

        assertEquals(1, rows.size());
        assertEquals("INT-1002", rows.get(0)[0]);
    }

    @Test
    void aDistrictWithExtraInnerSpacesIsFixed() {
        String district = FieldCleaner.fixDistrictCasing("  Downtown   Central ");

        assertEquals("Downtown central", district);
    }

    @Test
    void theIdLookupWorksEvenWhenTheStoreHasOneRecord() {
        IntersectionStore store = new IntersectionStore(List.of(
                new Intersection("INT-1001", "Downtown", "4-way", true)));

        assertTrue(store.findById("INT-1001").isPresent());
        assertEquals(1, store.size());
    }
}