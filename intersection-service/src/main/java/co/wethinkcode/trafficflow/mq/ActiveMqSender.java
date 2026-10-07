package co.wethinkcode.trafficflow.mq;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTextMessage;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * Sends messages to a real ActiveMQ broker.
 *
 * The connection is made once and kept, because opening a connection per
 * message is slow and the broker will start refusing us. If a send fails, the
 * broken connection is thrown away so the next attempt makes a fresh one —
 * that is what lets this recover when the broker comes back.
 */
public class ActiveMqSender implements MessageSender {

    private final String brokerUrl;

    private ActiveMQConnectionFactory factory;
    private Connection connection;
    private Session session;

    public ActiveMqSender(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    /**
     * Sends one message body to a destination.
     *
     * @throws MessagingUnavailable when the broker could not be reached or would
     *         not take the message. The broken connection is dropped first, so
     *         the next call starts from scratch.
     */
    @Override
    public void send(String destination, String body) throws MessagingUnavailable {
        try {
            MessageProducer producer = connectedSession().createProducer(((destination != null && destination.toLowerCase().contains("queue")) ? connectedSession().createQueue(destination) : connectedSession().createTopic(destination)));
            try {
                producer.send(session.createTextMessage(body));
            } finally {
                producer.close();
            }
        } catch (Exception e) {
            // Whatever went wrong, do not keep a connection that is broken.
            drop();
            throw new MessagingUnavailable(
                    "Could not send to " + destination + " on the broker at " + brokerUrl
                            + ": " + e.getMessage(), e);
        }
    }

    /**
     * The session we will use, opening a connection if we do not have one.
     */
    private Session connectedSession() throws Exception {
        if (session == null) {
            factory = new ActiveMQConnectionFactory(brokerUrl);
            // Let the client notice a dead broker on its own instead of waiting
            // for a send to time out.
            factory.setWatchTopicAdvisories(false);
            connection = factory.createConnection();
            connection.start();
            session = connection.createSession(Session.AUTO_ACKNOWLEDGE);
        }
        return session;
    }

    /**
     * Throws away the connection so the next send makes a new one.
     */
    private void drop() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception ignored) {
            // We are already in trouble; failing to tidy up changes nothing.
        }
        connection = null;
        session = null;
        factory = null;
    }

    /** Closes the connection if we have one. Safe to call more than once. */
    public void close() {
        drop();
    }
}