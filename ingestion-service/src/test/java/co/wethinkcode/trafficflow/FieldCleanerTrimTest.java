package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FieldCleanerTrimTest {

    @Test
    void removesSpaceAroundTheValue() {
        assertEquals("Downtown", FieldCleaner.trimSpaces("  Downtown  "));
        assertEquals("INT-1003", FieldCleaner.trimSpaces("INT-1003 "));
    }

    @Test
    void turnsDoubleSpaceIntoOneSpace() {
        assertEquals("West side", FieldCleaner.trimSpaces("West  side"));
    }

    @Test
    void removesTabsAndNewLines() {
        assertEquals("Uptown", FieldCleaner.trimSpaces("\tUptown\n"));
    }

    @Test
    void keepsNullAndEmptyAsTheyAre() {
        assertNull(FieldCleaner.trimSpaces(null));
        assertEquals("", FieldCleaner.trimSpaces("   "));
    }

    @Test
    void keepsAValueWithNoSpacesUnchanged() {
        assertEquals("4-way", FieldCleaner.trimSpaces("4-way"));
    }
}
