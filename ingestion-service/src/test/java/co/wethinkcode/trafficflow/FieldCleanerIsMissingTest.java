package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldCleanerIsMissingTest {

    @Test
    void blankIsMissing() {
        assertTrue(FieldCleaner.isMissing(""));
        assertTrue(FieldCleaner.isMissing("   "));
    }

    @Test
    void nullIsMissing() {
        assertTrue(FieldCleaner.isMissing(null));
    }

    @Test
    void placeholderWordsAreMissing() {
        assertTrue(FieldCleaner.isMissing("N/A"));
        assertTrue(FieldCleaner.isMissing("n/a"));
        assertTrue(FieldCleaner.isMissing("TBD"));
        assertTrue(FieldCleaner.isMissing("unknown"));
        assertTrue(FieldCleaner.isMissing("-"));
        assertTrue(FieldCleaner.isMissing("NaN"));
    }

    @Test
    void placeholderWordsWorkWithAnyCasingAndPadding() {
        assertTrue(FieldCleaner.isMissing(" tbd "));
        assertTrue(FieldCleaner.isMissing("UNKNOWN"));
        assertTrue(FieldCleaner.isMissing(" Unknown "));
    }

    @Test
    void realValuesAreNotMissing() {
        assertFalse(FieldCleaner.isMissing("Downtown"));
        assertFalse(FieldCleaner.isMissing("4-way"));
        assertFalse(FieldCleaner.isMissing("INT-1001"));
    }
}
