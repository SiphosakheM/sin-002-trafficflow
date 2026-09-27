package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FieldCleanerSignalTypeTest {

    @Test
    void makesSignalTypeLowerCase() {
        assertEquals("roundabout", FieldCleaner.fixSignalTypeCasing("ROUNDABOUT"));
        assertEquals("roundabout", FieldCleaner.fixSignalTypeCasing("Roundabout"));
        assertEquals("pedestrian", FieldCleaner.fixSignalTypeCasing("Pedestrian"));
    }

    @Test
    void keepsTheDashButLowerCasesTheWord() {
        assertEquals("4-way", FieldCleaner.fixSignalTypeCasing("4-Way"));
        assertEquals("stop-sign", FieldCleaner.fixSignalTypeCasing("stop-sign"));
    }

    @Test
    void keepsASignalTypeThatIsAlreadyLowerCase() {
        assertEquals("4-way", FieldCleaner.fixSignalTypeCasing("4-way"));
    }

    @Test
    void removesPaddingFirst() {
        assertEquals("pedestrian", FieldCleaner.fixSignalTypeCasing("  pedestrian "));
    }

    @Test
    void theWordUnknownCountsAsMissing() {
        assertNull(FieldCleaner.fixSignalTypeCasing("unknown"));
    }

    @Test
    void missingSignalTypeBecomesNull() {
        assertNull(FieldCleaner.fixSignalTypeCasing(""));
        assertNull(FieldCleaner.fixSignalTypeCasing("N/A"));
        assertNull(FieldCleaner.fixSignalTypeCasing(null));
    }
}
