package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MessagingUnavailable;
import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The routing service on port 7023.
 *
 * This is the only service that talks to the other two. It asks the
 * intersection service whether both ends of a route can be driven, asks the
 * congestion service how bad the traffic is, and turns those two answers into
 * an estimated travel time.
 *
 * The congestion level comes from a subscription to the topic rather than from
 * http, because the congestion service now publishes every change and routing
 * can hear about it without asking. Routing therefore keeps one value between
 * requests: the last level the topic told it. That is a departure from the rule
 * it had before, that nothing here is cached. Intersections are still asked for
 * fresh on every request, because they come and go and a stale one is a wrong
 * number in somebody's satnav.
 */
public class RoutingServiceApp {

    private static final Logger log = LoggerFactory.getLogger(RoutingServiceApp.class);

    private static final int PORT = 7023;
    private static final String INTERSECTION_URL = "http://localhost:7021";
    private static final String CONGESTION_URL = "http://localhost:7022";

    public static void main(String[] args) {
        ObjectMapper json = new ObjectMapper();
        IntersectionLookup intersections = new IntersectionClient(INTERSECTION_URL, json);

        // Congestion comes from the topic. The subscription is started here
        // rather than inside createApp so the tests can keep injecting fakes.
        TopicCongestionLookup congestion = new TopicCongestionLookup(json);
        try {
            congestion.start(MqConfig.BROKER_URL);
        } catch (MessagingUnavailable e) {
            // Without the topic the service still runs; it just has no level to
            // work with until the seed below supplies one.
            log.warn("Could not subscribe to {}: {}", MqConfig.TOPIC, e.getMessage());
        }
        seedIfUnknown(congestion, new CongestionClient(CONGESTION_URL, json));

        Javalin app = createApp(intersections, congestion, json);
        Runtime.getRuntime().addShutdownHook(new Thread(congestion::close));
        app.start(PORT);
    }

    /**
     * Gives routing its first congestion level, if the topic has not produced
     * one yet.
     *
     * A topic only announces changes, and on a quiet network the level sits at
     * 0 with nothing to say. Rather than assume 0 means clear roads, routing
     * asks the congestion service once. This runs at startup and never again, so
     * routing still does not poll — after this the topic is the only source.
     *
     * @param lookup   where routing reads the level from
     * @param fallback the congestion service over http, used only if needed
     */
    public static void seedIfUnknown(TopicCongestionLookup lookup, CongestionLookup fallback) {
        if (lookup.isReachable()) {
            return;
        }

        try {
            CongestionReading first = fallback.read();
            lookup.seed(first);
            log.info("Seeded the congestion level at {} from {}, now following the topic",
                    first.level(), CONGESTION_URL);
        } catch (CongestionLookupUnavailable e) {
            log.warn("No congestion level yet and the congestion service is no help: {}. "
                    + "Route requests will answer 503 until the topic speaks.", e.getMessage());
        }
    }

    /**
     * Builds the service around the two lookups.
     *
     * The tests pass fakes in here so they can start the service on any free
     * port and drive it over http without the other two services running.
     */
    public static Javalin createApp(IntersectionLookup intersections,
                                    CongestionLookup congestion,
                                    ObjectMapper json) {
        Javalin app = Javalin.create();
        TravelTimeEstimator estimator = new TravelTimeEstimator();

        // /health is a liveness check, so it says OK even when the services this
        // one depends on are down. /status is the one that explains the problem.
        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/status", ctx -> {
            boolean intersectionUp = intersections.isReachable();
            boolean congestionUp = congestion.isReachable();

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("state", intersectionUp && congestionUp ? "READY" : "DEGRADED");
            status.put("intersectionService", intersectionUp);
            status.put("congestionService", congestionUp);
            ctx.json(status);
        });

        app.post("/route", ctx -> estimateRoute(ctx, intersections, congestion, estimator, json));

        return app;
    }

    /**
     * Answers one route request.
     *
     * The order matters. The route is checked first, so a silly request costs
     * no http calls at all. Then the two intersections, then the congestion
     * level, and only then is anything worked out.
     */
    private static void estimateRoute(Context ctx,
                                      IntersectionLookup intersections,
                                      CongestionLookup congestion,
                                      TravelTimeEstimator estimator,
                                      ObjectMapper json) {
        RouteRequest route;
        try {
            route = RouteRequest.fromJson(ctx.body(), json);
        } catch (BadRouteRequestException e) {
            // 400: the request was wrong, and that is the caller's to fix.
            ctx.status(400).result(e.getMessage());
            return;
        }

        IntersectionCheck from;
        IntersectionCheck to;
        CongestionReading level;
        try {
            from = intersections.check(route.from());
            to = intersections.check(route.to());
        } catch (IntersectionLookupUnavailable e) {
            // 503: we could not do our job because someone else is down.
            log.warn("Cannot reach the intersection service: {}", e.getMessage());
            ctx.status(503).result(e.getMessage());
            return;
        }

        try {
            level = congestion.read();
        } catch (CongestionLookupUnavailable e) {
            log.warn("Cannot reach the congestion service: {}", e.getMessage());
            ctx.status(503).result(e.getMessage());
            return;
        }

        try {
            ctx.json(estimator.estimate(route, from, to, level).asMap());
        } catch (IntersectionLookupUnknown e) {
            // 404 when the city has never heard of the intersection, and 422
            // when it knows it but the road cannot be used. Both are about the
            // route itself, not about us or the other services.
            boolean unknown = !from.known() || !to.known();
            ctx.status(unknown ? 404 : 422).result(e.getMessage());
        }
    }
}