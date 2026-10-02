package co.wethinkcode.trafficflow;

import java.util.List;

/**
 * Holds the intersections this service knows about, and says whether they are
 * any good.
 *
 * The records come from the ingestion service. If that service is down we do
 * not want this service to die as well, because then nobody can answer "is
 * INT-1001 a real intersection?". So we start anyway, we say we are DEGRADED,
 * and /status explains why. POST /reload tries again.
 */
public class IntersectionCatalogue {

    /**
     * READY means we have the records from the ingestion service.
     * DEGRADED means we could not read them, so we are running on nothing or
     * on the last records we managed to get.
     */
    public enum State {
        READY,
        DEGRADED
    }

    /**
     * What we know about our own health, for the /status endpoint.
     */
    public record Status(State state, int count, String message) {
    }

    private final IntersectionFeed feed;

    // The records we hold now. Empty until the first successful read.
    private volatile IntersectionRegistry registry = new IntersectionRegistry(List.of());

    // Why we are degraded, or an empty string when everything is fine.
    private volatile String problem = "The ingestion service has not been read yet";

    public IntersectionCatalogue(IntersectionFeed feed) {
        this.feed = feed;
    }

    /**
     * Reads the intersections again.
     *
     * We never let an error out of here. If the read fails we keep whatever we
     * already had and mark ourselves DEGRADED, because old data is better than
     * no data for a service whose job is to be the source of truth.
     */
    public void refresh() {
        try {
            registry = new IntersectionRegistry(feed.fetchAll());
            problem = "";
        } catch (IngestionUnavailableException e) {
            problem = e.getMessage();
        } catch (RuntimeException e) {
            // An error we did not expect is still a reason to say we are degraded.
            problem = "Unexpected problem reading the ingestion service: " + e.getMessage();
        }
    }

    /**
     * The records we hold right now.
     */
    public IntersectionRegistry registry() {
        return registry;
    }

    /**
     * How many records we hold.
     */
    public int size() {
        return registry.size();
    }

    /**
     * Are we healthy? Tells /status what to answer.
     */
    public Status status() {
        State state = problem.isEmpty() ? State.READY : State.DEGRADED;
        String message = problem.isEmpty()
                ? "Loaded " + registry.size() + " intersections from the ingestion service"
                : problem;
        return new Status(state, registry.size(), message);
    }
}