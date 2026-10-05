package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A route the caller wants an estimate for: go from one intersection to
 * another, and this is how far apart they are.
 *
 * We check the whole thing here, before we call the other services, so a silly
 * request never costs two http calls.
 */
public record RouteRequest(String from, String to, double distanceKm) {

    /**
     * The example we put in every refusal, so the caller can see what we wanted.
     */
    private static final String EXAMPLE = """
            Send a body like {"from": "INT-1001", "to": "INT-1005", "distanceKm": 4.2}""";

    /**
     * A route can be at most this far, to catch a fat finger on the keyboard.
     */
    private static final double LONGEST_ROUTE_KM = 500;

    /**
     * Reads a route out of the request body.
     *
     * Ids are trimmed and turned upper case, because the intersection service
     * stores them upper case and we do not want a miss over case.
     *
     * @throws BadRouteRequestException when the body is not json, or is missing
     *         something, or the distance makes no sense.
     */
    public static RouteRequest fromJson(String body, ObjectMapper json) throws BadRouteRequestException {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            throw new BadRouteRequestException(
                    "That body was not json we could read. " + EXAMPLE);
        }
        if (root == null || !root.isObject()) {
            throw new BadRouteRequestException(
                    "That body was not a json object. " + EXAMPLE);
        }

        String from = readId(root, "from");
        String to = readId(root, "to");
        double distanceKm = readDistance(root);

        if (from.equals(to)) {
            throw new BadRouteRequestException(
                    "The from and to are the same intersection, so there is no route. "
                            + "They have to be different.");
        }

        return new RouteRequest(from, to, distanceKm);
    }

    /**
     * Reads one id and tidies it up. A missing or blank one is a refusal.
     */
    private static String readId(JsonNode root, String field) throws BadRouteRequestException {
        JsonNode value = root.get(field);
        if (value == null || value.isNull() || value.asText().trim().isEmpty()) {
            throw new BadRouteRequestException(
                    "The body needs a " + field + " intersection id. " + EXAMPLE);
        }
        return value.asText().trim().toUpperCase();
    }

    /**
     * Reads the distance. It has to be a real number and it has to be worth
     * travelling, otherwise the estimate would be nonsense.
     */
    private static double readDistance(JsonNode root) throws BadRouteRequestException {
        JsonNode value = root.get("distanceKm");
        if (value == null || value.isNull()) {
            throw new BadRouteRequestException(
                    "The body needs a distanceKm in kilometres. " + EXAMPLE);
        }
        if (!value.isNumber()) {
            throw new BadRouteRequestException(
                    "The distanceKm has to be a number of kilometres. " + EXAMPLE);
        }

        double distanceKm = value.asDouble();
        if (distanceKm <= 0) {
            throw new BadRouteRequestException(
                    "The distanceKm has to be a positive number of kilometres.");
        }
        if (distanceKm > LONGEST_ROUTE_KM) {
            throw new BadRouteRequestException(
                    "That route of " + distanceKm + " km is longer than the "
                            + LONGEST_ROUTE_KM + " km we are willing to estimate.");
        }
        return distanceKm;
    }

    /**
     * How many intersections the car has to pass on the way. That is the start
     * and the end, so a normal route has two.
     */
    public int intersectionCount() {
        return 2;
    }
}