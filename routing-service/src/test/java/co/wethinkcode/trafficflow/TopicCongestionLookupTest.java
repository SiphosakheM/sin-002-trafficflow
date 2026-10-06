package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.CongestionMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the level that routing-service keeps from the topic.
 *
 * Nothing here touches a broker. This is the part that turns messages into
 * answers, and the part that decides what to do when no message has ever
 * arrived — which is the interesting case, because a topic only tells you about
 * changes and the service starts at level 0.
 */
class TopicCongestionLookupTest {

    private final ObjectMapper json = new ObjectMapper();
    private TopicCongestionLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new TopicCongestionLookup(json);
    }

    @Test
    void withNoMessageEverItSaysItDoesNotKnow() {
        CongestionLookupUnavailable error = assertThrows(
                CongestionLookupUnavailable.class, lookup::read);

        assertTrue(error.getMessage().contains("not heard"));
    }

    @Test
    void aMessagePutsTheLevelInTheCache() throws Exception {
        lookup.update("{\"level\":6,\"label\":\"Heavy\"}");

        assertEquals(6, lookup.read().level());
    }

    @Test
    void theWordComesAcrossWithTheLevel() throws Exception {
        lookup.update("{\"level\":8,\"label\":\"Gridlock\"}");

        assertEquals("Gridlock", lookup.read().label());
    }

    @Test
    void theBusyFlagIsWorkedOutWhenTheMessageDoesNotCarryOne() throws Exception {
        lookup.update("{\"level\":7}");

        assertEquals(7, lookup.read().level());
        assertTrue(lookup.read().busy());
    }

    @Test
    void aLevelBelowSixIsNotBusy() throws Exception {
        lookup.update("{\"level\":5}");

        assertFalse(lookup.read().busy());
    }

    @Test
    void eachMessageReplacesTheOneBefore() throws Exception {
        lookup.update("{\"level\":3,\"label\":\"Light\"}");
        lookup.update("{\"level\":8,\"label\":\"Gridlock\"}");

        assertEquals(8, lookup.read().level());
    }

    @Test
    void onceAMessageHasArrivedItStaysKnown() throws Exception {
        lookup.update("{\"level\":4}");

        // The publisher can go quiet — it only speaks when the level changes.
        assertEquals(4, lookup.read().level());
        assertEquals(4, lookup.read().level());
    }

    @Test
    void aRubbishMessageDoesNotDestroyWhatWeAlreadyKnew() throws Exception {
        lookup.update("{\"level\":5}");

        try {
            lookup.update("not json at all");
        } catch (Exception expected) {
            // The bad message is discarded; the good one stays.
        }

        assertEquals(5, lookup.read().level());
    }

    @Test
    void aMessageWithNoLevelDoesNotDestroyWhatWeAlreadyKnew() throws Exception {
        lookup.update("{\"level\":5}");

        try {
            lookup.update("{\"label\":\"Clear\"}");
        } catch (Exception expected) {
            // Also discarded.
        }

        assertEquals(5, lookup.read().level());
    }

    @Test
    void itIsNotReachableUntilAMessageArrives() {
        assertFalse(lookup.isReachable());
    }

    @Test
    void itIsReachableOnceAMessageArrives() throws Exception {
        lookup.update("{\"level\":1}");

        assertTrue(lookup.isReachable());
    }

    @Test
    void beforeAMessageArrivesTheServiceIsDegradedNotBroken() {
        assertFalse(lookup.isReachable());
        assertThrows(CongestionLookupUnavailable.class, lookup::read);
    }

    @Test
    void theMessageRoundTripsThroughTheLookup() throws Exception {
        lookup.update(new CongestionMessage(6).asJson(json));

        CongestionReading reading = lookup.read();

        assertEquals(6, reading.level());
        assertEquals("Heavy", reading.label());
        assertTrue(reading.busy());
    }

    @Test
    void theLevelItKnowsIsReportedForTheStatusPage() throws Exception {
        assertEquals(-1, lookup.lastLevel());

        lookup.update("{\"level\":3}");

        assertEquals(3, lookup.lastLevel());
    }
}