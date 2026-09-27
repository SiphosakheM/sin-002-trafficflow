package co.wethinkcode.trafficflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts all the cleaning steps together.
 *
 * It takes the messy rows from the csv file and gives back clean
 * Intersection records that the other services can use.
 */
public final class IntersectionCleaner {

    private static final Logger log = LoggerFactory.getLogger(IntersectionCleaner.class);

    // The columns of the old csv file, in the order they appear.
    private static final int COLUMN_ID = 0;
    private static final int COLUMN_DISTRICT = 1;
    private static final int COLUMN_SIGNAL_TYPE = 2;
    private static final int COLUMN_ACTIVE_FLAG = 3;
    private static final int COLUMN_COUNT = 4;

    private IntersectionCleaner() {
    }

    /**
     * Reads a csv file from the project and gives back the clean records.
     */
    public static List<Intersection> cleanFromResource(String fileName) throws IOException {
        return clean(CsvReader.readFromResource(fileName));
    }

    /**
     * Cleans the raw rows of the csv file.
     * Every row goes through the field cleaner, and after that the repeated
     * rows are joined into one.
     */
    public static List<Intersection> clean(List<String[]> rawRows) {
        List<Intersection> cleaned = new ArrayList<>();

        for (String[] row : rawRows) {
            Intersection intersection = toIntersection(row);
            if (intersection != null) {
                cleaned.add(intersection);
            }
        }
        return Dedupe.removeDuplicates(cleaned);
    }

    /**
     * Turns one raw csv row into one clean record.
     * We send back null when the row does not have all the columns, because a
     * broken row cannot be fixed and we do not want to make a guess.
     */
    private static Intersection toIntersection(String[] row) {
        if (row == null || row.length < COLUMN_COUNT) {
            log.warn("Skipping a row, it does not have all {} columns: {}", COLUMN_COUNT, String.join("|", row));
            return null;
        }
        return new Intersection(
                FieldCleaner.fixIdCasing(row[COLUMN_ID]),
                FieldCleaner.fixDistrictCasing(row[COLUMN_DISTRICT]),
                FieldCleaner.fixSignalTypeCasing(row[COLUMN_SIGNAL_TYPE]),
                FieldCleaner.parseActiveFlag(row[COLUMN_ACTIVE_FLAG]));
    }
}
