package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the real intersection service on a free port and calls it over http.
 * The ingestion service is faked, so these tests do not need it running.
 */
class IntersectionEndpointsTest {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper json = new ObjectMapper();

    private Javalin app;

    private static IntersectionFeed aFeedWithTwoRecords() {
        return () -> List.of(
                new Intersection("INT-1001", "Downtown", "4-way", true),
                new Intersection("INT-1003", "Downtown", "4-way", false));
    }

    private static IntersectionFeed aBrokenFeed() {
        return () -> {
            throw new IngestionUnavailableException("the ingestion service is down");
        };
    }

    // Starts the service and reads the feed once, like the real start up does.
    private void startWith(IntersectionFeed feed) {
        IntersectionCatalogue catalogue = new IntersectionCatalogue(feed);
        catalogue.refresh();
        app = IntersectionServiceApp.createApp(catalogue).start(0);
    }

    private String url(String path) {
        return "http://localhost:" + app.port() + path;
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url(path))).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url(path))).timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @AfterEach
    void stopTheService() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    @Test
    void healthSaysOk() throws Exception {
        startWith(aFeedWithTwoRecords());

        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    void healthIsOkEvenWhenDegraded() throws Exception {
        startWith(aBrokenFeed());

        assertEquals(200, get("/health").statusCode());
    }

    @Test
    void statusSaysReadyWithTheCount() throws Exception {
        startWith(aFeedWithTwoRecords());

        JsonNode body = json.readTree(get("/status").body());

        assertEquals("READY", body.get("state").asText());
        assertEquals(2, body.get("count").asInt());
    }

    @Test
    void statusSaysDegradedAndWhy() throws Exception {
        startWith(aBrokenFeed());

        HttpResponse<String> response = get("/status");
        JsonNode body = json.readTree(response.body());

        assertEquals("DEGRADED", body.get("state").asText());
        assertEquals(0, body.get("count").asInt());
        assertTrue(body.get("message").asText().contains("down"));
    }

    @Test
    void intersectionsGivesTheRecordsAsJson() throws Exception {
        startWith(aFeedWithTwoRecords());

        HttpResponse<String> response = get("/intersections");
        JsonNode body = json.readTree(response.body());

        assertEquals(200, response.statusCode());
        assertTrue(body.isArray());
        assertEquals(2, body.size());
        assertEquals("INT-1001", body.get(0).get("id").asText());
    }

    @Test
    void intersectionsGives503WhenWeHaveNoDataAtAll() throws Exception {
        startWith(aBrokenFeed());

        HttpResponse<String> response = get("/intersections");

        // 503 and not 200 with an empty list, because "I am broken" is not the
        // same answer as "there are no intersections".
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("down"));
    }

    @Test
    void intersectionsCountGivesTheNumber() throws Exception {
        startWith(aFeedWithTwoRecords());

        JsonNode body = json.readTree(get("/intersections/count").body());

        assertEquals(2, body.get("count").asInt());
    }

    @Test
    void theCountStillAnswersWhenWeAreDegraded() throws Exception {
        startWith(aBrokenFeed());

        JsonNode body = json.readTree(get("/intersections/count").body());

        // 0 is a real answer here, so this is a 200 and not a 503.
        assertEquals(200, get("/intersections/count").statusCode());
        assertEquals(0, body.get("count").asInt());
    }

    @Test
    void oneIntersectionByIdAnswers() throws Exception {
        startWith(aFeedWithTwoRecords());

        HttpResponse<String> response = get("/intersections/INT-1001");
        JsonNode body = json.readTree(response.body());

        assertEquals(200, response.statusCode());
        assertEquals("INT-1001", body.get("id").asText());
        assertEquals("Downtown", body.get("district").asText());
    }

    @Test
    void anUnknownIdGives404() throws Exception {
        startWith(aFeedWithTwoRecords());

        HttpResponse<String> response = get("/intersections/INT-9999");

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("INT-9999"));
    }

    @Test
    void theIdLookupIgnoresCasing() throws Exception {
        startWith(aFeedWithTwoRecords());

        assertEquals(200, get("/intersections/int-1001").statusCode());
    }

    @Test
    void anIntersectionCanBeCheckedWithOneCall() throws Exception {
        startWith(aFeedWithTwoRecords());

        HttpResponse<String> response = get("/intersections/INT-1001/check");
        JsonNode body = json.readTree(response.body());

        assertEquals(200, response.statusCode());
        assertTrue(body.get("known").asBoolean());
        assertTrue(body.get("routable").asBoolean());
    }

    @Test
    void anUnknownIntersectionIsNotKnownAndNotRoutable() throws Exception {
        startWith(aFeedWithTwoRecords());

        JsonNode body = json.readTree(get("/intersections/INT-9999/check").body());

        assertEquals(false, body.get("known").asBoolean());
        assertEquals(false, body.get("routable").asBoolean());
    }

    @Test
    void anIntersectionThatIsSwitchedOffIsKnownButNotRoutable() throws Exception {
        startWith(aFeedWithTwoRecords());

        JsonNode body = json.readTree(get("/intersections/INT-1003/check").body());

        assertTrue(body.get("known").asBoolean());
        assertEquals(false, body.get("routable").asBoolean());
    }

    @Test
    void districtsGivesEachNameOnce() throws Exception {
        startWith(aFeedWithTwoRecords());

        HttpResponse<String> response = get("/districts");
        JsonNode body = json.readTree(response.body());

        assertEquals(200, response.statusCode());
        assertEquals(1, body.size());
        assertEquals("Downtown", body.get(0).asText());
    }

    @Test
    void aDistrictCanBeValidatedByName() throws Exception {
        startWith(aFeedWithTwoRecords());

        HttpResponse<String> response = get("/districts/Downtown");
        JsonNode body = json.readTree(response.body());

        assertEquals(200, response.statusCode());
        assertEquals("Downtown", body.get("district").asText());
        assertEquals(2, body.get("intersectionCount").asInt());
    }

    @Test
    void theDistrictLookupIgnoresCasing() throws Exception {
        startWith(aFeedWithTwoRecords());

        assertEquals(200, get("/districts/downtown").statusCode());
    }

    @Test
    void anUnknownDistrictGives404() throws Exception {
        startWith(aFeedWithTwoRecords());

        HttpResponse<String> response = get("/districts/N%20suburb");

        assertEquals(404, response.statusCode());
    }

    @Test
    void reloadTriesTheFeedAgain() throws Exception {
        AtomicBoolean feedIsUp = new AtomicBoolean(false);
        IntersectionFeed flaky = () -> {
            if (!feedIsUp.get()) {
                throw new IngestionUnavailableException("the ingestion service is down");
            }
            return List.of(new Intersection("INT-1001", "Downtown", "4-way", true));
        };
        startWith(flaky);
        assertEquals(503, get("/intersections").statusCode());

        feedIsUp.set(true);
        HttpResponse<String> response = post("/reload");

        assertEquals(200, response.statusCode());
        assertEquals(200, get("/intersections").statusCode());
    }

    @Test
    void reloadGives503WhenTheFeedIsStillDown() throws Exception {
        startWith(aBrokenFeed());

        assertEquals(503, post("/reload").statusCode());
    }

    @Test
    void everyJsonAnswerSaysItIsJson() throws Exception {
        startWith(aFeedWithTwoRecords());

        for (String path : List.of("/intersections", "/intersections/count", "/districts", "/status")) {
            String contentType = get(path).headers().firstValue("Content-Type").orElse("");
            assertTrue(contentType.contains("json"), path + " should answer with json");
        }
    }
}