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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    // What the stand-in should answer for a given id: active, signalType.
    private final Map<String, Object[]> answers = new HashMap<>();

    // What the stand-in should answer overall: "ok", "error", "broken".
    private String mode = "ok";

    @BeforeEach
    void startTheStandIn() {
        answers.put("INT-1001", new Object[]{Boolean.TRUE, "4-way"});
        answers.put("INT-1009", new Object[]{Boolean.FALSE, "4-way"});
        answers.put("INT-1013", new Object[]{null, null});

        fakeIntersectionService = Javalin.create().start(0);
        fakeIntersectionService.get("/health", ctx -> ctx.result("OK"));
        fakeIntersectionService.get("/intersections/{id}", this::answerLookup);

        client = new IntersectionClient("http://localhost:" + fakeIntersectionService.port(), json);
    }

    /** Puts the stand-in's answer on the wire, depending on the mode we are in. */
    private void answerLookup(Context ctx) {
        if (mode.equals("error")) {
            ctx.status(503).result("I cannot answer right now");
            return;
        }
        if (mode.equals("broken")) {
            ctx.result("this is not json");
            return;
        }

        String id = ctx.pathParam("id");
        Object[] answer = answers.get(id);
        if (answer == null) {
            // The real service answers 404 for an id it has never heard of.
            ctx.status(404);
            return;
        }

        // The real service sends the whole record, so this stands in does too.
        // We keep active as null rather than dropping it, like the real service.
        Map<String, Object> record = new HashMap<>();
        record.put("id", id);
        record.put("district", "Downtown");
        if (answer[1] != null) {
            record.put("signalType", answer[1]);
        }
        if (answer[0] != null) {
            record.put("active", answer[0]);
        }
        ctx.json(record);
    }

    @AfterEach
    void stopTheStandIn() {
        fakeIntersectionService.stop();
    }

    @Test
    void anIntersectionThatIsOnAndWorkingIsToldSo() throws Exception {
        IntersectionCheck check = client.check("INT-1001");

        assertTrue(check.known());
        assertTrue(check.routable());
        assertEquals("INT-1001", check.id());
        assertEquals("4-way", check.signalType());
    }

    @Test
    void anIntersectionThatIsSwitchedOffIsNotRoutable() throws Exception {
        IntersectionCheck check = client.check("INT-1009");

        assertTrue(check.known());
        assertFalse(check.routable());
    }

    @Test
    void anIntersectionWithNoActiveFlagIsAssumedToBeOpen() throws Exception {
        IntersectionCheck check = client.check("INT-1013");

        assertTrue(check.known());
        assertTrue(check.routable());
    }

    @Test
    void aMissingSignalTypeComesBackAsNullNotAsAGuess() throws Exception {
        assertNull(client.check("INT-1013").signalType());
    }

    @Test
    void anIntersectionWeHaveNeverHeardOfIsNotKnown() throws Exception {
        IntersectionCheck check = client.check("INT-9999");

        assertFalse(check.known());
        assertFalse(check.routable());
    }

    @Test
    void anUnknownIntersectionIsNotAnErrorItIsJustUnknown() throws Exception {
        // The point of this one: a 404 must not blow up as an unavailability.
        assertFalse(client.check("INT-0000").usableForARoute());
    }

    @Test
    void theIdIsMadeUpperCaseBeforeWeAsk() throws Exception {
        // The stand-in only knows the upper case form.
        assertTrue(client.check("int-1001").known());
    }

    @Test
    void theIntersectionServiceBeingDownIsAnUnavailability() {
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