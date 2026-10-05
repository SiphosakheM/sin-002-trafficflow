package co.wethinkcode.trafficflow;

/**
 * What the intersection service told us about one intersection.
 *
 * The two flags are different questions. "known" means the intersection is in
 * the city's records. "routable" means we are willing to send a car through it,
 * which is false for an intersection that is switched off.
 *
 * An intersection that is not known is also not routable, so a caller can
 * always send a route through and check both.
 */
public record IntersectionCheck(String id, boolean known, boolean routable) {

    /** Builds a check for an intersection the city has never heard of. */
    public static IntersectionCheck unknown(String id) {
        return new IntersectionCheck(id, false, false);
    }

    /** Says whether a route may pass through this intersection. */
    public boolean usableForARoute() {
        return known && routable;
    }
}