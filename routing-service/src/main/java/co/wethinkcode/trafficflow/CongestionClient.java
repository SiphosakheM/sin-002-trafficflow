package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Talks to the congestion service on port 7022 over http.
 *
 * In stage 3 this polling goes away and the level arrives on the
 * congestion-topic instead. The interface exists for exactly that swap.
 */
public class CongestionClient implements CongestionLookup {

    /** How long we wait before giving up on the congestion service. */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private final String baseUrl;
    private final ObjectMapper json;

    public CongestionClient(String baseUrl, ObjectMapper json) {
        this.baseUrl = baseUrl;
        this.json = json;
    }

    /**
     * Reads the current congestion level.
     *
     * @throws CongestionLookupUnavailable when the service could not be reached
     *         or did not answer in a way we understand.
     */
    @Override
    public CongestionReading read() throws CongestionLookupUnavailable {
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/congestion"))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new CongestionLookupUnavailable(
                    "We could not reach the congestion service at " + baseUrl + ".", e);
        }

        if (response.statusCode() != 200) {
            throw new CongestionLookupUnavailable(
                    "The congestion service answered " + response.statusCode() + " instead of 200.");
        }

        return readLevel(response.body());
    }

    /**
     * Reads the level out of the answer.
     *
     * An answer with no level in it is an unavailability, not a zero. Assuming
     * "no congestion" when we did not hear would quietly hand out optimistic
     * travel times to every caller, which is the worst way for this to fail.
     */
    private CongestionReading readLevel(String body) throws CongestionLookupUnavailable {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            throw new CongestionLookupUnavailable(
                    "The congestion service did not answer with json.", e);
        }
        if (root == null || !root.has("level") || !root.get("level").isNumber()) {
            throw new CongestionLookupUnavailable(
                    "The congestion service answered without a level in it.");
        }

        int level = root.get("level").asInt();
        // Take the word from the service when it sent one, otherwise work it out.
        String label = root.has("label") ? root.get("label").asText() : labelFor(level);
        boolean busy = root.has("busy") ? root.get("busy").asBoolean() : level >= 6;

        return new CongestionReading(level, label, busy);
    }

    /** The word for a level, so we can fill in a missing label. */
    private static String labelFor(int level) {
        return CongestionReading.labelFor(level);
    }

    /**
     * A quick "are you there?" for the readiness answer.
     */
    @Override
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
}