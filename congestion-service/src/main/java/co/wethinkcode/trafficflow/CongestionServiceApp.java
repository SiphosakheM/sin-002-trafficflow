package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.ActiveMqSender;
import co.wethinkcode.trafficflow.mq.CongestionPublisher;
import co.wethinkcode.trafficflow.mq.MqConfig;
import co.wethinkcode.trafficflow.mq.MessagingUnavailable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The congestion service on port 7022.
 *
 * It keeps the city-wide congestion level, a whole number from 0 to 8, and lets
 * the level be changed over http so we can see what the other services do when
 * traffic gets worse.
 */
public class CongestionServiceApp {

    private static final Logger log = LoggerFactory.getLogger(CongestionServiceApp.class);
    private static final ObjectMapper json = new ObjectMapper();

    private static final int PORT = 7022;

    // The highest number of changes /congestion/history keeps by default.
    private static final int DEFAULT_HISTORY_LIMIT = 100;

    public static void main(String[] args) {
        CongestionTracker tracker = new CongestionTracker(CongestionLevel.of(CongestionLevel.LOWEST));
        tracker.historyLimit(DEFAULT_HISTORY_LIMIT);
        log.info("Congestion service starting at level {}", tracker.currentLevel());

        // The service publishes every level change to the topic, so routing can
        // hear about it without asking us over http.
        CongestionPublisher publisher = new CongestionPublisher(
                new ActiveMqSender(MqConfig.BROKER_URL), json);

        createApp(tracker, publisher).start(PORT);
    }

    /**
     * Builds the service and wires the publisher into every level change.
     *
     * This is the production path. The tests can pass their own publisher in
     * and never touch a broker.
     */
    public static Javalin createApp(CongestionTracker tracker, CongestionPublisher publisher) {
        wireTopicPublishing(tracker, publisher);
        return createApp(tracker);
    }

    /**
     * Trackers we have already put a publishing listener on.
     *
     * The set is weak, so a tracker that nothing else holds can still be
     * collected. This exists so wiring is safe to call more than once — wiring
     * twice would put two listeners on, and one level change would then send
     * two identical messages to the topic.
     */
    private static final Set<CongestionTracker> alreadyWired =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /**
     * Connects a level change to a message on the congestion topic.
     *
     * This is the whole of stage 3 from this side. The tracker does not know
     * about message queues and the publisher does not know about levels; this
     * is the only place the two meet.
     *
     * Safe to call twice for the same tracker. If the broker is down the
     * publisher's failure is logged and swallowed, because the level has
     * already changed by then and that part is not undone by a message failing.
     */
    public static void wireTopicPublishing(CongestionTracker tracker, CongestionPublisher publisher) {
        if (!alreadyWired.add(tracker)) {
            return;
        }

        tracker.onChange((newLevel, oldLevel) -> {
            try {
                publisher.publish(newLevel.value());
            } catch (MessagingUnavailable e) {
                // The level change has happened and cannot be un-sent here, so
                // the best we can do is make the loss loud in the log.
                log.error("Could not publish the new level {}: {}", newLevel.value(), e.getMessage());
            }
        });
    }

    /**
     * Builds the service around a tracker. The tests use this so they can start
     * the service on any free port with a level they choose.
     */
    public static Javalin createApp(CongestionTracker tracker) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));

        // The level right now. The routing service reads this.
        app.get("/congestion", ctx -> ctx.json(describe(tracker)));

        // Sets the level. The body is {"level": 5}.
        app.post("/congestion", ctx -> {
            Integer level = readLevel(ctx.body());
            if (level == null) {
                badRequest(ctx, "Send a body like {\"level\": 5}");
                return;
            }
            tracker.set(CongestionLevel.of(level));
            ctx.json(describe(tracker));
        });

        // Moves the level one step. The body is {"direction": "up"} or "down".
        app.post("/congestion/step", ctx -> {
            String direction = readDirection(ctx.body());
            if (direction == null) {
                badRequest(ctx, "Send a body like {\"direction\": \"up\"} or {\"direction\": \"down\"}");
                return;
            }
            if (direction.equals("up")) {
                tracker.stepUp();
            } else {
                tracker.stepDown();
            }
            ctx.json(describe(tracker));
        });

        // The changes, newest first.
        app.get("/congestion/history", ctx -> {
            var history = tracker.history().stream().map(change -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("level", change.level().value());
                entry.put("label", change.level().label());
                entry.put("reason", change.reason());
                entry.put("at", change.at());
                return entry;
            }).toList();
            ctx.json(history);
        });

        // Changes how many changes we keep. Without this the history would grow
        // for as long as the service runs.
        app.post("/congestion/history/limit", ctx -> {
            Integer limit = readLimit(ctx.body());
            if (limit == null) {
                badRequest(ctx, "Send a body like {\"limit\": 20}");
                return;
            }
            tracker.historyLimit(limit);
            ctx.json(Map.of("limit", limit, "kept", tracker.history().size()));
        });

        return app;
    }

    /**
     * The level in the shape the other services read it, with a couple of extra
     * words that make the answer easier to read.
     */
    private static Map<String, Object> describe(CongestionTracker tracker) {
        CongestionLevel level = tracker.current();
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("level", level.value());
        answer.put("label", level.label());
        answer.put("busy", level.isBusy());
        return answer;
    }

    /**
     * Reads the level out of the request body. Gives back null when the body is
     * not json, is not an object, or has no whole number level in it. We do not
     * throw here, because a bad request from another service should give that
     * service a 400 and not a stack trace.
     */
    private static Integer readLevel(String body) {
        JsonNode parsed = parseObject(body);
        if (parsed == null) {
            return null;
        }
        JsonNode level = parsed.get("level");
        if (level == null || !level.isIntegralNumber()) {
            return null;
        }
        int value = level.asInt();
        if (value < CongestionLevel.LOWEST || value > CongestionLevel.HIGHEST) {
            // Out of range is also a bad request, not a server error.
            return null;
        }
        return value;
    }

    private static String readDirection(String body) {
        JsonNode parsed = parseObject(body);
        if (parsed == null) {
            return null;
        }
        JsonNode direction = parsed.get("direction");
        if (direction == null || !direction.isTextual()) {
            return null;
        }
        String value = direction.asText().trim().toLowerCase();
        if (value.equals("up") || value.equals("down")) {
            return value;
        }
        return null;
    }

    private static Integer readLimit(String body) {
        JsonNode parsed = parseObject(body);
        if (parsed == null) {
            return null;
        }
        JsonNode limit = parsed.get("limit");
        if (limit == null || !limit.isIntegralNumber() || limit.asInt() < 0) {
            return null;
        }
        return limit.asInt();
    }

    // Turns a request body into a json object, or gives back null when we cannot.
    private static JsonNode parseObject(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = json.readTree(body);
            return parsed.isObject() ? parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    // 400 says the request was wrong, which is the caller's problem to fix.
    private static void badRequest(Context ctx, String hint) {
        ctx.status(400).result("That request was not understood. " + hint
                + " The level must be a whole number between "
                + CongestionLevel.LOWEST + " and " + CongestionLevel.HIGHEST + ".");
    }
}