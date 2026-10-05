package co.wethinkcode.trafficflow;

/**
 * Reads the current congestion level from somewhere.
 *
 * An interface so the routing service can be tested without a real congestion
 * service, and so stage 3 can swap the http polling for an ActiveMQ
 * subscription by giving the routing service a different implementation of the
 * same idea.
 */
public interface CongestionLookup {

    /**
     * Reads the current congestion level.
     *
     * @throws CongestionLookupUnavailable when the level could not be read.
     */
    CongestionReading read() throws CongestionLookupUnavailable;

    /** A quick "is the source there?" for the readiness answer. */
    boolean isReachable();
}