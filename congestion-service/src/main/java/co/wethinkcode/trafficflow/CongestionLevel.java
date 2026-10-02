package co.wethinkcode.trafficflow;

/**
 * The city-wide congestion level, a whole number from 0 to 8.
 *
 * 0 means the roads are clear and 8 means they are gridlocked. Nothing outside
 * that range is allowed in, so a bad number cannot travel further into the
 * system.
 */
public record CongestionLevel(int value) implements Comparable<CongestionLevel> {

    public static final int LOWEST = 0;
    public static final int HIGHEST = 8;

    // From 6 upwards we call the roads busy.
    private static final int BUSY_FROM = 6;

    // The words we use for each level, so logs and answers are easy to read.
    // Level 6 is where we start calling the roads busy, so 6 says "Heavy".
    private static final String[] LABELS = {
            "Clear", "Clear", "Light", "Light", "Moderate", "Moderate", "Heavy", "Severe", "Gridlock"
    };

    public CongestionLevel {
        if (value < LOWEST || value > HIGHEST) {
            throw new IllegalArgumentException(
                    "The congestion level must be between " + LOWEST + " and " + HIGHEST + ", but was " + value);
        }
    }

    /**
     * Makes a level from a number, refusing anything outside 0 to 8.
     */
    public static CongestionLevel of(int value) {
        return new CongestionLevel(value);
    }

    /**
     * Makes a level from text, like the body of a request.
     */
    public static CongestionLevel parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("No congestion level was given");
        }
        try {
            return new CongestionLevel(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + text.trim() + "\" is not a whole number");
        }
    }

    /**
     * The level one step higher. Stepping up from 8 stays at 8.
     */
    public CongestionLevel up() {
        return new CongestionLevel(Math.min(value + 1, HIGHEST));
    }

    /**
     * The level one step lower. Stepping down from 0 stays at 0.
     */
    public CongestionLevel down() {
        return new CongestionLevel(Math.max(value - 1, LOWEST));
    }

    /**
     * Is traffic busy? Used to warn about longer travel times.
     */
    public boolean isBusy() {
        return value >= BUSY_FROM;
    }

    /**
     * Are the roads clear?
     */
    public boolean isFree() {
        return value <= 2;
    }

    /**
     * A word for this level, for logs and for the /congestion answer.
     */
    public String label() {
        return LABELS[value];
    }

    @Override
    public int compareTo(CongestionLevel other) {
        return Integer.compare(value, other.value());
    }
}