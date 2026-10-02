package co.wethinkcode.trafficflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The list of intersections this service knows about.
 *
 * This service is the source of truth for intersection and district names, so
 * everything else asks this class. The records come from the ingestion service,
 * which cleans the old csv file.
 *
 * The ids are put in a map so a lookup is quick, and the lookup is careful
 * about casing because callers do not always send "INT-1001" the same way.
 */
public class IntersectionRegistry {

    private final Map<String, Intersection> byId = new LinkedHashMap<>();

    public IntersectionRegistry(List<Intersection> records) {
        for (Intersection record : records) {
            if (record.id() != null) {
                byId.put(cleanId(record.id()), record);
            }
        }
    }

    /**
     * Finds one intersection by its id.
     * The id is cleaned first, so " int-1001 " finds "INT-1001".
     */
    public Optional<Intersection> find(String id) {
        String clean = cleanId(id);
        if (clean == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byId.get(clean));
    }

    /**
     * Do we know this intersection at all?
     */
    public boolean isKnown(String id) {
        return find(id).isPresent();
    }

    /**
     * Can this intersection be used as the start or end of a route?
     *
     * A record that is switched off in the old file cannot be used. When the
     * active flag is missing we allow it, because we cannot say it is off and
     * refusing every unknown flag would block routes on a guess.
     */
    public boolean isRoutable(String id) {
        return find(id)
                .map(record -> record.active() == null || record.active())
                .orElse(false);
    }

    /**
     * Every district we know, each one only once, in the order they came.
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
     * Is this a district name we recognise? The name is cleaned first, so
     * " downtown " matches "Downtown".
     */
    public boolean isKnownDistrict(String name) {
        String clean = cleanDistrict(name);
        return clean != null && districts().contains(clean);
    }

    /**
     * All the intersections in one district.
     */
    public List<Intersection> inDistrict(String name) {
        String clean = cleanDistrict(name);
        if (clean == null) {
            return List.of();
        }
        return byId.values().stream()
                .filter(record -> clean.equals(record.district()))
                .toList();
    }

    /**
     * Every record we hold.
     */
    public List<Intersection> all() {
        return List.copyOf(byId.values());
    }

    /**
     * How many records we hold.
     */
    public int size() {
        return byId.size();
    }

    // Puts an id into the one form we store: trimmed and upper case.
    private static String cleanId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    // Puts a district name into the one form we compare against.
    private static String cleanDistrict(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            return null;
        }
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1).toLowerCase(Locale.ROOT);
    }
}