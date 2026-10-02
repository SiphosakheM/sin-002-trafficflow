package co.wethinkcode.trafficflow;

/**
 * We could not get the intersections from the ingestion service.
 *
 * This is its own exception type so the service can tell the difference
 * between "the ingestion service is down" and a bug in our own code.
 */
public class IngestionUnavailableException extends Exception {

    public IngestionUnavailableException(String message) {
        super(message);
    }

    public IngestionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}