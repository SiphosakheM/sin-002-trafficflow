package co.wethinkcode.trafficflow.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the message that goes onto the congestion topic.
 *
 * The payload is the contract between congestion-service and routing-service,
 * so it is pinned down here with no broker anywhere in sight.
 */
class CongestionMessageTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void theMessageCarriesTheLevelAndTheWordForIt() {
        // This is the wire format routing-service reads, so it is pinned exactly.
        assertEquals("{\"level\":5,\"label\":\"Moderate\"}", new CongestionMessage(5).asJson(json));
    }

    @Test
    void theLevelIsTheFirstAndOnlyThingNeededToParse() throws Exception {
        assertEquals(5, json.readTree(new CongestionMessage(5).asJson(json)).get("level").asInt());
    }

    @Test
    void levelZeroIsAMessageToo() throws Exception {
        assertEquals(0, json.readTree(new CongestionMessage(0).asJson(json)).get("level").asInt());
    }

    @Test
    void levelEightIsAMessageToo() throws Exception {
        assertEquals(8, json.readTree(new CongestionMessage(8).asJson(json)).get("level").asInt());
    }

    @Test
    void theWordForTheLevelIsIncludedSoAMessageCanBeReadByHand() throws Exception {
        assertEquals("Gridlock",
                json.readTree(new CongestionMessage(8).asJson(json)).get("label").asText());
    }

    @Test
    void aLevelOutsideZeroToEightIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new CongestionMessage(9));
    }

    @Test
    void aNegativeLevelIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new CongestionMessage(-1));
    }

    @Test
    void theRefusalSaysWhatTheRangeIs() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> new CongestionMessage(9));

        assertTrue(error.getMessage().contains("0"));
        assertTrue(error.getMessage().contains("8"));
    }

    @Test
    void theLevelIsReadBackOutOfAMessage() {
        assertEquals(7, CongestionMessage.fromJson("{\"level\":7}", json).level());
    }

    @Test
    void rubbishJsonCannotBecomeAMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> CongestionMessage.fromJson("not json", json));
    }

    @Test
    void jsonWithNoLevelInItCannotBecomeAMessage() {
        assertThrows(IllegalArgumentException.class,
                () -> CongestionMessage.fromJson("{\"label\":\"Clear\"}", json));
    }

    @Test
    void twoMessagesWithTheSameLevelAreEqual() {
        assertEquals(new CongestionMessage(3), new CongestionMessage(3));
    }

    @Test
    void theMessageRemembersTheLevelItWasMadeWith() {
        assertEquals(3, new CongestionMessage(3).level());
    }
}