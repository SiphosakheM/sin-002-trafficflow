package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

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

        createApp(tracker).start(PORT);
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