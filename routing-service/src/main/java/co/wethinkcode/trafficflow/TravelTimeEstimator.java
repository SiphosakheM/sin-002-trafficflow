package co.wethinkcode.trafficflow;

import java.util.Locale;
import java.util.Map;

/**
 * Works out how long a route should take.
 *
 * The number is not hardcoded anywhere. It is worked out from three things:
 *
 * <ol>
 *   <li>the distance, over a speed that drops as the route crosses more
 *       intersections, because each one is a place to slow down</li>
 *   <li>the kind of each intersection — a signal light costs a car more time
 *       than a roundabout, and an uncontrolled junction costs more than
 *       either, because nothing keeps the traffic moving</li>
 *   <li>the congestion level, which multiplies the whole thing</li>
 * </ol>
 *
 * The route has to be drivable before we estimate it. An intersection we do
 * not know, or one that is switched off, is refused instead of estimated,
 * because a time for a road nobody can use is worse than no answer.
 */
public class TravelTimeEstimator {

    /** The speed on a road with nothing in the way, in km/h. */
    public static final double BASE_SPEED_KMH = 60;

    /** Each intersection on the route takes this off the average speed. */
    public static final double SPEED_COST_PER_INTERSECTION_KMH = 10;

    /** However bad it gets, we never assume slower than this. */
    public static final double SLOWEST_SPEED_KMH = 15;

    /** How much each congestion level adds to the time. */
    public static final double CONGESTION_COST_PER_LEVEL = 0.25;

    /** Seconds a car loses at a signal light. */
    private static final int SIGNAL_DELAY_SECONDS = 20;

    /** Seconds a car loses at a stop sign. */
    private static final int STOP_SIGN_DELAY_SECONDS = 12;

    /** Seconds a car loses going round a roundabout. */
    private static final int ROUNDABOUT_DELAY_SECONDS = 6;

    /**
     * Seconds a car loses at a junction with no control at all.
     *
     * The smallest of the four, because nothing makes the car stop there. It
     * still costs something, because cars give way to each other and slow down.
     */
    private static final int UNCONTROLLED_DELAY_SECONDS = 3;

    /** Even a tiny route costs at least this, because driving is not free. */
    private static final int MINIMUM_MINUTES = 1;

    /**
     * Estimates one route.
     *
     * @throws IntersectionLookupUnknown when either end of the route is not
     *         an intersection we are willing to send a car through.
     */
    public RouteEstimate estimate(RouteRequest route,
                                  IntersectionCheck from,
                                  IntersectionCheck to,
                                  CongestionReading congestion) throws IntersectionLookupUnknown {

        // Check the route can be driven before spending any effort on it.
        requireUsable(from);
        requireUsable(to);

        double averageSpeed = averageSpeedKmh(route.intersectionCount());
        double freeFlowMinutes = (route.distanceKm() / averageSpeed) * 60;
        double congestionFactor = congestionFactor(congestion.level());
        int intersectionDelay = delaySecondsFor(from) + delaySecondsFor(to);

        // The seconds lost at the junctions are worth less once the route is
        // slowed down by traffic anyway, so they are scaled the same way.
        double minutes = freeFlowMinutes * congestionFactor
                + ((double) intersectionDelay / 60) * congestionFactor;

        return new RouteEstimate(
                route.from(),
                route.to(),
                route.distanceKm(),
                Math.max(MINIMUM_MINUTES, (int) Math.round(minutes)),
                (int) Math.round(freeFlowMinutes),
                congestion.level(),
                congestion.label(),
                averageSpeed,
                congestionFactor,
                intersectionDelay);
    }

    /**
     * The speed we expect over a route that crosses this many intersections.
     *
     * Each junction is a place to slow down, so more junctions means a lower
     * average. There is a floor, because a route through twenty traffic lights
     * is slow but it is not a car park.
     */
    public double averageSpeedKmh(int intersectionCount) {
        double speed = BASE_SPEED_KMH - (intersectionCount * SPEED_COST_PER_INTERSECTION_KMH);
        return Math.max(SLOWEST_SPEED_KMH, speed);
    }

    /**
     * How much slower traffic makes things.
     *
     * Level 0 is 1, so clear roads are the plain travel time with nothing
     * added. Each level up adds a quarter, so level 8 is three times as slow.
     */
    public double congestionFactor(int level) {
        return 1 + (level * CONGESTION_COST_PER_LEVEL);
    }

    /**
     * Seconds a car loses at one intersection, from the kind it is.
     *
     * A missing signal type is treated as an uncontrolled junction. That is
     * the honest reading of "we do not know what this is": there is nothing
     * there to stop the car, so it costs the least. If we invented a signal
     * light instead, every estimate for an unknown junction would be 17
     * seconds too slow for no reason.
     */
    int delaySecondsFor(IntersectionCheck check) {
        String type = check.signalType();
        if (type == null || type.isBlank()) {
            return UNCONTROLLED_DELAY_SECONDS;
        }

        return switch (type.trim().toLowerCase(Locale.ROOT)) {
            case "roundabout" -> ROUNDABOUT_DELAY_SECONDS;
            case "stop-sign", "stop sign", "stop_sign", "give-way", "give way" -> STOP_SIGN_DELAY_SECONDS;
            default -> SIGNAL_DELAY_SECONDS;
        };
    }

    /**
     * Refuses an intersection we cannot send a car through, saying which one
     * and why.
     */
    private void requireUsable(IntersectionCheck check) throws IntersectionLookupUnknown {
        if (!check.known()) {
            throw new IntersectionLookupUnknown(
                    "The intersection " + check.id() + " is not known to the intersection service.");
        }
        if (!check.routable()) {
            throw new IntersectionLookupUnknown(
                    "The intersection " + check.id() + " is not usable for a route, because it is not active.");
        }
    }

    /**
     * A small table of the speeds, for the readme and for anyone debugging.
     */
    public Map<Integer, Double> speedsByIntersectionCount() {
        return Map.of(2, averageSpeedKmh(2), 4, averageSpeedKmh(4), 6, averageSpeedKmh(6));
    }
}