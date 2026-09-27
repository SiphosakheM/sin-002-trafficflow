package co.wethinkcode.trafficflow;

import java.util.List;
import java.util.Locale;

/**
 * Small helper that cleans one field of the old csv file.
 *
 * Every method here works on a single value and returns the clean version of it.
 */
public final class FieldCleaner {

    // Words the old system used when it did not have a real value.
    private static final List<String> PLACEHOLDERS =
            List.of("n/a", "tbd", "unknown", "-", "nan");

    private FieldCleaner() {
    }

    /**
     * Tells us if a value is missing.
     * A value is missing when it is null, only spaces, or one of the
     * placeholder words like "N/A" or "TBD".
     */
    public static boolean isMissing(String value) {
        if (value == null) {
            return true;
        }
        String cleaned = value.trim().toLowerCase(Locale.ROOT);
        if (cleaned.isEmpty()) {
            return true;
        }
        return PLACEHOLDERS.contains(cleaned);
    }

    /**
     * Removes the padding around a value.
     * The old file has spaces at the start, at the end, and sometimes two
     * spaces in the middle. We cut the value into words and join the words
     * with one space, so all the extra space goes away.
     */
    public static String trimSpaces(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
