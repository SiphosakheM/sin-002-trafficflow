package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.HeartbeatScheduler;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * The intersection service on port 7021.
 *
 * This service is the source of truth for intersection and district names. It
 * asks the ingestion service (port 7020) for the clean records when it starts
 * and keeps them in memory, so the other services get a fast answer.
 */
public class IntersectionServiceApp {

    private static final Logger log = LoggerFactory.getLogger(IntersectionServiceApp.class);

    private static final int PORT = 7021;
    private static final String INGESTION_URL = "http://localhost:7020";

    public static void main(String[] args) {
        IntersectionCatalogue catalogue = new IntersectionCatalogue(new IngestionClient(INGESTION_URL));
        catalogue.refresh();
        logStatus(catalogue);

        HeartbeatScheduler heartbeat = HeartbeatScheduler.createDefault();
        heartbeat.start();

        Javalin app = createApp(catalogue);
        Runtime.getRuntime().addShutdownHook(new Thread(heartbeat::close));
        app.start(PORT);
    }

    /**
     * Builds the service around a catalogue that is already loaded.
     * The tests use this so they can start the service on any free port and
     * with a fake ingestion service.
     */
    public static Javalin createApp(IntersectionCatalogue catalogue) {
        Javalin app = Javalin.create();

        // /health is a liveness check, so it says OK even when we are degraded.
        // /status is the one that explains what is wrong.
        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/status", ctx -> {
            IntersectionCatalogue.Status status = catalogue.status();
            ctx.json(Map.of(
                    "state", status.state().name(),
                    "count", status.count(),
                    "message", status.message()));
        });

        // Every intersection we know about.
        app.get("/intersections", ctx -> {
            if (isDegraded(catalogue)) {
                unavailable(ctx, catalogue);
                return;
            }
            ctx.json(catalogue.registry().all());
        });

        // How many we know about. This one still answers when we are degraded,
        // because 0 is a real answer.
        app.get("/intersections/count", ctx -> ctx.json(Map.of("count", catalogue.size())));

        // One intersection, or 404 when the id is not one of ours.
        app.get("/intersections/{id}", ctx -> {
            String id = ctx.pathParam("id");
            catalogue.registry().find(id)
                    .ifPresentOrElse(
                            found -> ctx.json(found),
                            () -> ctx.status(404).result("No intersection with id " + id));
        });

        // One call that tells the routing service what it needs to know about an
        // intersection: do we know it, and can a route use it?
        app.get("/intersections/{id}/check", ctx -> {
            String id = ctx.pathParam("id");
            ctx.json(Map.of(
                    "id", id,
                    "known", catalogue.registry().isKnown(id),
                    "routable", catalogue.registry().isRoutable(id)));
        });

        // Every district name, each one only once.
        app.get("/districts", ctx -> {
            if (isDegraded(catalogue)) {
                unavailable(ctx, catalogue);
                return;
            }
            ctx.json(catalogue.registry().districts());
        });

        // Is this a district we know? Used to check a route before we plan it.
        app.get("/districts/{name}", ctx -> {
            String name = ctx.pathParam("name");
            if (!catalogue.registry().isKnownDistrict(name)) {
                ctx.status(404).result("No district with name " + name);
                return;
            }
            ctx.json(Map.of(
                    "district", name,
                    "intersectionCount", catalogue.registry().inDistrict(name).size()));
        });

        // Read the ingestion service again, in case it was down when we started.
        app.post("/reload", ctx -> {
            catalogue.refresh();
            if (isDegraded(catalogue)) {
                unavailable(ctx, catalogue);
                return;
            }
            logStatus(catalogue);
            ctx.result("Reloaded " + catalogue.size() + " intersections");
        });

        return app;
    }

    private static boolean isDegraded(IntersectionCatalogue catalogue) {
        return catalogue.status().state() == IntersectionCatalogue.State.DEGRADED;
    }

    // 503 says "this service cannot answer right now", which is different from
    // an empty answer that means "there is nothing here".
    private static void unavailable(Context ctx, IntersectionCatalogue catalogue) {
        ctx.status(503).result(
                "The intersection service cannot answer right now: " + catalogue.status().message());
    }

    private static void logStatus(IntersectionCatalogue catalogue) {
        IntersectionCatalogue.Status status = catalogue.status();
        log.info("Intersection service is {} with {} intersections: {}",
                status.state(), status.count(), status.message());
    }
}