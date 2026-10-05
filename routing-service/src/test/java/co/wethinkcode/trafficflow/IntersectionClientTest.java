package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks our call to the intersection service on port 7021.
 *
 * The stand-in is a real http server, so this test goes through the real
 * HttpClient and the real json reading.
 */
class IntersectionClientTest {

    private final ObjectMapper json = new ObjectMapper();

    private Javalin fakeIntersectionService;
    private IntersectionClient client;

    // What the stand-in should answer for a given id.
    private final Map<String, boolean[]> answers = new HashMap<>();

    // What the stand-in should answer overall. "ok", "error" or "broken".
    private String mode = "ok";

    @BeforeEach
    void startTheStandIn() {
        answers.put("INT-1001", new boolean[]{true, true});
        answers.put("INT-1009", new boolean[]{true, false});

        fakeIntersectionService = Javalin.create().start(0);
        fakeIntersectionService.get("/health", ctx -> ctx.result("OK"));
        fakeIntersectionService.get("/intersections/{id}/check", this::answerCheck);

        client = new IntersectionClient("http://localhost:" + fakeIntersectionService.port(), json);
    }

    /** Puts the stand-in's answer on the wire, depending on the mode we are in. */
    private void answerCheck(Context ctx) {
        if (mode.equals("error")) {
            ctx.status(503).result("I cannot answer right now");
            return;
        }
        if (mode.equals("broken")) {
            ctx.status(200).result("this is not json");
            return;
        }

        boolean[] answer = answers.getOrDefault(ctx.pathParam("id"), new boolean[]{false, false});
        ctx.json(Map.of("id", ctx.pathParam("id"), "known", answer[0], "routable", answer[1]));
    }

    @AfterEach
    void stopTheStandIn() {
        fakeIntersectionService.stop();
    }

    @Test
    void anIntersectionThatIsKnownAndRoutableIsToldSo() throws Exception {
        IntersectionCheck check = client.check("INT-1001");

        assertTrue(check.known());
        assertTrue(check.routable());
        assertEquals("INT-1001", check.id());
    }

    @Test
    void anIntersectionThatIsKnownButClosedIsNotRoutable() throws Exception {
        IntersectionCheck check = client.check("INT-1009");

        assertTrue(check.known());
        assertFalse(check.routable());
    }

    @Test
    void anIntersectionWeHaveNeverHeardOfIsNotKnown() throws Exception {
        IntersectionCheck check = client.check("INT-9999");

        assertFalse(check.known());
        assertFalse(check.routable());
    }

    @Test
    void theIdIsMadeUpperCaseBeforeWeAsk() throws Exception {
        // The stand-in only knows the upper case form.
        assertTrue(client.check("int-1001").known());
    }

    @Test
    void anErrorFromTheIntersectionServiceIsAnUnavailability() {
        mode = "error";

        IntersectionLookupUnavailable error = assertThrows(
                IntersectionLookupUnavailable.class, () -> client.check("INT-1001"));

        assertTrue(error.getMessage().contains("intersection service"));
    }

    @Test
    void anAnswerThatIsNotJsonIsAnUnavailability() {
        mode = "broken";

        assertThrows(IntersectionLookupUnavailable.class, () -> client.check("INT-1001"));
    }

    @Test
    void whenTheIntersectionServiceIsNotThereAtAllWeSaySo() {
        // Nothing is listening on this port, so the call cannot even be made.
        IntersectionClient lonely = new IntersectionClient("http://localhost:1", json);

        IntersectionLookupUnavailable error =
                assertThrows(IntersectionLookupUnavailable.class, () -> lonely.check("INT-1001"));

        assertTrue(error.getMessage().contains("intersection service"));
    }

    @Test
    void theServiceCanBeAskedIfItIsThere() throws Exception {
        assertTrue(client.isReachable());
    }

    @Test
    void theServiceIsNotThereWhenNothingAnswers() {
        IntersectionClient lonely = new IntersectionClient("http://localhost:1", json);

        assertFalse(lonely.isReachable());
    }
}