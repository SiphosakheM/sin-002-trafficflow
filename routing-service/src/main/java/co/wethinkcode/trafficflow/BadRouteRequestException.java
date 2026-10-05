package co.wethinkcode.trafficflow;

/**
 * The caller sent us something we cannot work with.
 *
 * This is a checked exception so the service has to decide what to do about it,
 * which is answer 400. It is separate from "the intersection service is down",
 * because that one is a 503 and is not the caller's fault.
 */
public class BadRouteRequestException extends Exception {

    public BadRouteRequestException(String message) {
        super(message);
    }
}