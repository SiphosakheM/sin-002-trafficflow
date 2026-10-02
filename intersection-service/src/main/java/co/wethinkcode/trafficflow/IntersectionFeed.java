package co.wethinkcode.trafficflow;

import java.util.List;

/**
 * Where the intersections come from.
 *
 * The real one is IngestionClient, which calls the ingestion service over http.
 * The tests use their own version so they do not need that service running.
 */
@FunctionalInterface
public interface IntersectionFeed {

    /**
     * Gives back all the intersections, or throws if we cannot get them.
     */
    List<Intersection> fetchAll() throws IngestionUnavailableException;
}