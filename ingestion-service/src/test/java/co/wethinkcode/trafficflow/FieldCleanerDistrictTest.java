package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FieldCleanerDistrictTest {

    @Test
    void makesFirstLetterUpperCase() {
        assertEquals("Downtown", FieldCleaner.fixDistrictCasing("downtown"));
        assertEquals("Midtown", FieldCleaner.fixDistrictCasing("midtown"));
        assertEquals("Uptown", FieldCleaner.fixDistrictCasing("uptown"));
        assertEquals("Eastside", FieldCleaner.fixDistrictCasing("eastside"));
        assertEquals("Westside", FieldCleaner.fixDistrictCasing("westside"));
    }

    @Test
    void fixesLoudCasing() {
        assertEquals("Uptown", FieldCleaner.fixDistrictCasing("UPTOWN"));
    }

    @Test
    void keepsADistrictThatIsAlreadyRight() {
        assertEquals("Downtown", FieldCleaner.fixDistrictCasing("Downtown"));
    }

    @Test
    void removesPaddingFirst() {
        assertEquals("Downtown", FieldCleaner.fixDistrictCasing("  downtown "));
    }

    @Test
    void missingDistrictBecomesNull() {
        assertNull(FieldCleaner.fixDistrictCasing(""));
        assertNull(FieldCleaner.fixDistrictCasing("N/A"));
        assertNull(FieldCleaner.fixDistrictCasing("unknown"));
        assertNull(FieldCleaner.fixDistrictCasing(null));
    }
}
