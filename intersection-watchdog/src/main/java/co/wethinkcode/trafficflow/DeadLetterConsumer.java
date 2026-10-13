package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MessagingUnavailable;
import co.wethinkcode.trafficflow.mq.MqConfig;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * Watches the broker's dead-letter queue.
 *
 * The heartbeat queue covers "is it still talking"; this covers "did something
 * it said fail to be delivered". Both are reasons for the watchdog to shout.
 */
public class DeadLetterConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    private final DeadLetterHandler handler;
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    public DeadLetterConsumer(DeadLetterHandler handler) {
        this.handler = handler;
    }

    public void start(String brokerUrl) throws MessagingUnavailable {
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            factory.setWatchTopicAdvisories(false);
            connection = factory.createConnection();
            connection.start();
            session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(MqConfig.DEAD_LETTER_QUEUE);
            consumer = session.createConsumer(queue);
            consumer.setMessageListener(message -> {
                try {
                    if (message instanceof TextMessage text) {
                        handler.handle(text.getText());
                    }
                } catch (Exception e) {
                    log.warn("Unreadable dead letter: {}", e.getMessage());
                }
            });
        } catch (Exception e) {
            close();
            throw new MessagingUnavailable(
                    "Could not consume from " + MqConfig.DEAD_LETTER_QUEUE + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try { if (consumer != null) consumer.close(); } catch (Exception ignored) {}
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
        consumer = null;
        session = null;
        connection = null;
    }
}
