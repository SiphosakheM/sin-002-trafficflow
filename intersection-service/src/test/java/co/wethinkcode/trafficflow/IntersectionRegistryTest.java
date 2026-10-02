package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntersectionRegistryTest {

    private IntersectionRegistry registry;

    @BeforeEach
    void makeARegistry() {
        registry = new IntersectionRegistry(List.of(
                new Intersection("INT-1001", "Downtown", "4-way", true),
                new Intersection("INT-1002", "Midtown", "pedestrian", true),
                new Intersection("INT-1003", "Downtown", "4-way", false),
                new Intersection("INT-1015", null, "4-way", true)));
    }

    @Test
    void holdsEveryRecord() {
        assertEquals(4, registry.size());
    }

    @Test
    void findsARecordByItsId() {
        assertTrue(registry.find("INT-1001").isPresent());
        assertEquals("Downtown", registry.find("INT-1001").orElseThrow().district());
    }

    @Test
    void theLookupIgnoresCasingAndPadding() {
        assertTrue(registry.find("int-1001").isPresent());
        assertTrue(registry.find("  INT-1001  ").isPresent());
        assertTrue(registry.find("Int-1001").isPresent());
    }

    @Test
    void anUnknownIdGivesNothing() {
        assertTrue(registry.find("INT-9999").isEmpty());
        assertTrue(registry.find("nonsense").isEmpty());
    }

    @Test
    void aBlankOrNullIdGivesNothing() {
        assertTrue(registry.find("").isEmpty());
        assertTrue(registry.find("   ").isEmpty());
        assertTrue(registry.find(null).isEmpty());
    }

    @Test
    void tellsUsIfAnIntersectionIsKnown() {
        assertTrue(registry.isKnown("INT-1001"));
        assertFalse(registry.isKnown("INT-9999"));
    }

    @Test
    void onlyActiveIntersectionsCanBeUsedInARoute() {
        // INT-1003 is switched off in the csv file, INT-1001 is on.
        assertTrue(registry.isRoutable("INT-1001"));
        assertFalse(registry.isRoutable("INT-1003"));
    }

    @Test
    void anUnknownIntersectionIsNotRoutable() {
        assertFalse(registry.isRoutable("INT-9999"));
    }

    @Test
    void aRecordWithNoActiveFlagIsRoutableBecauseWeCannotSayItIsOff() {
        IntersectionRegistry withUnknownFlag = new IntersectionRegistry(List.of(
                new Intersection("INT-1013", "Westside", null, null)));

        assertTrue(withUnknownFlag.isRoutable("INT-1013"));
    }

    @Test
    void listsEachDistrictOnlyOnce() {
        // Downtown appears twice in the records above.
        assertEquals(List.of("Downtown", "Midtown"), registry.districts());
    }

    @Test
    void aRecordWithNoDistrictIsNotInTheDistrictList() {
        assertFalse(registry.districts().contains(null));
        assertEquals(2, registry.districts().size());
    }

    @Test
    void theDistrictLookupIgnoresCasingAndPadding() {
        assertTrue(registry.isKnownDistrict("downtown"));
        assertTrue(registry.isKnownDistrict("  MIDTOWN "));
    }

    @Test
    void anUnknownDistrictIsNotKnown() {
        assertFalse(registry.isKnownDistrict("N suburb"));
        assertFalse(registry.isKnownDistrict(""));
        assertFalse(registry.isKnownDistrict(null));
    }

    @Test
    void givesTheIntersectionsInADistrict() {
        List<Intersection> downtown = registry.inDistrict("Downtown");

        assertEquals(2, downtown.size());
    }

    @Test
    void anUnknownDistrictGivesAnEmptyList() {
        assertTrue(registry.inDistrict("N suburb").isEmpty());
    }

    @Test
    void anEmptyRegistryKnowsNothing() {
        IntersectionRegistry empty = new IntersectionRegistry(List.of());

        assertEquals(0, empty.size());
        assertTrue(empty.districts().isEmpty());
        assertFalse(empty.isKnown("INT-1001"));
    }

    @Test
    void allGivesBackEveryRecord() {
        assertEquals(4, registry.all().size());
    }
}