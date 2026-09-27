package co.wethinkcode.trafficflow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the rows in the old csv file that talk about the same intersection.
 *
 * The old file wrote "INT-1005" and "int-1005" as two separate rows, so we
 * have to join them back into one.
 */
public final class Dedupe {

    private Dedupe() {
    }

    /**
     * Removes the repeated rows and keeps the first one of each id.
     * A row with no id stays as it is, because without an id we cannot tell
     * if two rows are the same place or not.
     */
    public static List<Intersection> removeDuplicates(List<Intersection> rows) {
        List<Intersection> unique = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (Intersection row : rows) {
            if (row.id() == null) {
                unique.add(row);
                continue;
            }
            if (seenIds.add(row.id())) {
                unique.add(row);
            }
        }
        return unique;
    }
}
