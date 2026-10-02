package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * The ingestion service on port 7020.
 *
 * It reads the old messy csv file once when it starts, cleans it, and then
 * gives the clean records to whoever asks.
 */
public class IngestionServiceApp {

    private static final Logger log = LoggerFactory.getLogger(IngestionServiceApp.class);

    private static final int PORT = 7020;
    private static final String CSV_FILE = "intersections-legacy.csv";

    public static void main(String[] args) {
        IntersectionStore store = loadTheStore();
        createApp(store).start(PORT);
    }

    /**
     * Builds the service with the records already in memory.
     * The tests use this so they can start the service on any free port.
     */
    public static Javalin createApp(IntersectionStore store) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));

        // All the clean intersections. This is what intersection-service reads.
        app.get("/intersections", ctx -> ctx.json(store.all()));

        // How many clean records we have.
        app.get("/intersections/count", ctx -> ctx.result(String.valueOf(store.size())));

        // One intersection by its id, or 404 when we do not know it.
        app.get("/intersections/{id}", ctx -> {
            String id = ctx.pathParam("id");
            store.findById(id)
                    .ifPresentOrElse(
                            found -> ctx.json(found),
                            () -> ctx.status(404).result("No intersection with id " + id));
        });

        // Every district name we know about.
        app.get("/districts", ctx -> ctx.json(store.districts()));

        return app;
    }

    /**
     * Builds the service by reading the csv file.
     */
    public static Javalin createApp() {
        return createApp(loadTheStore());
    }

    /**
     * Reads and cleans the csv file. We do this once at start up, so every
     * request after that is answered from memory and is fast.
     */
    private static IntersectionStore loadTheStore() {
        try {
            IntersectionStore store = IntersectionStore.loadFromResource(CSV_FILE);
            log.info("Loaded {} clean intersections from {}", store.size(), CSV_FILE);
            return store;
        } catch (IOException e) {
            // Without the file there is nothing to serve, so we stop here
            // instead of starting a service that always gives errors.
            log.error("Cannot start, the file {} could not be read: {}", CSV_FILE, e.getMessage());
            throw new IllegalStateException("Cannot read " + CSV_FILE, e);
        }
    }
}