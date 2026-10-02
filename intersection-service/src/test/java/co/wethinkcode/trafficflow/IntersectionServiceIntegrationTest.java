package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
 * Runs the whole path with real http on both sides.
 *
 * The other tests use a fake feed so they do not need a second service. This
 * one starts a fake *ingestion service over http* and lets the real
 * IngestionClient fetch it, so the json contract, the client, the registry and
 * the endpoints are all checked together. If the shape of the json from
 * ingestion-service ever changes, this test is the one that fails.
 */
class IntersectionServiceIntegrationTest {

    // The json the real ingestion service sends, copied from its output.
    private static final String INGESTION_JSON = """
            [
              {"id":"INT-1001","district":"Downtown","signalType":"4-way","active":true},
              {"id":"INT-1005","district":"Downtown","signalType":"roundabout","active":true},
              {"id":"INT-1009","district":"Westside","signalType":"4-way","active":false},
              {"id":"INT-1013","district":"Westside","signalType":null,"active":null},
              {"id":"INT-1015","district":null,"signalType":"4-way","active":true}
            ]
            """;

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper json = new ObjectMapper();

    // What the fake ingestion service sends. A test can change it to pretend
    // the ingestion service has new records for us.
    private static volatile String ingestionJson = INGESTION_JSON;

    // A test can set this to make the fake ingestion service answer with an error.
    private static volatile boolean ingestionIsBroken = false;

    private static Javalin fakeIngestion;
    private static Javalin intersectionService;

    @BeforeAll
    static void startBothServices() {
        fakeIngestion = Javalin.create().start(0);
        fakeIngestion.get("/intersections", ctx -> {
            if (ingestionIsBroken) {
                ctx.status(500).result("the ingestion service is having a bad day");
                return;
            }
            ctx.result(ingestionJson);
        });

        IntersectionCatalogue catalogue =
                new IntersectionCatalogue(new IngestionClient("http://localhost:" + fakeIngestion.port()));
        catalogue.refresh();
        intersectionService = IntersectionServiceApp.createApp(catalogue).start(0);
    }

    @AfterAll
    static void stopBothServices() {
        intersectionService.stop();
        fakeIngestion.stop();
    }

    // JUnit does not promise the order of the tests, so every test starts from
    // the same known state: the fake is healthy and the service has read it.
    @BeforeEach
    void resetTheFakeAndReadItAgain() throws Exception {
        ingestionJson = INGESTION_JSON;
        ingestionIsBroken = false;
        reload();
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + intersectionService.port() + path))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void theRecordsFromIngestionServiceEndUpBehindOurEndpoints() throws Exception {
        JsonNode body = json.readTree(get("/intersections").body());

        assertEquals(5, body.size());
        assertEquals("INT-1001", body.get(0).get("id").asText());
    }

    @Test
    void aNullInTheJsonStaysNullAllTheWayThrough() throws Exception {
        JsonNode body = json.readTree(get("/intersections").body());

        JsonNode noSignalType = body.get(3);
        assertEquals("INT-1013", noSignalType.get("id").asText());
        assertTrue(noSignalType.get("signalType").isNull());
        assertTrue(noSignalType.get("active").isNull());
    }

    @Test
    void aRecordWithNoDistrictIsStillServed() throws Exception {
        HttpResponse<String> response = get("/intersections/INT-1015");

        assertEquals(200, response.statusCode());
        assertTrue(json.readTree(response.body()).get("district").isNull());
    }

    @Test
    void theDistrictsListIsBuiltFromTheJsonWeWereGiven() throws Exception {
        JsonNode body = json.readTree(get("/districts").body());

        // Downtown and Westside. The record with no district is left out.
        assertEquals(2, body.size());
        assertEquals("Downtown", body.get(0).asText());
        assertEquals("Westside", body.get(1).asText());
    }

    @Test
    void anInactiveIntersectionIsKnownButNotRoutable() throws Exception {
        JsonNode body = json.readTree(get("/intersections/INT-1009/check").body());

        assertTrue(body.get("known").asBoolean());
        assertEquals(false, body.get("routable").asBoolean());
    }

    @Test
    void anIntersectionWithNoActiveFlagIsStillRoutable() throws Exception {
        JsonNode body = json.readTree(get("/intersections/INT-1013/check").body());

        assertTrue(body.get("known").asBoolean());
        assertTrue(body.get("routable").asBoolean());
    }

    @Test
    void statusSaysReadyBecauseIngestionAnswered() throws Exception {
        JsonNode body = json.readTree(get("/status").body());

        assertEquals("READY", body.get("state").asText());
        assertEquals(5, body.get("count").asInt());
    }

    @Test
    void aReloadPicksUpNewRecordsFromIngestion() throws Exception {
        // The fake service now sends one more record, and drops one.
        ingestionJson = """
                [
                  {"id":"INT-1001","district":"Downtown","signalType":"4-way","active":true},
                  {"id":"INT-2001","district":"Uptown","signalType":"stop-sign","active":true}
                ]
                """;

        assertEquals(200, reload());

        assertEquals(200, get("/intersections/INT-2001").statusCode());
        assertEquals(2, json.readTree(get("/intersections/count").body()).get("count").asInt());
    }

    @Test
    void aReloadThatFailsKeepsTheOldRecordsAndSaysItIsDegraded() throws Exception {
        // Go back to the normal answer and reload, so we know we are READY.
        ingestionJson = INGESTION_JSON;
        ingestionIsBroken = false;
        assertEquals(200, reload());

        // Now the ingestion service starts answering with an error.
        ingestionIsBroken = true;
        assertEquals(503, reload());

        // The records from the last good read are still there, so lookups work.
        assertEquals(200, get("/intersections/INT-1001").statusCode());
        assertEquals("DEGRADED", json.readTree(get("/status").body()).get("state").asText());
    }

    private int reload() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + intersectionService.port() + "/reload"))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    @Test
    void theCountEndpointAgreesWithTheList() throws Exception {
        int count = json.readTree(get("/intersections/count").body()).get("count").asInt();
        int listed = json.readTree(get("/intersections").body()).size();

        assertEquals(count, listed);
    }

    @Test
    void everyRecordWeServeHasAnUpperCaseId() throws Exception {
        for (JsonNode record : json.readTree(get("/intersections").body())) {
            String id = record.get("id").asText();
            assertEquals(id.toUpperCase(), id);
        }
    }

    @Test
    void theEndpointsAllAnswerWithoutFallingOver() throws Exception {
        List<String> paths = List.of(
                "/health", "/status", "/intersections", "/intersections/count",
                "/intersections/INT-1001", "/districts", "/districts/Downtown");

        for (String path : paths) {
            assertEquals(200, get(path).statusCode(), path + " should answer 200");
        }
    }
}