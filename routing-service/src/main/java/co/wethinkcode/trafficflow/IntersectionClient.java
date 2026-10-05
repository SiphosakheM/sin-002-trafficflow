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
 * We ask for the one intersection rather than the whole list, because that is
 * all a route needs, and the list changes behind our back when ingestion is
 * reloaded.
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
     * Looks up one intersection.
     *
     * An id we have never heard of is not an error. The service answers 404
     * for that, which is a normal answer meaning "no such intersection", so it
     * comes back as a plain result. Only a service we cannot reach, or an
     * answer we cannot read, counts as unavailability — otherwise a caller
     * would get a 503 for a perfectly ordinary unknown id.
     */
    @Override
    public IntersectionCheck check(String id) throws IntersectionLookupUnavailable {
        String upper = id.trim().toUpperCase();
        String url = baseUrl + "/intersections/" + encode(upper);

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

        // 404 is a real answer: the city has never heard of this intersection.
        if (response.statusCode() == 404) {
            return IntersectionCheck.unknown(upper);
        }
        if (response.statusCode() == 503) {
            throw new IntersectionLookupUnavailable(
                    "The intersection service is degraded, so it cannot confirm " + upper + ".");
        }
        if (response.statusCode() != 200) {
            throw new IntersectionLookupUnavailable(
                    "The intersection service answered " + response.statusCode()
                            + " instead of 200 for " + upper + ".");
        }

        return readRecord(upper, response.body());
    }

    /**
     * Reads one record out of the answer.
     *
     * If the answer is not the json we expect we treat the whole thing as an
     * unavailability rather than guessing. Guessing would mean answering "I do
     * not know this intersection" for an intersection that is perfectly real,
     * and that would send a 404 to a caller who did nothing wrong.
     */
    private IntersectionCheck readRecord(String id, String body) throws IntersectionLookupUnavailable {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            throw new IntersectionLookupUnavailable(
                    "The intersection service did not answer with json for " + id + ".", e);
        }
        if (root == null || !root.isObject()) {
            throw new IntersectionLookupUnavailable(
                    "The intersection service answered with something that is not a record for " + id + ".");
        }

        // An active flag of null means the data does not say. We assume the
        // road is open, same as the intersection service does, because closing
        // a road we have no evidence about would be the worse mistake.
        boolean active = !root.hasNonNull("active") || root.get("active").asBoolean();
        String signalType = root.hasNonNull("signalType") ? root.get("signalType").asText() : null;

        return new IntersectionCheck(id, true, active, signalType);
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