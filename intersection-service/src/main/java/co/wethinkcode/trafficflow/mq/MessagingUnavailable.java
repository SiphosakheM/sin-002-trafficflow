package co.wethinkcode.trafficflow.mq;

/**
 * We could not send a message, because the broker was not there or would not
 * take it.
 *
 * Its own type so the service can tell "the broker is down" apart from a bug in
 * our own code. The first is worth logging loudly and carrying on; the second
 * means the level should probably not change.
 */
public class MessagingUnavailable extends Exception {

    public MessagingUnavailable(String message) {
        super(message);
    }

    public MessagingUnavailable(String message, Throwable cause) {
        super(message, cause);
    }
}