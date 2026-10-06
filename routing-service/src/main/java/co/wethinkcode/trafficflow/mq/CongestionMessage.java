package co.wethinkcode.trafficflow.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * What goes onto the congestion topic when the level changes.
 *
 * This is the whole contract between congestion-service and routing-service, so
 * it is deliberately small: the level, and the word for it so a message can be
 * read by a human in a log or the web console.
 *
 * The level is checked here as well as in the tracker. A message can come from
 * somewhere other than our own tracker one day, and a level of 9 should never
 * leave this process whatever the reason.
 */
public record CongestionMessage(int level) {

    /** Level 0 means the roads are empty. */
    public static final int LOWEST = 0;

    /** Level 8 means gridlock. */
    public static final int HIGHEST = 8;

    public CongestionMessage {
        if (level < LOWEST || level > HIGHEST) {
            throw new IllegalArgumentException(
                    "The congestion level must be between " + LOWEST + " and " + HIGHEST
                            + ", but this message carries " + level + ".");
        }
    }

    /**
     * The message as the json that goes on the wire.
     */
    public String asJson(ObjectMapper json) {
        try {
            return json.writeValueAsString(new Body(level, labelFor(level)));
        } catch (Exception e) {
            // Writing two ints and a string cannot really fail, but if it ever
            // did we would rather hear about it than send a broken message.
            throw new IllegalStateException("Could not write the congestion message as json.", e);
        }
    }

    /**
     * Reads a message back, which is what the consumer on the other side does.
     *
     * @throws IllegalArgumentException when the json is unreadable or has no
     *         level in it. There is no sensible default here — assuming level 0
     *         would hand out optimistic travel times to every caller.
     */
    public static CongestionMessage fromJson(String body, ObjectMapper json) {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("The congestion message was not json: " + body, e);
        }
        if (root == null || !root.has("level") || !root.get("level").isNumber()) {
            throw new IllegalArgumentException("The congestion message had no level in it: " + body);
        }

        int level = root.get("level").asInt();
        if (level < LOWEST || level > HIGHEST) {
            throw new IllegalArgumentException(
                    "The congestion message carried a level of " + level + ", which is outside "
                            + LOWEST + " to " + HIGHEST + ".");
        }
        return new CongestionMessage(level);
    }

    /**
     * The word for a level, the same scale the congestion service uses.
     */
    public static String labelFor(int level) {
        if (level <= 1) return "Clear";
        if (level <= 3) return "Light";
        if (level <= 5) return "Moderate";
        if (level == 6) return "Heavy";
        if (level == 7) return "Severe";
        return "Gridlock";
    }

    /**
     * The shape of the json. A record of its own so the field order and the
     * names are decided in one place.
     */
    private record Body(int level, String label) {
    }
}