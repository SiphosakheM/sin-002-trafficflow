package co.wethinkcode.trafficflow;

/**
 * We could not ask the congestion service, or it did not answer properly.
 *
 * Its own type so the routing service can answer 503 for this, instead of
 * blaming the caller with a 400.
 */
public class CongestionLookupUnavailable extends Exception {

    public CongestionLookupUnavailable(String message) {
        super(message);
    }

    public CongestionLookupUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}