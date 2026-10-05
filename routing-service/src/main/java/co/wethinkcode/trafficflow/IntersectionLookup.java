package co.wethinkcode.trafficflow;

/**
 * Asks the intersection service whether an intersection can be used.
 *
 * An interface so the routing service can be tested without a real
 * intersection service running, and so stage 3 can swap in something else if
 * it ever needs to.
 */
public interface IntersectionLookup {

    /**
     * Asks about one intersection.
     *
     * @throws IntersectionLookupUnavailable when the intersection service could
     *         not be reached or did not answer in a way we understand.
     */
    IntersectionCheck check(String id) throws IntersectionLookupUnavailable;
}