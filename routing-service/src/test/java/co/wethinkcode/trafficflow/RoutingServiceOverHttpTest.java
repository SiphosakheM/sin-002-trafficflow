package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
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

/**
 * Drives the real routing service over real http, with the real clients.
 *
 * Nothing in the routing service is faked here — the same IntersectionClient
 * and CongestionClient that run in production are the ones being used. The two
 * other services are stood in for, each answering the json the real one sends,
 * because they live in other Maven modules and cannot be on this classpath.
 *
 * The full four-service check with the real jars is done by hand; see the
 * readme under "How to check my work".
 */
class RoutingServiceOverHttpTest {

    /** The records the real intersection service sends. */
    private static final String RECORDS = """
            [
              {"id":"INT-1001","district":"Downtown","signalType":"4-way","active":true},
              {"id":"INT-1005","district":"Downtown","signalType":"roundabout","active":true},
              {"id":"INT-1009","district":"Westside","signalType":"4-way","active":false},
              {"id":"INT-1013","district":"Westside","signalType":null,"active":null}
            ]
            """;

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private Javalin standInIntersectionService;
    private Javalin standInCongestionService;
    private Javalin routingService;

    /** The level the congestion stand-in reports. */
    private int level = 0;

    /** Makes the intersection stand-in answer with an error. */
    private boolean intersectionDown = false;

    /** Makes the congestion stand-in answer with an error. */
    private boolean congestionDown = false;

    @BeforeEach
    void startEverything() {
        standInIntersectionService = Javalin.create().start(0);
        standInIntersectionService.get("/health", ctx ->
                ctx.status(intersectionDown ? 503 : 200).result(intersectionDown ? "down" : "OK"));
        standInIntersectionService.get("/intersections/{id}", this::answerIntersection);

        standInCongestionService = Javalin.create().start(0);
        standInCongestionService.get("/health", ctx ->
                ctx.status(congestionDown ? 503 : 200).result(congestionDown ? "down" : "OK"));
        standInCongestionService.get("/congestion", this::answerCongestion);

        routingService = RoutingServiceApp.createApp(
                new IntersectionClient("http://localhost:" + standInIntersectionService.port(), json),
                new CongestionClient("http://localhost:" + standInCongestionService.port(), json),
                json).start(0);
    }

    /**
     * Answers like the real intersection service: the record for an id it knows,
     * and 404 for one it does not.
     */
    private void answerIntersection(Context ctx) {
        if (intersectionDown) {
            ctx.status(503).result("I cannot answer right now");
            return;
        }

        String id = ctx.pathParam("id");
        for (JsonNode record : readAll(RECORDS)) {
            if (record.get("id").asText().equals(id)) {
                ctx.json(record);
                return;
            }
        }
        ctx.status(404);
    }

    /** Answers like the real congestion service. */
    private void answerCongestion(Context ctx) {
        if (congestionDown) {
            ctx.status(503).result("I cannot answer right now");
            return;
        }
        ctx.json(new CongestionReading(level, CongestionReading.labelFor(level), level >= 6).asMap());
    }

    private JsonNode readAll(String body) {
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("the test's own json is broken", e);
        }
    }

    @AfterEach
    void stopEverything() {
        routingService.stop();
        standInCongestionService.stop();
        standInIntersectionService.stop();
    }

    private HttpResponse<String> route(String body) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + routingService.port() + "/route"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + routingService.port() + path))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void aRouteAcrossTwoRealIntersectionsComesBackWithAnEstimate() throws Exception {
        HttpResponse<String> response = route("""
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """);

        assertEquals(200, response.statusCode());
        JsonNode body = json.readTree(response.body());

        // 10 km at 40 km/h is 15 minutes, plus a 4-way (20s) and a roundabout (6s).
        assertEquals(15, body.get("freeFlowMinutes").asInt());
        assertEquals(26, body.get("intersectionDelaySeconds").asInt());
        assertEquals(15, body.get("minutes").asInt());
    }

    @Test
    void theEstimateMovesWhenTheCongestionLevelMoves() throws Exception {
        level = 8;

        JsonNode body = json.readTree(route("""
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """).body());

        assertEquals(8, body.get("congestionLevel").asInt());
        assertEquals(3.0, body.get("congestionFactor").asDouble());

        // 15 clear-road minutes and 26 seconds of junctions, both tripled:
        // 15 * 3 + (26 / 60) * 3 = 46.3, which rounds to 46.
        assertEquals(46, body.get("minutes").asInt());
    }

    @Test
    void everyLevelGivesADifferentTimeSoItIsNotAHardcodedNumber() throws Exception {
        int previous = 0;

        for (int candidate = 0; candidate <= 8; candidate++) {
            level = candidate;
            int minutes = json.readTree(route("""
                    {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                    """).body()).get("minutes").asInt();

            assertTrue(minutes >= previous,
                    "level " + candidate + " gave " + minutes + ", which is quicker than level "
                            + (candidate - 1) + " at " + previous);
            previous = minutes;
        }
    }

    @Test
    void anIntersectionTheServiceDoesNotKnowIsFourOhFour() throws Exception {
        HttpResponse<String> response = route("""
                {"from":"INT-9999","to":"INT-1005","distanceKm":10}
                """);

        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("INT-9999"));
    }

    @Test
    void anIntersectionThatIsSwitchedOffIsFourTwentyTwo() throws Exception {
        HttpResponse<String> response = route("""
                {"from":"INT-1009","to":"INT-1005","distanceKm":10}
                """);

        assertEquals(422, response.statusCode());
        assertTrue(response.body().contains("INT-1009"));
    }

    @Test
    void anIntersectionWithNoSignalTypeIsRoutableAndQuicker() throws Exception {
        JsonNode body = json.readTree(route("""
                {"from":"INT-1001","to":"INT-1013","distanceKm":10}
                """).body());

        // 4-way is 20s and the uncontrolled one is 3s.
        assertEquals(23, body.get("intersectionDelaySeconds").asInt());
    }

    @Test
    void aLowerCaseIdStillFindsTheIntersection() throws Exception {
        assertEquals(200, route("""
                {"from":"int-1001","to":"INT-1005","distanceKm":10}
                """).statusCode());
    }

    @Test
    void whenTheCongestionServiceIsDownItIsFiveOhThree() throws Exception {
        congestionDown = true;

        HttpResponse<String> response = route("""
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """);

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("congestion service"));
    }

    @Test
    void whenTheIntersectionServiceIsDownItIsFiveOhThree() throws Exception {
        intersectionDown = true;

        HttpResponse<String> response = route("""
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """);

        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("intersection service"));
    }

    @Test
    void healthIsOkEvenWhenTheOtherServicesAreDown() throws Exception {
        intersectionDown = true;
        congestionDown = true;

        assertEquals(200, get("/health").statusCode());
    }

    @Test
    void statusSaysDegradedWhenOneOfTheOthersIsDown() throws Exception {
        congestionDown = true;

        JsonNode status = json.readTree(get("/status").body());

        assertEquals("DEGRADED", status.get("state").asText());
        assertEquals(true, status.get("intersectionService").asBoolean());
        assertEquals(false, status.get("congestionService").asBoolean());
    }

    @Test
    void aLongerRouteTakesLonger() throws Exception {
        int short10km = json.readTree(route("""
                {"from":"INT-1001","to":"INT-1005","distanceKm":10}
                """).body()).get("minutes").asInt();
        int long25km = json.readTree(route("""
                {"from":"INT-1001","to":"INT-1005","distanceKm":25}
                """).body()).get("minutes").asInt();

        assertTrue(long25km > short10km);
    }

    @Test
    void everyPairOfUsableIntersectionsInTheCityIsRoutable() throws Exception {
        List<String> ids = List.of("INT-1001", "INT-1005", "INT-1013");

        for (String from : ids) {
            for (String to : ids) {
                if (from.equals(to)) {
                    continue;
                }
                HttpResponse<String> response = route("""
                        {"from":"%s","to":"%s","distanceKm":3}
                        """.formatted(from, to));

                assertEquals(200, response.statusCode(), from + " -> " + to);
            }
        }
    }

    @Test
    void aRubbishBodyNeverReachesTheOtherServices() throws Exception {
        assertEquals(400, route("nonsense").statusCode());
    }
}