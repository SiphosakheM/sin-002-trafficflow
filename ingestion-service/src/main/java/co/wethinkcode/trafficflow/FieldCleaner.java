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

    /**
     * Makes an intersection id look the same every time.
     * The old file writes some ids as "int-1002" and some as "INT-1002".
     * We always use the upper case form. A missing id becomes null.
     */
    public static String fixIdCasing(String value) {
        if (isMissing(value)) {
            return null;
        }
        return trimSpaces(value).toUpperCase(Locale.ROOT);
    }

    /**
     * Makes a district name look the same every time.
     * The old file writes "Downtown", "downtown" and "  downtown ".
     * We always use the first letter upper case and the rest lower case.
     * A missing district becomes null.
     */
    public static String fixDistrictCasing(String value) {
        if (isMissing(value)) {
            return null;
        }
        String name = trimSpaces(value).toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Makes a signal type look the same every time.
     * The old file writes "Roundabout", "ROUNDABOUT" and "roundabout" for the
     * same thing, so we always use the lower case form.
     * The word "unknown" is a missing value here, not a real signal type,
     * because the old system used it when it had nothing to fill in.
     */
    public static String fixSignalTypeCasing(String value) {
        if (isMissing(value)) {
            return null;
        }
        return trimSpaces(value).toLowerCase(Locale.ROOT);
    }
}
