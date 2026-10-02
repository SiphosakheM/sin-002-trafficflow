package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CongestionTrackerTest {

    private CongestionTracker tracker;

    @BeforeEach
    void makeATracker() {
        tracker = new CongestionTracker(CongestionLevel.of(3));
    }

    @Test
    void startsAtTheLevelWeGaveIt() {
        assertEquals(3, tracker.current().value());
    }

    @Test
    void startsAtClearWhenWeDoNotSay() {
        assertEquals(0, new CongestionTracker().current().value());
    }

    @Test
    void settingALevelChangesIt() {
        tracker.set(CongestionLevel.of(6));

        assertEquals(6, tracker.current().value());
    }

    @Test
    void aLevelOutsideTheRangeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> tracker.set(CongestionLevel.of(9)));
    }

    @Test
    void aRefusedLevelLeavesTheOldOneAlone() {
        assertThrows(IllegalArgumentException.class, () -> tracker.set(CongestionLevel.of(-2)));

        assertEquals(3, tracker.current().value());
    }

    @Test
    void steppingUpMovesOneLevel() {
        tracker.stepUp();

        assertEquals(4, tracker.current().value());
    }

    @Test
    void steppingDownMovesOneLevel() {
        tracker.stepDown();

        assertEquals(2, tracker.current().value());
    }

    @Test
    void steppingUpAtTheTopStaysAtTheTop() {
        tracker.set(CongestionLevel.of(8));

        tracker.stepUp();

        assertEquals(8, tracker.current().value());
    }

    @Test
    void steppingDownAtTheBottomStaysAtTheBottom() {
        tracker.set(CongestionLevel.of(0));

        tracker.stepDown();

        assertEquals(0, tracker.current().value());
    }

    @Test
    void stepsAreSafeFromManyThreadsAtOnce() throws Exception {
        // 40 threads each stepping up 50 times would go past 8 if the level was
        // not checked and changed in one go.
        tracker.set(CongestionLevel.of(0));
        int threads = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }
                for (int step = 0; step < 50; step++) {
                    tracker.stepUp();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertEquals(8, tracker.current().value());
    }

    @Test
    void tellsAListenerWhenTheLevelChanges() {
        List<CongestionLevel> heard = new ArrayList<>();
        tracker.onChange((newLevel, oldLevel) -> heard.add(newLevel));

        tracker.set(CongestionLevel.of(5));

        assertEquals(List.of(CongestionLevel.of(5)), heard);
    }

    @Test
    void aListenerIsNotCalledWhenNothingChanges() {
        List<CongestionLevel> heard = new ArrayList<>();
        tracker.onChange((newLevel, oldLevel) -> heard.add(newLevel));

        tracker.set(CongestionLevel.of(3));   // same level as now

        assertTrue(heard.isEmpty(), "nothing changed, so nothing should be sent out");
    }

    @Test
    void aStepThatDoesNotMoveAtTheTopDoesNotTellAnyone() {
        tracker.set(CongestionLevel.of(8));
        List<CongestionLevel> heard = new ArrayList<>();
        tracker.onChange((newLevel, oldLevel) -> heard.add(newLevel));

        tracker.stepUp();

        assertTrue(heard.isEmpty());
    }

    @Test
    void aStepAtTheBottomThatDoesNotMoveDoesNotTellAnyone() {
        tracker.set(CongestionLevel.of(0));
        List<CongestionLevel> heard = new ArrayList<>();
        tracker.onChange((newLevel, oldLevel) -> heard.add(newLevel));

        tracker.stepDown();

        assertTrue(heard.isEmpty());
    }

    @Test
    void everyChangeIsSentOutInOrder() {
        List<CongestionLevel> heard = new ArrayList<>();
        tracker.onChange((newLevel, oldLevel) -> heard.add(newLevel));

        tracker.set(CongestionLevel.of(4));
        tracker.stepUp();
        tracker.stepUp();
        tracker.stepDown();

        assertEquals(List.of(
                CongestionLevel.of(4),
                CongestionLevel.of(5),
                CongestionLevel.of(6),
                CongestionLevel.of(5)), heard);
    }

    @Test
    void twoListenersBothGetTheChange() {
        List<CongestionLevel> first = new ArrayList<>();
        List<CongestionLevel> second = new ArrayList<>();
        tracker.onChange((newLevel, oldLevel) -> first.add(newLevel));
        tracker.onChange((newLevel, oldLevel) -> second.add(newLevel));

        tracker.set(CongestionLevel.of(7));

        assertEquals(1, first.size());
        assertEquals(1, second.size());
    }

    @Test
    void oneBrokenListenerDoesNotStopTheOthers() {
        List<CongestionLevel> heard = new ArrayList<>();
        tracker.onChange((level, oldLevel) -> {
            throw new IllegalStateException("this listener is broken");
        });
        tracker.onChange((newLevel, oldLevel) -> heard.add(newLevel));

        tracker.set(CongestionLevel.of(2));   // must not throw

        assertEquals(1, heard.size());
    }

    @Test
    void aListenerThatThrowsDoesNotStopTheLevelChanging() {
        tracker.onChange((level, oldLevel) -> {
            throw new IllegalStateException("this listener is broken");
        });

        tracker.set(CongestionLevel.of(2));   // must not throw

        assertEquals(2, tracker.current().value());
    }

    @Test
    void keepsTheHistoryOfChanges() {
        tracker.set(CongestionLevel.of(4));
        tracker.set(CongestionLevel.of(5));

        // Newest first: 5, then 4, then the level we started at.
        assertEquals(3, tracker.history().size());
        assertEquals(5, tracker.history().get(0).level().value());
        assertEquals(4, tracker.history().get(1).level().value());
    }

    @Test
    void theHistoryStartsWithTheLevelWeBeganAt() {
        assertEquals(3, tracker.history().get(0).level().value());
    }

    @Test
    void theHistoryDoesNotGrowWithoutAMax() {
        tracker.historyLimit(10);
        for (int i = 0; i < 50; i++) {
            tracker.stepUp();
            tracker.stepDown();
        }

        assertEquals(10, tracker.history().size());
    }

    @Test
    void theHistoryKeepsTheNewestEntries() {
        tracker.historyLimit(3);
        tracker.set(CongestionLevel.of(1));
        tracker.set(CongestionLevel.of(2));
        tracker.set(CongestionLevel.of(4));

        List<CongestionChange> history = tracker.history();

        // Newest first, and the oldest entry (the start up one) was dropped.
        assertEquals(3, history.size());
        assertEquals(4, history.get(0).level().value());
        assertEquals(2, history.get(1).level().value());
        assertEquals(1, history.get(2).level().value());
    }

    @Test
    void aChangeKnowsWhenItHappened() {
        long before = System.currentTimeMillis();

        tracker.set(CongestionLevel.of(6));

        CongestionChange change = tracker.history().get(0);
        assertTrue(change.at() >= before);
    }

    @Test
    void aChangeKnowsWhyItHappened() {
        tracker.stepUp();
        tracker.set(CongestionLevel.of(0));

        // Newest first, so the set we did last is at the front.
        assertEquals("set", tracker.history().get(0).reason());
        assertEquals("stepped up", tracker.history().get(1).reason());
    }

    @Test
    void theHistoryIsNewestFirstSoItIsEasyToRead() {
        tracker.set(CongestionLevel.of(5));

        assertEquals(5, tracker.history().get(0).level().value());
        assertEquals(3, tracker.history().get(1).level().value());
    }

    @Test
    void theHistoryIsACopySoNobodyCanChangeItFromOutside() {
        tracker.historyLimit(5);
        tracker.history().clear();

        assertEquals(1, tracker.history().size());
    }

    @Test
    void aHistoryLimitOfZeroMeansKeepNothing() {
        tracker.historyLimit(0);
        tracker.set(CongestionLevel.of(5));

        assertTrue(tracker.history().isEmpty());
    }

    @Test
    void aHistoryLimitCannotGoNegative() {
        assertThrows(IllegalArgumentException.class, () -> tracker.historyLimit(-1));
    }

    @Test
    void theLevelIsBusyWhenItSaysSo() {
        tracker.set(CongestionLevel.of(7));

        assertTrue(tracker.current().isBusy());
    }

    @Test
    void givesTheNumberStraightOutForTheOtherServices() {
        assertEquals(3, tracker.currentLevel());
    }
}