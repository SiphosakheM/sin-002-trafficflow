package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the estimated travel time.
 *
 * The whole point of this class is that the number is worked out from the
 * distance, the intersections and the congestion — never just printed out.
 * These tests pin that down.
 */
class TravelTimeEstimatorTest {

    private final TravelTimeEstimator estimator = new TravelTimeEstimator();

    private RouteRequest route(double distanceKm) {
        return new RouteRequest("INT-1001", "INT-1005", distanceKm);
    }

    /** Both ends of the route are fine, so nothing is in the way. */
    private RouteEstimate estimate(RouteRequest route, int level) throws Exception {
        return estimator.estimate(route,
                new IntersectionCheck("INT-1001", true, true, "4-way"),
                new IntersectionCheck("INT-1005", true, true, "4-way"),
                new CongestionReading(level, CongestionReading.labelFor(level), level >= 6));
    }

    @Test
    void tenKilometresOnClearRoadsIsFifteenMinutesBeforeTheJunctions() throws Exception {
        // 10 km at 40 km/h is 15 minutes of driving.
        RouteEstimate answer = estimate(route(10), 0);

        assertEquals(15, answer.freeFlowMinutes());
    }

    @Test
    void theJunctionsAddTheirSecondsOnTopOfTheDriving() throws Exception {
        RouteEstimate answer = estimate(route(10), 0);

        // Two 4-way signals, 20 seconds each, is 40 seconds on 15 minutes.
        assertEquals(40, answer.intersectionDelaySeconds());
        assertEquals(16, answer.minutes());
    }

    @Test
    void twiceTheDistanceTakesAboutTwiceAsLongOnClearRoads() throws Exception {
        assertEquals(30, estimate(route(20), 0).freeFlowMinutes());
    }

    @Test
    void theFreeFlowPartIsShownSeparatelyFromTheJunctions() throws Exception {
        RouteEstimate answer = estimate(route(10), 0);

        // The answer has to be arguable, so both parts are in it.
        assertEquals(15, answer.freeFlowMinutes());
        assertTrue(answer.minutes() > answer.freeFlowMinutes());
    }

    @Test
    void congestionMakesTheSameRouteTakeLonger() throws Exception {
        int clear = estimate(route(10), 0).minutes();
        int bad = estimate(route(10), 8).minutes();

        assertTrue(bad > clear, "gridlock must be slower than clear roads");
    }

    @Test
    void theTimeGrowsWithEveryStepUpOfTheLevel() throws Exception {
        int previous = 0;

        for (int level = 0; level <= 8; level++) {
            int minutes = estimate(route(10), level).minutes();

            assertTrue(minutes >= previous,
                    "level " + level + " (" + minutes + " min) must not be quicker than level "
                            + (level - 1) + " (" + previous + " min)");
            previous = minutes;
        }
    }

    @Test
    void gridlockTriplesTheClearRoadTime() throws Exception {
        // Level 8 is 1 + 8 * 0.25 = 3 times as slow.
        RouteEstimate clear = estimate(route(10), 0);
        RouteEstimate gridlock = estimate(route(10), 8);

        assertEquals(3.0, gridlock.congestionFactor());
        assertEquals(47, gridlock.minutes());
        assertTrue(gridlock.minutes() > clear.minutes() * 2);
    }

    @Test
    void theAnswerSaysWhichLevelItUsed() throws Exception {
        RouteEstimate answer = estimate(route(10), 6);

        assertEquals(6, answer.congestionLevel());
        assertEquals("Heavy", answer.congestionLabel());
    }

    @Test
    void theAnswerKeepsTheRouteItWasAskedAbout() throws Exception {
        RouteEstimate answer = estimate(route(4.2), 3);

        assertEquals("INT-1001", answer.from());
        assertEquals("INT-1005", answer.to());
        assertEquals(4.2, answer.distanceKm());
    }

    @Test
    void theAnswerShowsItsWorkingSoTheNumberCanBeArguedWith() throws Exception {
        RouteEstimate answer = estimate(route(10), 0);

        assertEquals(40.0, answer.averageSpeedKmh());
        assertEquals(1.0, answer.congestionFactor());
    }

    @Test
    void anUncontrolledJunctionIsQuickerThanASignalisedOne() throws Exception {
        RouteEstimate signalled = estimate(route(10), 0);
        RouteEstimate uncontrolled = estimator.estimate(route(10),
                new IntersectionCheck("INT-1001", true, true, "4-way"),
                new IntersectionCheck("INT-1013", true, true, null),
                new CongestionReading(0, "Clear", false));

        // Nothing stops a car at an uncontrolled junction, so it costs the least.
        assertTrue(uncontrolled.intersectionDelaySeconds() < signalled.intersectionDelaySeconds(),
                "an uncontrolled junction should cost less time than a signal light");
        assertTrue(uncontrolled.minutes() <= signalled.minutes());
    }

    @Test
    void aSignalLightCostsMoreThanARoundabout() throws Exception {
        RouteEstimate roundabouts = estimator.estimate(route(10),
                new IntersectionCheck("INT-1001", true, true, "roundabout"),
                new IntersectionCheck("INT-1005", true, true, "roundabout"),
                new CongestionReading(0, "Clear", false));
        RouteEstimate lights = estimator.estimate(route(10),
                new IntersectionCheck("INT-1002", true, true, "4-way"),
                new IntersectionCheck("INT-1006", true, true, "4-way"),
                new CongestionReading(0, "Clear", false));

        assertTrue(lights.minutes() > roundabouts.minutes(),
                "signal lights should cost more than a roundabout");
    }

    @Test
    void aRoundaboutAndASignalLightOnTheSameDistanceGiveDifferentTimes() throws Exception {
        // The same distance, so the only difference can be the junctions.
        assertTrue(estimator.estimate(route(10),
                new IntersectionCheck("INT-1001", true, true, "roundabout"),
                new IntersectionCheck("INT-1005", true, true, "roundabout"),
                new CongestionReading(0, "Clear", false)).minutes()
                < estimator.estimate(route(10),
                new IntersectionCheck("INT-1001", true, true, "4-way"),
                new IntersectionCheck("INT-1005", true, true, "4-way"),
                new CongestionReading(0, "Clear", false)).minutes());
    }

    @Test
    void weRefuseToEstimateThroughAnIntersectionWeHaveNeverHeardOf() {
        IntersectionLookupUnknown error = assertThrows(
                IntersectionLookupUnknown.class,
                () -> estimator.estimate(route(10),
                        new IntersectionCheck("INT-9999", false, false, null),
                        new IntersectionCheck("INT-1005", true, true, "4-way"),
                        new CongestionReading(0, "Clear", false)));

        assertTrue(error.getMessage().contains("INT-9999"));
    }

    @Test
    void weRefuseToEstimateThroughAnIntersectionThatIsSwitchedOff() {
        IntersectionLookupUnknown error = assertThrows(
                IntersectionLookupUnknown.class,
                () -> estimator.estimate(route(10),
                        new IntersectionCheck("INT-1009", true, false, "4-way"),
                        new IntersectionCheck("INT-1005", true, true, "4-way"),
                        new CongestionReading(0, "Clear", false)));

        assertTrue(error.getMessage().contains("INT-1009"));
    }

    @Test
    void theRefusalSaysTheIntersectionIsNotKnownOrNotUsable() {
        IntersectionLookupUnknown unknown = assertThrows(
                IntersectionLookupUnknown.class,
                () -> estimator.estimate(route(10),
                        new IntersectionCheck("INT-9999", false, false, null),
                        new IntersectionCheck("INT-1005", true, true, "4-way"),
                        new CongestionReading(0, "Clear", false)));
        IntersectionLookupUnknown closed = assertThrows(
                IntersectionLookupUnknown.class,
                () -> estimator.estimate(route(10),
                        new IntersectionCheck("INT-1009", true, false, "4-way"),
                        new IntersectionCheck("INT-1005", true, true, "4-way"),
                        new CongestionReading(0, "Clear", false)));

        assertTrue(unknown.getMessage().contains("not known"));
        assertTrue(closed.getMessage().contains("not usable"));
    }

    @Test
    void aVeryShortRouteStillGetsAtLeastOneMinute() throws Exception {
        // A route of almost nothing still has to cost something.
        assertTrue(estimate(route(0.01), 0).minutes() >= 1);
    }

    @Test
    void theEstimateIsTheSameEveryTimeForTheSameInputs() throws Exception {
        RouteEstimate first = estimate(route(7.5), 5);
        RouteEstimate second = estimate(route(7.5), 5);

        assertEquals(first.minutes(), second.minutes());
    }
}