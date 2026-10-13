package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MessagingUnavailable;
import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Puts a message on the real dead-letter queue and checks the watchdog notices.
 *
 * This is the proof for the dead-letter half of stage 4: a heartbeat that ends
 * up in the dead-letter queue raises the same alert as a missed heartbeat.
 *
 * These tests need the broker from {@code common/docker-compose.yml} to be up.
 * If it is not, they are skipped rather than failed, so the suite still runs on
 * a machine with no broker. The parts that do not need a broker are covered by
 * {@link DeadLetterHandlerTest} and {@link WatchdogStateTest}.
 */
class DeadLetterConsumerOverBrokerTest {

    private final ObjectMapper json = new ObjectMapper();

    private WatchdogState state;
    private DeadLetterConsumer consumer;
    private Connection publisherConnection;
    private Session publisherSession;
    private MessageProducer producer;

    @BeforeEach
    void listen() {
        if (!brokerIsUp()) {
            return;
        }
        state = new WatchdogState();
        consumer = new DeadLetterConsumer(new DeadLetterHandler(json, state));
        try {
            consumer.start(MqConfig.BROKER_URL);
        } catch (MessagingUnavailable e) {
            throw new AssertionError("broker was up but the dead-letter subscription failed", e);
        }
    }

    @AfterEach
    void tidyUp() {
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }
        try {
            if (producer != null) {
                producer.close();
            }
            if (publisherSession != null) {
                publisherSession.close();
            }
            if (publisherConnection != null) {
                publisherConnection.close();
            }
        } catch (Exception ignored) {
            // Tidying up is best effort; nothing is asserted after this point.
        }
        producer = null;
        publisherSession = null;
        publisherConnection = null;
    }

    /** True when something is listening on the broker port. */
    private static boolean brokerIsUp() {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 61616), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Publishes one body to the dead-letter queue. */
    private void publishDeadLetter(String body) throws Exception {
        if (producer == null) {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
            publisherConnection = factory.createConnection();
            publisherConnection.start();
            publisherSession = publisherConnection.createSession(Session.AUTO_ACKNOWLEDGE);
            Queue queue = publisherSession.createQueue(MqConfig.DEAD_LETTER_QUEUE);
            producer = publisherSession.createProducer(queue);
        }

        TextMessage message = publisherSession.createTextMessage(body);
        producer.send(message);
    }

    /** Waits for the watchdog to notice a dead letter. */
    private void awaitAlert() {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (state.isDeadLettered()) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertTrue(state.isDeadLettered(), "the dead letter never reached the watchdog");
    }

    @Test
    void aDeadLetteredHeartbeatRaisesTheAlert() throws Exception {
        if (!brokerIsUp()) {
            return;
        }

        publishDeadLetter(
                "{\"service\":\"intersection-service\",\"instance\":\"abc\",\"at\":\"2026-01-01T00:00:00Z\"}");
        awaitAlert();

        assertTrue(state.deadLetterReason().contains("intersection-service"));
    }

    @Test
    void closingTwiceIsSafe() {
        if (!brokerIsUp()) {
            return;
        }

        consumer.close();
        consumer.close();
        consumer = null;
    }

    @Test
    void aBrokerThatIsDownFailsLoudlyRatherThanSilently() {
        if (brokerIsUp()) {
            return;
        }

        DeadLetterConsumer down = new DeadLetterConsumer(new DeadLetterHandler(json, new WatchdogState()));
        assertThrows(MessagingUnavailable.class, () -> down.start("tcp://localhost:61616"));
    }
}
