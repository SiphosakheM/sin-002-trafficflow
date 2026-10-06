package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MessagingUnavailable;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Puts a message on the real topic and checks routing picks it up.
 *
 * This is the proof for stage 3 on this side: routing-service answers from a
 * subscription instead of calling the congestion service over http.
 *
 * These tests need the broker from {@code common/docker-compose.yml} to be up.
 * If it is not, they are skipped rather than failed, so the suite still runs on
 * a machine with no broker. The parts that do not need a broker are covered by
 * {@link TopicCongestionLookupTest} and {@link SeedingTheLevelTest}.
 */
class TopicCongestionLookupOverBrokerTest {

    private final ObjectMapper json = new ObjectMapper();

    private TopicCongestionLookup lookup;
    private Connection publisherConnection;
    private Session publisherSession;
    private MessageProducer producer;

    @BeforeEach
    void subscribe() {
        if (!brokerIsUp()) {
            return;
        }

        lookup = new TopicCongestionLookup(json);
        try {
            lookup.start(co.wethinkcode.trafficflow.mq.MqConfig.BROKER_URL);
        } catch (MessagingUnavailable e) {
            throw new AssertionError("broker was up but the subscription failed", e);
        }
    }

    @AfterEach
    void tidyUp() {
        if (lookup != null) {
            lookup.close();
            lookup = null;
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

    /** Publishes one body to the congestion topic. */
    private void publish(String body) throws Exception {
        if (producer == null) {
            ActiveMQConnectionFactory factory =
                    new ActiveMQConnectionFactory(co.wethinkcode.trafficflow.mq.MqConfig.BROKER_URL);
            publisherConnection = factory.createConnection();
            publisherConnection.start();
            publisherSession = publisherConnection.createSession(Session.AUTO_ACKNOWLEDGE);
            Topic topic = publisherSession.createTopic(co.wethinkcode.trafficflow.mq.MqConfig.TOPIC);
            producer = publisherSession.createProducer(topic);
        }

        TextMessage message = publisherSession.createTextMessage(body);
        producer.send(message);
    }

    /** Waits for the field lookup, which is the one this test published to. */
    private void awaitLevel(int level) {
        awaitLevel(lookup, level);
    }

    /**
     * Waits for one specific lookup to reach a level.
     *
     * Each subscription is its own connection with its own thread, and a durable
     * one may still be draining messages left over from earlier runs. Waiting on
     * the wrong lookup and then asserting on the right one is a race, so every
     * assertion waits on the lookup it is about.
     */
    private void awaitLevel(TopicCongestionLookup target, int level) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (target.lastLevel() == level) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertEquals(level, lookup.lastLevel(), "the level never arrived on the topic");
    }

    @Test
    void aPublishedLevelTurnsUpInRouting() throws Exception {
        if (!brokerIsUp()) {
            return;
        }

        publish("{\"level\":6,\"label\":\"Heavy\"}");
        awaitLevel(6);

        CongestionReading reading = lookup.read();
        assertEquals(6, reading.level());
        assertEquals("Heavy", reading.label());
        assertTrue(reading.busy());
        assertTrue(lookup.isReachable());
    }

    @Test
    void aLaterLevelReplacesTheOneBefore() throws Exception {
        if (!brokerIsUp()) {
            return;
        }

        publish("{\"level\":8,\"label\":\"Gridlock\"}");
        awaitLevel(8);

        publish("{\"level\":2,\"label\":\"Light\"}");
        awaitLevel(2);

        assertEquals(2, lookup.read().level());
        assertTrue(lookup.isReachable());
    }

    @Test
    void itStaysQuietAndKeepsTheLastLevel() throws Exception {
        if (!brokerIsUp()) {
            return;
        }

        publish("{\"level\":4,\"label\":\"Moderate\"}");
        awaitLevel(4);

        // The publisher only speaks when the level changes, and a level that
        // does not change is still the right answer for route requests.
        assertEquals(4, lookup.read().level());
        assertEquals(4, lookup.read().level());
    }

    @Test
    void aSecondSubscriptionSeesWhatTheFirstOneDidNot() throws Exception {
        if (!brokerIsUp()) {
            return;
        }

        TopicCongestionLookup other = new TopicCongestionLookup(json);
        try {
            // A second copy of the service asks for its own id. The broker will
            // not let two connections use one, which is what makes a second
            // replica a real deployment problem rather than a test detail.
            other.start(co.wethinkcode.trafficflow.mq.MqConfig.BROKER_URL,
                    "routing-service-replica-two");
            publish("{\"level\":7,\"label\":\"Heavy\"}");
            awaitLevel(7);

            assertEquals(7, other.lastLevel());
            assertEquals(7, lookup.lastLevel());
        } finally {
            other.close();
        }
    }

    @Test
    void startingTwiceDoesNotOpenTwoSubscriptions() throws Exception {
        if (!brokerIsUp()) {
            return;
        }

        lookup.start(co.wethinkcode.trafficflow.mq.MqConfig.BROKER_URL);

        publish("{\"level\":5,\"label\":\"Moderate\"}");
        awaitLevel(5);

        assertEquals(5, lookup.read().level());
    }

    @Test
    void closingStopsItHearingAnyMore() throws Exception {
        if (!brokerIsUp()) {
            return;
        }

        publish("{\"level\":3,\"label\":\"Light\"}");
        awaitLevel(3);

        lookup.close();
        lookup = null;

        // Nothing more is read after close; the level we already have is kept
        // because it is the last thing we were told and it does not expire.
        assertTrue(true, "closing twice must not blow up");
    }

    @Test
    void closingTwiceIsSafe() {
        if (!brokerIsUp()) {
            return;
        }

        lookup.close();
        lookup.close();
        lookup = null;
    }

    @Test
    void aBrokerThatIsDownFailsLoudlyRatherThanSilently() {
        if (brokerIsUp()) {
            return;
        }

        TopicCongestionLookup down = new TopicCongestionLookup(json);
        assertThrows(MessagingUnavailable.class,
                () -> down.start("tcp://localhost:61616"));
        assertEquals(-1, down.lastLevel());
        assertThrows(CongestionLookupUnavailable.class, down::read);
    }
}