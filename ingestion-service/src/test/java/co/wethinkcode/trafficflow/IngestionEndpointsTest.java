package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the real service on a free port and calls it over http.
 * That way we check the endpoint the way another service will use it.
 */
class IngestionEndpointsTest {

    private static Javalin app;
    private static String baseUrl;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    static void startTheServer() {
        // Port 0 means the computer picks a free port for us.
        app = IngestionServiceApp.createApp().start(0);
        baseUrl = "http://localhost:" + app.port();
    }

    @AfterAll
    static void stopTheServer() {
        app.stop();
    }

    private static HttpResponse<String> call(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthSaysOk() throws Exception {
        HttpResponse<String> response = call("/health");

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    void intersectionsAnswersWithTheCleanRecords() throws Exception {
        HttpResponse<String> response = call("/intersections");

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("json"),
                "the answer should be json");
    }

    @Test
    void intersectionsGivesAll17Records() throws Exception {
        HttpResponse<String> response = call("/intersections");

        JsonNode body = json.readTree(response.body());

        assertTrue(body.isArray());
        assertEquals(17, body.size());
    }

    @Test
    void theFirstRecordHasTheFieldsWePromised() throws Exception {
        HttpResponse<String> response = call("/intersections");

        JsonNode first = json.readTree(response.body()).get(0);

        assertEquals("INT-1001", first.get("id").asText());
        assertEquals("Downtown", first.get("district").asText());
        assertEquals("4-way", first.get("signalType").asText());
        assertTrue(first.get("active").asBoolean());
    }

    @Test
    void theMissingValuesComeBackAsJsonNull() throws Exception {
        HttpResponse<String> response = call("/intersections");

        JsonNode body = json.readTree(response.body());

        JsonNode noDistrict = findById(body, "INT-1015");
        assertTrue(noDistrict.get("district").isNull());

        JsonNode noSignalType = findById(body, "INT-1007");
        assertTrue(noSignalType.get("signalType").isNull());
    }

    @Test
    void oneIntersectionByIdAnswersWithThatRecord() throws Exception {
        HttpResponse<String> response = call("/intersections/INT-1005");

        assertEquals(200, response.statusCode());
        JsonNode body = json.readTree(response.body());
        assertEquals("INT-1005", body.get("id").asText());
        assertEquals("roundabout", body.get("signalType").asText());
    }

    @Test
    void anUnknownIdGives404() throws Exception {
        HttpResponse<String> response = call("/intersections/INT-9999");

        assertEquals(404, response.statusCode());
    }

    @Test
    void theDistrictsEndpointListsEveryDistrict() throws Exception {
        HttpResponse<String> response = call("/districts");

        assertEquals(200, response.statusCode());
        JsonNode body = json.readTree(response.body());
        assertEquals(5, body.size());
        assertTrue(body.toString().contains("Downtown"));
        assertTrue(body.toString().contains("Westside"));
    }

    @Test
    void theIntersectionsCountIsSeventeen() throws Exception {
        HttpResponse<String> response = call("/intersections/count");

        assertEquals(200, response.statusCode());
        assertEquals("17", response.body().trim());
    }

    // Small helper to pull one record out of the json array by its id.
    private static JsonNode findById(JsonNode body, String id) {
        for (JsonNode record : body) {
            if (id.equals(record.get("id").asText())) {
                return record;
            }
        }
        throw new AssertionError("no record with id " + id);
    }
}