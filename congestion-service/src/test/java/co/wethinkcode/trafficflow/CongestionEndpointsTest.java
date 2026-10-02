package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CongestionEndpointsTest {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper json = new ObjectMapper();

    private CongestionTracker tracker;
    private Javalin app;

    @BeforeEach
    void startTheService() {
        tracker = new CongestionTracker(CongestionLevel.of(3));
        app = CongestionServiceApp.createApp(tracker).start(0);
    }

    @AfterEach
    void stopTheService() {
        if (app != null) {
            app.stop();
            app = null;
        }
    }

    private String url(String path) {
        return "http://localhost:" + app.port() + path;
    }

    private HttpResponse<String> send(String path, String body) throws IOException, InterruptedException {
        HttpRequest.BodyPublisher payload = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(payload)
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url(path))).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthSaysOk() throws Exception {
        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    void congestionGivesTheLevelAsJson() throws Exception {
        HttpResponse<String> response = get("/congestion");
        JsonNode body = json.readTree(response.body());

        assertEquals(200, response.statusCode());
        assertEquals(3, body.get("level").asInt());
    }

    @Test
    void theCongestionAnswerAlsoHasAWordForTheLevel() throws Exception {
        // Level 3 is "Light" on our scale.
        JsonNode body = json.readTree(get("/congestion").body());

        assertEquals("Light", body.get("label").asText());
    }

    @Test
    void theAnswerSaysIfTrafficIsBusy() throws Exception {
        tracker.set(CongestionLevel.of(7));

        JsonNode body = json.readTree(get("/congestion").body());

        assertTrue(body.get("busy").asBoolean());
    }

    @Test
    void postingALevelChangesIt() throws Exception {
        HttpResponse<String> response = send("/congestion", "{\"level\":6}");

        assertEquals(200, response.statusCode());
        assertEquals(6, tracker.currentLevel());
    }

    @Test
    void afterPostingTheNewLevelIsWhatWeReadBack() throws Exception {
        send("/congestion", "{\"level\":2}");

        assertEquals(2, json.readTree(get("/congestion").body()).get("level").asInt());
    }

    @Test
    void aLevelAboveEightGives400() throws Exception {
        HttpResponse<String> response = send("/congestion", "{\"level\":9}");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("0"));
        assertTrue(response.body().contains("8"));
    }

    @Test
    void aNegativeLevelGives400() throws Exception {
        assertEquals(400, send("/congestion", "{\"level\":-1}").statusCode());
    }

    @Test
    void aRefusedLevelDoesNotChangeTheOldOne() throws Exception {
        send("/congestion", "{\"level\":9}");

        assertEquals(3, tracker.currentLevel());
    }

    @Test
    void aLevelThatIsNotANumberGives400() throws Exception {
        HttpResponse<String> response = send("/congestion", "{\"level\":\"busy\"}");

        assertEquals(400, response.statusCode());
    }

    @Test
    void anEmptyBodyGives400() throws Exception {
        assertEquals(400, send("/congestion", null).statusCode());
    }

    @Test
    void aBodyWithNoLevelGives400() throws Exception {
        assertEquals(400, send("/congestion", "{}").statusCode());
    }

    @Test
    void aBodyThatIsNotJsonGives400() throws Exception {
        assertEquals(400, send("/congestion", "not json at all").statusCode());
    }

    @Test
    void aFractionalLevelGives400() throws Exception {
        assertEquals(400, send("/congestion", "{\"level\":3.5}").statusCode());
    }

    @Test
    void steppingUpMovesTheLevelOneHigher() throws Exception {
        send("/congestion/step", "{\"direction\":\"up\"}");

        assertEquals(4, tracker.currentLevel());
    }

    @Test
    void steppingDownMovesTheLevelOneLower() throws Exception {
        send("/congestion/step", "{\"direction\":\"down\"}");

        assertEquals(2, tracker.currentLevel());
    }

    @Test
    void aStepWithNoDirectionGives400() throws Exception {
        assertEquals(400, send("/congestion/step", "{}").statusCode());
    }

    @Test
    void aStepWithRubbishDirectionGives400() throws Exception {
        assertEquals(400, send("/congestion/step", "{\"direction\":\"sideways\"}").statusCode());
    }

    @Test
    void steppingUpAtTheTopStaysAtTheTopAndIsStill200() throws Exception {
        tracker.set(CongestionLevel.of(8));

        HttpResponse<String> response = send("/congestion/step", "{\"direction\":\"up\"}");

        assertEquals(200, response.statusCode());
        assertEquals(8, tracker.currentLevel());
    }

    @Test
    void theHistoryIsNewestFirst() throws Exception {
        send("/congestion", "{\"level\":4}");
        send("/congestion", "{\"level\":5}");

        JsonNode body = json.readTree(get("/congestion/history").body());

        assertEquals(5, body.get(0).get("level").asInt());
        assertEquals(4, body.get(1).get("level").asInt());
    }

    @Test
    void theHistorySaysWhyEachChangeHappened() throws Exception {
        send("/congestion/step", "{\"direction\":\"up\"}");

        JsonNode body = json.readTree(get("/congestion/history").body());

        assertEquals("stepped up", body.get(0).get("reason").asText());
    }

    @Test
    void theHistoryLimitCanBeChangedOverHttp() throws Exception {
        // Make some changes first, so there is more than one thing to drop.
        send("/congestion", "{\"level\":4}");
        send("/congestion", "{\"level\":5}");

        send("/congestion/history/limit", "{\"limit\":2}");

        assertEquals(2, tracker.history().size());
        assertEquals(5, tracker.history().get(0).level().value());
    }

    @Test
    void aNegativeHistoryLimitGives400() throws Exception {
        assertEquals(400, send("/congestion/history/limit", "{\"limit\":-5}").statusCode());
    }

    @Test
    void everyJsonAnswerSaysItIsJson() throws Exception {
        for (String path : List.of("/congestion", "/congestion/history")) {
            String contentType = get(path).headers().firstValue("Content-Type").orElse("");
            assertTrue(contentType.contains("json"), path + " should answer with json");
        }
    }
}