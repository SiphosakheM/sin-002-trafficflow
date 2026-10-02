package co.wethinkcode.trafficflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Keeps the city-wide congestion level and who has to hear about it changing.
 *
 * The "listeners" are the important part. When the level changes we tell every
 * listener, and in stage 3 the ActiveMQ publisher is one of those listeners. That
 * way this class does not need to know anything about message queues.
 */
public class CongestionTracker {

    private static final Logger log = LoggerFactory.getLogger(CongestionTracker.class);

    // How many changes we keep for the /congestion/history answer.
    private static final int DEFAULT_HISTORY_LIMIT = 100;

    // The level we are on right now.
    private volatile CongestionLevel current;

    // Everyone who wants to hear about a change.
    private final List<CongestionListener> listeners = new CopyOnWriteArrayList<>();

    // The newest change is at the front. We stop adding when it is full.
    private final Deque<CongestionChange> history = new ArrayDeque<>();

    private volatile int historyLimit = DEFAULT_HISTORY_LIMIT;

    /**
     * Starts at the given level.
     */
    public CongestionTracker(CongestionLevel startingLevel) {
        this.current = startingLevel;
        remember(startingLevel, "start up");
    }

    /**
     * Starts at level 0, clear roads.
     */
    public CongestionTracker() {
        this(CongestionLevel.of(CongestionLevel.LOWEST));
    }

    /**
     * Sets the level.
     *
     * Setting the level it is already on does nothing and tells nobody, because
     * nothing happened. A level outside 0 to 8 is refused, and the old level is
     * kept.
     */
    public synchronized void set(CongestionLevel level) {
        moveTo(level, "set");
    }

    /**
     * Moves the level up one. Steps above 8 do nothing.
     */
    public synchronized void stepUp() {
        moveTo(current.up(), "stepped up");
    }

    /**
     * Moves the level down one. Steps below 0 do nothing.
     */
    public synchronized void stepDown() {
        moveTo(current.down(), "stepped down");
    }

    /**
     * The level right now.
     */
    public CongestionLevel current() {
        return current;
    }

    /**
     * The level as a plain number, which is what the other services read.
     */
    public int currentLevel() {
        return current.value();
    }

    /**
     * Asks to be told whenever the level changes.
     */
    public void onChange(CongestionListener listener) {
        listeners.add(listener);
    }

    /**
     * The changes, newest first. The list is a copy, so nobody outside can
     * change our history.
     */
    public List<CongestionChange> history() {
        synchronized (this) {
            return new ArrayList<>(history);
        }
    }

    /**
     * How many changes to keep. Zero means keep none. Without a limit the
     * history would grow for as long as the service runs.
     */
    public synchronized void historyLimit(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("The history limit cannot be negative, but was " + limit);
        }
        this.historyLimit = limit;
        trimHistory();
    }

    /**
     * Changes the level and tells everyone who is listening.
     * We hold the lock while we change it, so two callers stepping at the same
     * time cannot both read the same level and skip past each other.
     */
    private void moveTo(CongestionLevel level, String reason) {
        if (level.value() == current.value()) {
            return;
        }
        CongestionLevel previous = current;
        current = level;
        remember(level, reason);

        log.info("Congestion level moved from {} ({}) to {} ({})",
                previous.value(), previous.label(), level.value(), level.label());

        for (CongestionListener listener : listeners) {
            try {
                listener.levelChanged(level, previous);
            } catch (RuntimeException e) {
                // A listener that is broken must not stop the level changing,
                // and must not stop the other listeners from hearing about it.
                log.error("A congestion listener threw an error: {}", e.getMessage(), e);
            }
        }
    }

    // Puts a change at the front of the history and drops the oldest if needed.
    private void remember(CongestionLevel level, String reason) {
        history.addFirst(new CongestionChange(level, reason, System.currentTimeMillis()));
        trimHistory();
    }

    private void trimHistory() {
        while (history.size() > historyLimit) {
            history.removeLast();
        }
    }
}