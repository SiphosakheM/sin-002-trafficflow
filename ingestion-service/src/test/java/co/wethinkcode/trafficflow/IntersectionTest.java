package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IntersectionTest {

    @Test
    void keepsAllTheValues() {
        Intersection i = new Intersection("INT-1001", "Downtown", "4-way", true);

        assertEquals("INT-1001", i.id());
        assertEquals("Downtown", i.district());
        assertEquals("4-way", i.signalType());
        assertEquals(true, i.active());
    }

    @Test
    void allowsMissingValuesAsNull() {
        Intersection i = new Intersection("INT-1015", null, "4-way", true);

        assertNull(i.district());
    }
}
