package co.wethinkcode.trafficflow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the congestion service told us about the city right now.
 *
 * The level is the number 0 to 8. The word and the busy flag are the same
 * thing said twice, and they are here so answers and logs can be read without
 * a lookup table.
 */
public record CongestionReading(int level, String label, boolean busy) {

    /** Level 0 means the roads are empty. */
    public static final int LOWEST = 0;

    /** Level 8 means gridlock. */
    public static final int HIGHEST = 8;

    /**
     * Puts the reading back into a map, in the order we like to read it.
     */
    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("level", level);
        map.put("label", label);
        map.put("busy", busy);
        return map;
    }

    /**
     * The word for a level, worked out here if the service did not send one.
     *
     * Same scale the congestion service uses, so the two always agree.
     */
    public static String labelFor(int level) {
        if (level <= 1) return "Clear";
        if (level <= 3) return "Light";
        if (level <= 5) return "Moderate";
        if (level == 6) return "Heavy";
        if (level == 7) return "Severe";
        return "Gridlock";
    }
}