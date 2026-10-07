package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MessagingUnavailable;
import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.util.concurrent.atomic.AtomicLong;

public class HeartbeatConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatConsumer.class);

    private final ObjectMapper json;
    private final WatchdogState state;
    private final long timeoutMillis;
    private final AtomicLong lastSeen = new AtomicLong(System.currentTimeMillis());
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;
    private Thread monitor;

    public HeartbeatConsumer(ObjectMapper json, WatchdogState state, long timeoutMillis) {
        this.json = json;
        this.state = state;
        this.timeoutMillis = timeoutMillis;
    }

    public void start(String brokerUrl) throws MessagingUnavailable {
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            factory.setWatchTopicAdvisories(false);
            connection = factory.createConnection();
            connection.start();
            session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(MqConfig.HEARTBEAT_QUEUE);
            consumer = session.createConsumer(queue);
            consumer.setMessageListener(msg -> {
                try {
                    if (msg instanceof TextMessage text) {
                        lastSeen.set(System.currentTimeMillis());
                        state.markUp();
                    }
                } catch (Exception e) {
                    log.warn("Unreadable heartbeat: {}", e.getMessage());
                }
            });
            lastSeen.set(System.currentTimeMillis());
            monitor = new Thread(this::monitorLoop, "heartbeat-monitor");
            monitor.setDaemon(true);
            monitor.start();
        } catch (Exception e) {
            close();
            throw new MessagingUnavailable("Could not consume from " + MqConfig.HEARTBEAT_QUEUE + ": " + e.getMessage(), e);
        }
    }

    private void monitorLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1000);
                if (System.currentTimeMillis() - lastSeen.get() > timeoutMillis) {
                    state.markDown();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void close() {
        if (monitor != null) {
            monitor.interrupt();
        }
        try { if (consumer != null) consumer.close(); } catch (Exception ignored) {}
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
    }
}
