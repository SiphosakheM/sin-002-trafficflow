package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Talks to the intersection service on port 7021 over http.
 *
 * We ask "do you know this intersection, and can I route through it?" rather
 * than asking for the whole record, because that is the only question routing
 * actually has.
 */
public class IntersectionClient implements IntersectionLookup {

    /** How long we wait before giving up on the intersection service. */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private final String baseUrl;
    private final ObjectMapper json;

    public IntersectionClient(String baseUrl, ObjectMapper json) {
        this.baseUrl = baseUrl;
        this.json = json;
    }

    /**
     * Asks the intersection service about one intersection.
     *
     * An id we have never heard of is not an error — the service answers 200
     * with known=false — so that comes back as a plain answer. Only a service
     * we cannot reach, or an answer we cannot read, is an unavailability.
     */
    @Override
    public IntersectionCheck check(String id) throws IntersectionLookupUnavailable {
        String upper = id.trim().toUpperCase();
        String url = baseUrl + "/intersections/" + encode(upper) + "/check";

        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new IntersectionLookupUnavailable(
                    "We could not reach the intersection service at " + baseUrl + ".", e);
        }

        if (response.statusCode() != 200) {
            throw new IntersectionLookupUnavailable(
                    "The intersection service answered " + response.statusCode()
                            + " instead of 200 for " + upper + ".");
        }

        return readCheck(upper, response.body());
    }

    /**
     * Reads the known/routable pair out of the answer.
     *
     * If the answer is not the json we expect we treat the whole thing as an
     * unavailability rather than guessing. Guessing would mean answering "I do
     * not know this intersection" for an intersection that is perfectly real,
     * and that would send a 404 to a caller who did nothing wrong.
     */
    private IntersectionCheck readCheck(String id, String body) throws IntersectionLookupUnavailable {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            throw new IntersectionLookupUnavailable(
                    "The intersection service did not answer with json for " + id + ".", e);
        }
        if (root == null || !root.has("known") || !root.has("routable")) {
            throw new IntersectionLookupUnavailable(
                    "The intersection service answered without known/routable for " + id + ".");
        }

        return new IntersectionCheck(id, root.get("known").asBoolean(), root.get("routable").asBoolean());
    }

    /**
     * A quick "are you there?" for the readiness answer.
     */
    public boolean isReachable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Sends an id in the right percent-encoding, so an id with an odd character
     * in it cannot break the url.
     */
    private static String encode(String id) {
        return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }
}