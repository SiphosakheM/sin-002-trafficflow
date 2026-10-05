package co.wethinkcode.trafficflow;

/**
 * What the intersection service told us about one intersection.
 *
 * The two flags are different questions. "known" means the intersection is in
 * the city's records. "routable" means we are willing to send a car through
 * it, which is false for one that is switched off.
 *
 * An intersection that is not known is also not routable, so a caller can
 * always send a route through and check both.
 *
 * signalType is what kind of crossing it is — "4-way", "roundabout" and so on.
 * It can be null, because stage 1 leaves unknown values as null rather than
 * making something up. The estimator reads a null as an uncontrolled junction,
 * where nothing makes a car stop, so it costs the least time of all.
 */
public record IntersectionCheck(String id, boolean known, boolean routable, String signalType) {

    /** Builds a check for an intersection the city has never heard of. */
    public static IntersectionCheck unknown(String id) {
        return new IntersectionCheck(id, false, false, null);
    }

    /** Says whether a route may pass through this intersection. */
    public boolean usableForARoute() {
        return known && routable;
    }
}