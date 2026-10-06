package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.CongestionPublisher;
import co.wethinkcode.trafficflow.mq.CongestionMessage;
import co.wethinkcode.trafficflow.mq.MessagingUnavailable;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks that a level change puts a message on the topic.
 *
 * The sender is faked, so nothing here needs a broker. What is being tested is
 * the wiring: the tracker's listener and the publisher, connected the way the
 * service connects them at startup.
 */
class CongestionTopicWiringTest {

    private final ObjectMapper json = new ObjectMapper();
    private final RecordingSender sender = new RecordingSender();
    private final CongestionPublisher publisher = new CongestionPublisher(sender, json);

    private CongestionTracker tracker;

    /** Stands in for ActiveMQ and remembers what it was asked to send. */
    private static class RecordingSender
            implements co.wethinkcode.trafficflow.mq.MessageSender {
        final List<String> bodies = new ArrayList<>();
        boolean broken = false;

        @Override
        public void send(String destination, String body) throws MessagingUnavailable {
            if (broken) {
                throw new MessagingUnavailable("the broker is not there");
            }
            bodies.add(body);
        }
    }

    @BeforeEach
    void setUp() {
        tracker = new CongestionTracker();
        CongestionServiceApp.wireTopicPublishing(tracker, publisher);
    }

    @Test
    void settingTheLevelPublishesAMessage() throws Exception {
        tracker.set(CongestionLevel.of(5));

        assertEquals(List.of("{\"level\":5,\"label\":\"Moderate\"}"), sender.bodies);
    }

    @Test
    void everyStepUpPublishesItsOwnMessage() throws Exception {
        tracker.stepUp();
        tracker.stepUp();
        tracker.stepUp();

        assertEquals(3, sender.bodies.size());
        assertEquals("{\"level\":1,\"label\":\"Clear\"}", sender.bodies.get(0));
        assertEquals("{\"level\":2,\"label\":\"Light\"}", sender.bodies.get(1));
        assertEquals("{\"level\":3,\"label\":\"Light\"}", sender.bodies.get(2));
    }

    @Test
    void aStepDownPublishesToo() throws Exception {
        tracker.set(CongestionLevel.of(6));
        tracker.stepDown();

        assertEquals(List.of("{\"level\":6,\"label\":\"Heavy\"}", "{\"level\":5,\"label\":\"Moderate\"}"),
                sender.bodies);
    }

    @Test
    void settingTheLevelToWhatItAlreadyIsPublishesNothing() throws Exception {
        tracker.set(CongestionLevel.of(4));
        sender.bodies.clear();

        tracker.set(CongestionLevel.of(4));

        assertTrue(sender.bodies.isEmpty(), "nothing changed, so nothing should be published");
    }

    @Test
    void startingAtLevelZeroPublishesNothing() {
        // The service starts at 0 and that is not a change, so no message.
        assertTrue(sender.bodies.isEmpty());
    }

    @Test
    void aBrokerThatIsDownDoesNotStopTheLevelChanging() throws Exception {
        sender.broken = true;

        tracker.set(CongestionLevel.of(7));

        // The level change is the important thing and it still happened.
        assertEquals(7, tracker.currentLevel());
    }

    @Test
    void aBrokerThatIsDownDoesNotStopTheOtherListeners() throws Exception {
        sender.broken = true;
        List<CongestionLevel> alsoNoticed = new ArrayList<>();
        tracker.onChange((newLevel, oldLevel) -> alsoNoticed.add(newLevel));

        tracker.set(CongestionLevel.of(7));

        assertEquals(List.of(CongestionLevel.of(7)), alsoNoticed);
    }

    @Test
    void aBrokerThatIsDownDoesNotBreakTheNextRequest() throws Exception {
        sender.broken = true;
        tracker.set(CongestionLevel.of(7));

        // The broker comes back.
        sender.broken = false;
        tracker.set(CongestionLevel.of(8));

        assertEquals("{\"level\":8,\"label\":\"Gridlock\"}", sender.bodies.get(0));
    }

    @Test
    void aFailedPublishIsNotCountedAsOneThatWorked() {
        sender.broken = true;
        tracker.set(CongestionLevel.of(7));

        assertEquals(0, publisher.sentCount());
        assertEquals(-1, publisher.lastSentLevel());
    }

    @Test
    void historyStillRecordsTheChangeEvenWhenTheBrokerIsDown() {
        sender.broken = true;

        tracker.set(CongestionLevel.of(7));

        // Two entries: the constructor's "start up" one, and this change.
        assertEquals(2, tracker.history().size());
        assertEquals("set", tracker.history().get(0).reason());
    }

    @Test
    void onlyOneMessageGoesOutForEachChange() throws Exception {
        tracker.set(CongestionLevel.of(4));
        tracker.set(CongestionLevel.of(4));

        assertEquals(1, sender.bodies.size());
    }

    @Test
    void wiringIsHarmlessToRegisterTwiceInARow() throws Exception {
        CongestionServiceApp.wireTopicPublishing(tracker, publisher);
        CongestionServiceApp.wireTopicPublishing(tracker, publisher);

        tracker.set(CongestionLevel.of(5));

        // Three listeners on one change would mean three identical messages.
        assertEquals(1, sender.bodies.size(), "one change must produce one message");
    }

    @Test
    void theMessageThatGoesOutIsReadableByTheOtherSide() throws Exception {
        tracker.set(CongestionLevel.of(6));

        CongestionMessage readBack = CongestionMessage.fromJson(sender.bodies.get(0), json);

        assertEquals(6, readBack.level());
        assertEquals(6, CongestionMessage.fromJson(sender.bodies.get(0), json).level());
        assertFalse(readBack.asJson(json).isEmpty());
    }
}