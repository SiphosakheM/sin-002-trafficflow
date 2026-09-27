package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FieldCleanerActiveFlagTest {

    @Test
    void yesAnswers() {
        assertEquals(true, FieldCleaner.parseActiveFlag("Y"));
        assertEquals(true, FieldCleaner.parseActiveFlag("y"));
        assertEquals(true, FieldCleaner.parseActiveFlag("yes"));
        assertEquals(true, FieldCleaner.parseActiveFlag("YES"));
        assertEquals(true, FieldCleaner.parseActiveFlag("1"));
        assertEquals(true, FieldCleaner.parseActiveFlag("true"));
        assertEquals(true, FieldCleaner.parseActiveFlag("TRUE"));
    }

    @Test
    void noAnswers() {
        assertEquals(false, FieldCleaner.parseActiveFlag("N"));
        assertEquals(false, FieldCleaner.parseActiveFlag("n"));
        assertEquals(false, FieldCleaner.parseActiveFlag("no"));
        assertEquals(false, FieldCleaner.parseActiveFlag("0"));
        assertEquals(false, FieldCleaner.parseActiveFlag("FALSE"));
    }

    @Test
    void ignoresCasingAndPadding() {
        assertEquals(true, FieldCleaner.parseActiveFlag("  YeS  "));
    }

    @Test
    void aFlagWeDoNotKnowBecomesNull() {
        assertNull(FieldCleaner.parseActiveFlag("unknown"));
        assertNull(FieldCleaner.parseActiveFlag("maybe"));
        assertNull(FieldCleaner.parseActiveFlag(""));
        assertNull(FieldCleaner.parseActiveFlag(null));
    }
}
