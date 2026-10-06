package co.wethinkcode.trafficflow.mq;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the real ActiveMQ sender against the real broker.
 *
 * These tests need the broker from {@code common/docker-compose.yml} to be up.
 * If it is not, every test here is skipped rather than failed, so the suite
 * still runs on a machine with no broker. The logic that does not need a broker
 * is tested in {@link CongestionPublisherTest} instead.
 */
class ActiveMqSenderTest {

    private static final String BROKER = MqConfig.BROKER_URL;

    private Connection consumerConnection;
    private Session consumerSession;
    private Destination topic;

    /**
     * The subscriber, made once and kept.
     *
     * A topic only gives you what is published after you subscribe, so the
     * consumer has to exist before the test publishes anything. Making a new
     * consumer per message would miss them all.
     */
    private MessageConsumer consumer;

    @BeforeEach
    void subscribeSoWeHaveSomethingToLookAt() throws Exception {
        if (!brokerIsUp()) {
            return;
        }

        ConnectionFactory factory = new ActiveMQConnectionFactory(BROKER);
        consumerConnection = factory.createConnection();
        consumerConnection.start();
        consumerSession = consumerConnection.createSession(Session.AUTO_ACKNOWLEDGE);
        topic = consumerSession.createTopic(MqConfig.TOPIC);
        consumer = consumerSession.createConsumer(topic);
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (consumerConnection != null) {
            consumerConnection.close();
        }
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

    /** Pulls the next message off the topic, or gives up after a moment. */
    private String nextMessageBody() throws JMSException {
        Message message = consumer.receive(5000);
        assertNotNull(message, "expected a message on " + MqConfig.TOPIC);
        return ((TextMessage) message).getText();
    }

    /** The next message as a Message, for checking what kind it is. */
    private Message nextMessage() throws JMSException {
        Message message = consumer.receive(5000);
        assertNotNull(message, "expected a message on " + MqConfig.TOPIC);
        return message;
    }

    @Test
    void aMessageSentToTheTopicArrivesOnIt() throws Exception {
        if (!brokerIsUp()) {
            return;
        }

        ActiveMqSender sender = new ActiveMqSender(BROKER);
        try {
            sender.send(MqConfig.TOPIC, "{\"level\":5,\"label\":\"Moderate\"}");
        } finally {
            sender.close();
        }

        assertEquals("{\"level\":5,\"label\":\"Moderate\"}", nextMessageBody());
    }

    @Test
    void theSenderCanSendSeveralMessagesInARow() throws Exception {
        if (!brokerIsUp()) {
            return;
        }

        ActiveMqSender sender = new ActiveMqSender(BROKER);
        try {
            sender.send(MqConfig.TOPIC, "{\"level\":1}");
            sender.send(MqConfig.TOPIC, "{\"level\":2}");
            sender.send(MqConfig.TOPIC, "{\"level\":3}");
        } finally {
            sender.close();
        }

        assertEquals("{\"level\":1}", nextMessageBody());
        assertEquals("{\"level\":2}", nextMessageBody());
        assertEquals("{\"level\":3}", nextMessageBody());
    }

    @Test
    void theWholePublishPathWorksAgainstTheRealBroker() throws Exception {
        if (!brokerIsUp()) {
            return;
        }

        CongestionPublisher publisher =
                new CongestionPublisher(new ActiveMqSender(BROKER), new com.fasterxml.jackson.databind.ObjectMapper());
        publisher.publish(6);

        assertEquals("{\"level\":6,\"label\":\"Heavy\"}", nextMessageBody());
        assertEquals(1, publisher.sentCount());
        assertEquals(6, publisher.lastSentLevel());
    }

    @Test
    void sendingToABrokerThatIsNotThereIsReportedNotHidden() {
        // Port 1 is nothing, so this cannot connect.
        ActiveMqSender sender = new ActiveMqSender("tcp://localhost:1");

        MessagingUnavailable error = assertThrows(MessagingUnavailable.class,
                () -> sender.send(MqConfig.TOPIC, "{\"level\":1}"));

        assertTrue(error.getMessage().contains("congestion-topic"));
    }

    @Test
    void aSenderWithNoBrokerStillCountsNothingAsSent() {
        CongestionPublisher publisher = new CongestionPublisher(
                new ActiveMqSender("tcp://localhost:1"), new com.fasterxml.jackson.databind.ObjectMapper());

        assertThrows(MessagingUnavailable.class, () -> publisher.publish(4));

        assertEquals(0, publisher.sentCount());
    }

    @Test
    void closingASenderTwiceIsHarmless() {
        ActiveMqSender sender = new ActiveMqSender("tcp://localhost:1");

        sender.close();
        sender.close();
    }

    @Test
    void aSenderRecoversAfterBeingClosed() throws Exception {
        ActiveMqSender sender = new ActiveMqSender(BROKER);
        sender.close();

        // Close drops the connection, and the next send quietly makes a new one.
        // That is what lets a service recover from a broker restart.
        sender.send(MqConfig.TOPIC, "{\"level\":1}");
        sender.close();

        assertEquals("{\"level\":1}", nextMessageBody());
    }

    @Test
    void theMessageIsSentAsTextSoASubscriberCanReadItAsAString() throws Exception {
        if (!brokerIsUp()) {
            return;
        }

        ActiveMqSender sender = new ActiveMqSender(BROKER);
        try {
            sender.send(MqConfig.TOPIC, "{\"level\":7,\"label\":\"Severe\"}");
        } finally {
            sender.close();
        }

        Message message = nextMessage();
        assertTrue(message instanceof ActiveMQTextMessage,
                "should be a text message, not " + message.getClass());
    }
}