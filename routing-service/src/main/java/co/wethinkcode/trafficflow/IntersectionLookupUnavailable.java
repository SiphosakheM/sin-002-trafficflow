package co.wethinkcode.trafficflow;

/**
 * We could not ask the intersection service, or it did not answer properly.
 *
 * This is its own type so the routing service can tell "the intersection
 * service is down" apart from a bad route request. The first is a 503, the
 * second is a 400.
 */
public class IntersectionLookupUnavailable extends Exception {

    public IntersectionLookupUnavailable(String message) {
        super(message);
    }

    public IntersectionLookupUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}