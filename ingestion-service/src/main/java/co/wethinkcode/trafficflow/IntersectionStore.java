package co.wethinkcode.trafficflow;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the clean intersections in memory so we do not read the csv file on
 * every request.
 *
 * The service reads the file once when it starts. If the old csv file changes
 * while the service is running, we need to restart it to see the new rows.
 */
public class IntersectionStore {

    // The records, in the order they came from the file.
    private final Map<String, Intersection> byId = new LinkedHashMap<>();

    /**
     * Makes a store from records we already have.
     */
    public IntersectionStore(List<Intersection> records) {
        for (Intersection record : records) {
            if (record.id() != null) {
                byId.put(record.id(), record);
            }
        }
    }

    /**
     * Reads the csv file and makes a store with the clean records in it.
     */
    public static IntersectionStore loadFromResource(String fileName) throws IOException {
        return new IntersectionStore(IntersectionCleaner.cleanFromResource(fileName));
    }

    /**
     * All the clean records.
     */
    public List<Intersection> all() {
        return List.copyOf(byId.values());
    }

    /**
     * Looks for one intersection by its id.
     * The id is cleaned first, so " int-1001 " finds "INT-1001".
     * We give back an empty Optional when there is no such intersection.
     */
    public Optional<Intersection> findById(String id) {
        String cleanId = FieldCleaner.fixIdCasing(id);
        if (cleanId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(cleanId.toUpperCase(Locale.ROOT)));
    }

    /**
     * All the district names we have, each one only once and in order.
     * Records with no district are not in this list.
     */
    public List<String> districts() {
        return byId.values().stream()
                .map(Intersection::district)
                .filter(district -> district != null && !district.isBlank())
                .distinct()
                .toList();
    }

    /**
     * How many clean records we have.
     */
    public int size() {
        return byId.size();
    }
}