package co.wethinkcode.trafficflow.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;

public class ActiveMqReceiver implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ActiveMqReceiver.class);

    private final String brokerUrl;
    private final String queueName;
    private final ObjectMapper json;
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;
    private volatile boolean running;

    public ActiveMqReceiver(String brokerUrl, String queueName, ObjectMapper json) {
        this.brokerUrl = brokerUrl;
        this.queueName = queueName;
        this.json = json;
    }

    public void start(MessageListener listener) throws MessagingUnavailable {
        if (running) {
            return;
        }
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            factory.setWatchTopicAdvisories(false);
            connection = factory.createConnection();
            connection.start();
            session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(queueName);
            consumer = session.createConsumer(queue);
            consumer.setMessageListener(listener);
            running = true;
        } catch (Exception e) {
            close();
            throw new MessagingUnavailable("Could not receive from " + queueName + " at " + brokerUrl + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        running = false;
        try { if (consumer != null) consumer.close(); } catch (Exception ignored) {}
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
        consumer = null;
        session = null;
        connection = null;
    }
}
