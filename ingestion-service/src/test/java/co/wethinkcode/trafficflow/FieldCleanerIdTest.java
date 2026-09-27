package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FieldCleanerIdTest {

    @Test
    void makesTheIdUpperCase() {
        assertEquals("INT-1002", FieldCleaner.fixIdCasing("int-1002"));
        assertEquals("INT-1012", FieldCleaner.fixIdCasing("int-1012"));
    }

    @Test
    void keepsAnIdThatIsAlreadyUpperCase() {
        assertEquals("INT-1001", FieldCleaner.fixIdCasing("INT-1001"));
    }

    @Test
    void removesPaddingBeforeChangingCasing() {
        assertEquals("INT-1005", FieldCleaner.fixIdCasing("  int-1005 "));
    }

    @Test
    void missingIdBecomesNull() {
        assertNull(FieldCleaner.fixIdCasing("N/A"));
        assertNull(FieldCleaner.fixIdCasing(""));
        assertNull(FieldCleaner.fixIdCasing(null));
    }
}
