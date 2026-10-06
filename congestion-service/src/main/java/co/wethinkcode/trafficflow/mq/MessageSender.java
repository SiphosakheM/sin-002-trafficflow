package co.wethinkcode.trafficflow.mq;

/**
 * Puts a message somewhere over a broker.
 *
 * An interface so the publisher can be tested without a broker running. In
 * production this is {@link ActiveMqSender}; the tests use a fake that just
 * remembers what it was asked to send.
 */
public interface MessageSender {

    /**
     * Sends one message body to a destination.
     *
     * @throws MessagingUnavailable when the broker could not be reached or would
     *         not take the message.
     */
    void send(String destination, String body) throws MessagingUnavailable;
}