package co.wethinkcode.trafficflow.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks what the publisher puts on the topic.
 *
 * The sender is faked, so these tests need no broker. What is being checked is
 * our side of the wire: which destination, and what body.
 */
class CongestionPublisherTest {

    private final ObjectMapper json = new ObjectMapper();
    private final RecordingSender sender = new RecordingSender();
    private final CongestionPublisher publisher = new CongestionPublisher(sender, json);

    /** Stands in for ActiveMQ and remembers what it was asked to send. */
    private static class RecordingSender implements MessageSender {
        final List<String> destinations = new ArrayList<>();
        final List<String> bodies = new ArrayList<>();
        boolean broken = false;

        @Override
        public void send(String destination, String body) throws MessagingUnavailable {
            if (broken) {
                throw new MessagingUnavailable("the broker is not there");
            }
            destinations.add(destination);
            bodies.add(body);
        }
    }

    @Test
    void aMessageGoesToTheCongestionTopic() throws Exception {
        publisher.publish(new CongestionMessage(5));

        assertEquals("congestion-topic", sender.destinations.get(0));
    }

    @Test
    void theTopicNameComesFromTheSharedConfig() throws Exception {
        publisher.publish(new CongestionMessage(5));

        // Not a string written into our own code.
        assertEquals(MqConfig.TOPIC, sender.destinations.get(0));
    }

    @Test
    void theBodyIsTheMessageAsJson() throws Exception {
        publisher.publish(new CongestionMessage(5));

        assertEquals("{\"level\":5,\"label\":\"Moderate\"}", sender.bodies.get(0));
    }

    @Test
    void aBareLevelCanBePublishedDirectly() throws Exception {
        publisher.publish(8);

        assertEquals("{\"level\":8,\"label\":\"Gridlock\"}", sender.bodies.get(0));
    }

    @Test
    void everyChangeGetsItsOwnMessage() throws Exception {
        publisher.publish(1);
        publisher.publish(2);
        publisher.publish(3);

        assertEquals(3, sender.bodies.size());
        assertEquals("{\"level\":1,\"label\":\"Clear\"}", sender.bodies.get(0));
        assertEquals("{\"level\":2,\"label\":\"Light\"}", sender.bodies.get(1));
        assertEquals("{\"level\":3,\"label\":\"Light\"}", sender.bodies.get(2));
    }

    @Test
    void aLevelOutsideTheRangeNeverReachesTheBroker() {
        assertThrows(IllegalArgumentException.class, () -> publisher.publish(9));
    }

    @Test
    void whenTheBrokerIsNotThereTheFailureIsReportedNotSwallowed() {
        sender.broken = true;

        MessagingUnavailable error = assertThrows(
                MessagingUnavailable.class, () -> publisher.publish(5));

        assertTrue(error.getMessage().contains("congestion-topic"));
    }

    @Test
    void aPublishThatFailedIsNotCountedAsSent() {
        sender.broken = true;

        assertThrows(MessagingUnavailable.class, () -> publisher.publish(5));

        assertEquals(0, sender.bodies.size());
    }

    @Test
    void thePublisherRemembersWhetherTheLastSendWorked() throws Exception {
        assertEquals(0, publisher.sentCount());

        publisher.publish(5);
        assertEquals(1, publisher.sentCount());

        sender.broken = true;
        assertThrows(MessagingUnavailable.class, () -> publisher.publish(6));

        // The failed one did not count, so the count stays honest.
        assertEquals(1, publisher.sentCount());
    }

    @Test
    void thePublisherRemembersTheLastLevelItManagedToSend() throws Exception {
        publisher.publish(3);
        sender.broken = true;
        assertThrows(MessagingUnavailable.class, () -> publisher.publish(7));

        assertEquals(3, publisher.lastSentLevel());
    }
}