package co.wethinkcode.trafficflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks our call to the congestion service on port 7022.
 *
 * The stand-in is a real http server, so the real client and the real json
 * reading are both used.
 */
class CongestionClientTest {

    private final ObjectMapper json = new ObjectMapper();

    private Javalin fakeCongestionService;
    private CongestionClient client;

    // What the stand-in should answer. A level, or an error, or rubbish.
    private String mode = "level";

    @BeforeEach
    void startTheStandIn() {
        fakeCongestionService = Javalin.create().start(0);
        fakeCongestionService.get("/health", ctx -> ctx.result("OK"));
        fakeCongestionService.get("/congestion", ctx -> {
            switch (mode) {
                case "level" -> ctx.json(new CongestionReading(5, "Moderate", false).asMap());
                case "gridlock" -> ctx.json(new CongestionReading(8, "Gridlock", true).asMap());
                case "noLabel" -> ctx.result("{\"level\":6}");
                case "error" -> ctx.status(503).result("I cannot answer right now");
                case "rubbish" -> ctx.result("this is not json");
                case "noLevel" -> ctx.result("{\"label\":\"Moderate\"}");
                default -> ctx.result("{}");
            }
        });

        client = new CongestionClient("http://localhost:" + fakeCongestionService.port(), json);
    }

    @AfterEach
    void stopTheStandIn() {
        fakeCongestionService.stop();
    }

    @Test
    void theLevelComesBackWithItsWordAndBusyFlag() throws Exception {
        CongestionReading reading = client.read();

        assertEquals(5, reading.level());
        assertEquals("Moderate", reading.label());
        assertEquals(false, reading.busy());
    }

    @Test
    void theReadingCanSayWhetherTheCityIsBusy() throws Exception {
        mode = "gridlock";

        assertTrue(client.read().busy());
    }

    @Test
    void aMissingWordIsFilledInFromTheLevelWeWereGiven() throws Exception {
        mode = "noLabel";

        // The level is the fact we need; the word is only there to be read.
        assertEquals(6, client.read().level());
        assertEquals("Heavy", client.read().label());
    }

    @Test
    void anErrorFromTheCongestionServiceIsAnUnavailability() {
        mode = "error";

        CongestionLookupUnavailable error = assertThrows(
                CongestionLookupUnavailable.class, client::read);

        assertTrue(error.getMessage().contains("congestion service"));
    }

    @Test
    void anAnswerThatIsNotJsonIsAnUnavailability() {
        mode = "rubbish";

        assertThrows(CongestionLookupUnavailable.class, client::read);
    }

    @Test
    void anAnswerWithNoLevelIsAnUnavailabilityRatherThanAGuess() {
        mode = "noLevel";

        assertThrows(CongestionLookupUnavailable.class, client::read);
    }

    @Test
    void whenTheCongestionServiceIsNotThereAtAllWeSaySo() {
        CongestionClient lonely = new CongestionClient("http://localhost:1", json);

        assertThrows(CongestionLookupUnavailable.class, lonely::read);
    }

    @Test
    void theServiceCanBeAskedIfItIsThere() throws Exception {
        assertTrue(client.isReachable());
    }

    @Test
    void theServiceIsNotThereWhenNothingAnswers() {
        assertEquals(false, new CongestionClient("http://localhost:1", json).isReachable());
    }
}