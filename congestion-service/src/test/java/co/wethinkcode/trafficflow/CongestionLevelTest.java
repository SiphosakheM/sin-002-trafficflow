package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CongestionLevelTest {

    @Test
    void theLowestLevelIsZero() {
        assertEquals(0, CongestionLevel.of(0).value());
    }

    @Test
    void theHighestLevelIsEight() {
        assertEquals(8, CongestionLevel.of(8).value());
    }

    @Test
    void aLevelInTheMiddleIsFine() {
        assertEquals(4, CongestionLevel.of(4).value());
    }

    @Test
    void aLevelAboveEightIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> CongestionLevel.of(9));
    }

    @Test
    void aLevelBelowZeroIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> CongestionLevel.of(-1));
    }

    @Test
    void theErrorSaysWhatTheRangeIs() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> CongestionLevel.of(42));

        assertTrue(error.getMessage().contains("0"));
        assertTrue(error.getMessage().contains("8"));
    }

    @Test
    void aLevelCanBeReadFromText() {
        assertEquals(5, CongestionLevel.parse("5").value());
        assertEquals(5, CongestionLevel.parse(" 5 ").value());
    }

    @Test
    void textThatIsNotANumberIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> CongestionLevel.parse("busy"));
    }

    @Test
    void emptyTextIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> CongestionLevel.parse(""));
        assertThrows(IllegalArgumentException.class, () -> CongestionLevel.parse(null));
    }

    @Test
    void aFractionIsRefusedBecauseALevelIsAWholeNumber() {
        assertThrows(IllegalArgumentException.class, () -> CongestionLevel.parse("3.5"));
    }

    @Test
    void aWordLikeNaNIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> CongestionLevel.parse("NaN"));
    }

    @Test
    void stepsUpAndDownButStaysInsideTheRange() {
        assertEquals(5, CongestionLevel.of(4).up().value());
        assertEquals(3, CongestionLevel.of(4).down().value());
    }

    @Test
    void steppingUpFromEightStaysAtEight() {
        assertEquals(8, CongestionLevel.of(8).up().value());
    }

    @Test
    void steppingDownFromZeroStaysAtZero() {
        assertEquals(0, CongestionLevel.of(0).down().value());
    }

    @Test
    void tellsUsIfTrafficIsBusy() {
        assertTrue(CongestionLevel.of(6).isBusy());
        assertTrue(CongestionLevel.of(8).isBusy());
        assertTrue(!CongestionLevel.of(3).isBusy());
    }

    @Test
    void tellsUsIfTrafficIsFree() {
        assertTrue(CongestionLevel.of(0).isFree());
        assertTrue(CongestionLevel.of(2).isFree());
        assertTrue(!CongestionLevel.of(5).isFree());
    }

    @Test
    void givesUsALabelWeCanShowInLogs() {
        assertEquals("Clear", CongestionLevel.of(0).label());
        assertEquals("Heavy", CongestionLevel.of(6).label());
        assertEquals("Gridlock", CongestionLevel.of(8).label());
    }

    @Test
    void theHeavyLabelIsWhereTrafficBecomesBusy() {
        for (int value = 0; value <= 8; value++) {
            CongestionLevel level = CongestionLevel.of(value);
            if (value >= 6) {
                assertTrue(level.isBusy(), value + " should be busy");
            } else {
                assertTrue(!level.isBusy(), value + " should not be busy");
            }
        }
    }

    @Test
    void twoLevelsWithTheSameNumberAreEqual() {
        assertEquals(CongestionLevel.of(3), CongestionLevel.of(3));
    }

    @Test
    void theSameNumberInTextGivesTheSameLevel() {
        assertEquals(CongestionLevel.of(3), CongestionLevel.parse("3"));
    }
}