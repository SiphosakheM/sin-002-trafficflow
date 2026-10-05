package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real routing service over real http.
 *
 * The two lookups are fakes here so the tests do not need the other services
 * running. They are put in through the same interface the real clients use, so
 * what is tested here is the service's own logic and its answers.
 */
class RoutingServiceAppTest {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    /** Stands in for the intersection service on port 7021. */
    private FakeIntersectionLookup intersections = new FakeIntersectionLookup();

    /** Stands in for the congestion service on port 7022. */
    private FakeCongestionLookup congestion = new FakeCongestionLookup();

    private Javalin app;

    @BeforeEach
    void startTheService() {
        intersections = new FakeIntersectionLookup();
        congestion = new FakeCongestionLookup();
        app = RoutingServiceApp.createApp(intersections, congestion, new ObjectMapper()).start(0);
    }

    @AfterEach
    void stopTheService() {
        app.stop();
    }

    /** A short GET that returns the whole response, so we can see the status. */
    private HttpResponse<String> get(String path) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .GET());
    }

    /** A POST with a json body. */
    private HttpResponse<String> post(String path, String body) throws Exception {
        return send(HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.timeout(Duration.ofSeconds(10)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String url(String path) {
        return "http://localhost:" + app.port() + path;
    }

    @Test
    void healthIsOk() throws Exception {
        HttpResponse<String> response = get("/health");

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    void aGoodRouteComesBackWithAnEstimate() throws Exception {
        HttpResponse<String> response = post("/route", """
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """);

        assertEquals(200, response.statusCode());
        JsonNode body = json.readTree(response.body());
        assertEquals("INT-1001", body.get("from").asText());

        // INT-1001 is a 4-way (20s) and INT-1005 is a roundabout (6s), so
        // 10 km at 40 km/h is 15 minutes plus 26 seconds, which rounds to 15.
        assertEquals(15, body.get("minutes").asInt());
        assertEquals(15, body.get("freeFlowMinutes").asInt());
        assertEquals(26, body.get("intersectionDelaySeconds").asInt());
    }

    @Test
    void theEstimateFollowsTheCongestionLevel() throws Exception {
        congestion.level = 0;
        int clear = json.readTree(post("/route", """
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """).body()).get("minutes").asInt();

        congestion.level = 8;
        int gridlock = json.readTree(post("/route", """
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """).body()).get("minutes").asInt();

        assertTrue(gridlock > clear, "gridlock must be slower than clear roads");
    }

    @Test
    void theEstimateShowsWhichLevelItUsed() throws Exception {
        congestion.level = 6;

        JsonNode body = json.readTree(post("/route", """
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """).body());

        assertEquals(6, body.get("congestionLevel").asInt());
        assertEquals("Heavy", body.get("congestionLabel").asText());
    }

    @Test
    void anUnknownIntersectionIsFourOhFour() throws Exception {
        HttpResponse<String> response = post("/route", """
                {"from":"INT-9999","to":"INT-1005","distanceKm":10}
                """);

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("INT-9999"));
    }

    @Test
    void anIntersectionThatIsSwitchedOffIsFourTwentyTwo() throws Exception {
        HttpResponse<String> response = post("/route", """
                {"from":"INT-1009","to":"INT-1005","distanceKm":10}
                """);

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("INT-1009"));
    }

    @Test
    void aRubbishBodyIsFourHundred() throws Exception {
        assertEquals(400, post("/route", "not json at all").statusCode());
    }

    @Test
    void aMissingPieceIsFourHundred() throws Exception {
        assertEquals(400, post("/route", """
                {"from":"INT-1001"}
                """).statusCode());
    }

    @Test
    void theIntersectionServiceBeingDownIsFiveOhThree() throws Exception {
        intersections.down = true;

        HttpResponse<String> response = post("/route", """
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """);

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("intersection service"));
    }

    @Test
    void theCongestionServiceBeingDownIsFiveOhThree() throws Exception {
        congestion.down = true;

        HttpResponse<String> response = post("/route", """
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """);

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("congestion service"));
    }

    @Test
    void anUnknownRoutePathIsFourOhFour() throws Exception {
        assertEquals(404, get("/nope").statusCode());
    }

    @Test
    void gettingARouteInsteadOfPostingIsRefused() throws Exception {
        // /route only answers POST. Javalin 5 has no 405 setting, so an
        // unmatched path and method both come back as 404.
        assertEquals(404, get("/route").statusCode());
    }

    @Test
    void statusSaysWhetherTheOtherServicesAreThere() throws Exception {
        JsonNode ready = json.readTree(get("/status").body());
        assertEquals("READY", ready.get("state").asText());
        assertTrue(ready.get("intersectionService").asBoolean());
        assertTrue(ready.get("congestionService").asBoolean());

        congestion.down = true;
        JsonNode degraded = json.readTree(get("/status").body());
        assertEquals("DEGRADED", degraded.get("state").asText());
        assertFalse(degraded.get("congestionService").asBoolean());
    }

    @Test
    void healthIsStillOkWhenTheOtherServicesAreDown() throws Exception {
        intersections.down = true;
        congestion.down = true;

        // Same rule as the other services: liveness is about this process only.
        assertEquals(200, get("/health").statusCode());
    }

    @Test
    void theEstimateIsJson() throws Exception {
        HttpResponse<String> response = post("/route", """
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """);

        assertTrue(response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    }

    @Test
    void oneRouteDoesNotAffectTheNext() throws Exception {
        post("/route", """
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """);

        JsonNode body = json.readTree(post("/route", """
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """).body());

        // Same as a fresh route, so nothing was left behind by the first one.
        assertEquals(15, body.get("minutes").asInt());
    }
}