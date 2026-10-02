package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Reads the clean intersections from the ingestion service over http.
 *
 * The ingestion service is the one that cleans the old csv file, so we ask it
 * for the records instead of reading the csv ourselves.
 */
public class IngestionClient implements IntersectionFeed {

    // How long we wait for the ingestion service before we give up.
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final ObjectMapper json = new ObjectMapper();

    public IngestionClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Asks the ingestion service for all the clean intersections.
     *
     * We throw IngestionUnavailableException when the service is down, answers
     * with an error, or sends back something we do not understand. That way the
     * caller never has to guess why it has no data.
     */
    public List<Intersection> fetchAll() throws IngestionUnavailableException {
        String url = baseUrl + "/intersections";

        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            throw new IngestionUnavailableException(
                    "Could not reach the ingestion service at " + url + ": " + e.getMessage(), e);
        }

        if (response.statusCode() != 200) {
            throw new IngestionUnavailableException(
                    "The ingestion service at " + url + " answered with " + response.statusCode());
        }

        try {
            return json.readValue(response.body(), new TypeReference<List<Intersection>>() {
            });
        } catch (IOException e) {
            throw new IngestionUnavailableException(
                    "The ingestion service at " + url + " sent back json we cannot read", e);
        }
    }
}