package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntersectionCatalogueTest {

    // A feed that always works and gives two records.
    private static IntersectionFeed workingFeed() {
        return () -> List.of(
                new Intersection("INT-1001", "Downtown", "4-way", true),
                new Intersection("INT-1002", "Midtown", "pedestrian", true));
    }

    // A feed that never works, like the ingestion service being down.
    private static IntersectionFeed brokenFeed() {
        return () -> {
            throw new IngestionUnavailableException("the ingestion service is down");
        };
    }

    @Test
    void startsWithNothingAndSaysSo() {
        IntersectionCatalogue catalogue = new IntersectionCatalogue(workingFeed());

        assertEquals(0, catalogue.size());
        assertEquals(IntersectionCatalogue.State.DEGRADED, catalogue.status().state());
        assertTrue(catalogue.status().message().contains("not been read"));
    }

    @Test
    void aGoodReadMakesItReady() throws Exception {
        IntersectionCatalogue catalogue = new IntersectionCatalogue(workingFeed());

        catalogue.refresh();

        assertEquals(IntersectionCatalogue.State.READY, catalogue.status().state());
        assertEquals(2, catalogue.size());
    }

    @Test
    void aBadReadMakesItDegraded() {
        IntersectionCatalogue catalogue = new IntersectionCatalogue(brokenFeed());

        catalogue.refresh();

        assertEquals(IntersectionCatalogue.State.DEGRADED, catalogue.status().state());
        assertEquals(0, catalogue.size());
    }

    @Test
    void theMessageSaysWhatWentWrong() {
        IntersectionCatalogue catalogue = new IntersectionCatalogue(brokenFeed());

        catalogue.refresh();

        assertTrue(catalogue.status().message().contains("the ingestion service is down"));
    }

    @Test
    void itCanRecoverOnASecondRead() {
        AtomicInteger calls = new AtomicInteger();
        IntersectionFeed flaky = () -> {
            if (calls.incrementAndGet() == 1) {
                throw new IngestionUnavailableException("down on the first try");
            }
            return List.of(new Intersection("INT-1001", "Downtown", "4-way", true));
        };
        IntersectionCatalogue catalogue = new IntersectionCatalogue(flaky);

        catalogue.refresh();
        assertEquals(IntersectionCatalogue.State.DEGRADED, catalogue.status().state());

        catalogue.refresh();
        assertEquals(IntersectionCatalogue.State.READY, catalogue.status().state());
        assertEquals(1, catalogue.size());
    }

    @Test
    void aFailedReadKeepsTheOldRecordsInsteadOfThrowingThemAway() {
        AtomicInteger calls = new AtomicInteger();
        IntersectionFeed flaky = () -> {
            if (calls.incrementAndGet() == 1) {
                return List.of(new Intersection("INT-1001", "Downtown", "4-way", true));
            }
            throw new IngestionUnavailableException("down on the second try");
        };
        IntersectionCatalogue catalogue = new IntersectionCatalogue(flaky);

        catalogue.refresh();
        catalogue.refresh();

        // We would rather serve old data than no data at all.
        assertEquals(1, catalogue.size());
        assertEquals(IntersectionCatalogue.State.DEGRADED, catalogue.status().state());
    }

    @Test
    void anEmptyListFromTheFeedStillCountsAsReady() throws Exception {
        IntersectionCatalogue catalogue = new IntersectionCatalogue(() -> List.of());

        catalogue.refresh();

        assertEquals(IntersectionCatalogue.State.READY, catalogue.status().state());
        assertEquals(0, catalogue.size());
    }

    @Test
    void anUnexpectedErrorIsAlsoReportedAndNotThrown() {
        IntersectionFeed broken = () -> {
            throw new IllegalStateException("something went very wrong");
        };
        IntersectionCatalogue catalogue = new IntersectionCatalogue(broken);

        catalogue.refresh();

        assertEquals(IntersectionCatalogue.State.DEGRADED, catalogue.status().state());
        assertTrue(catalogue.status().message().contains("something went very wrong"));
    }

    @Test
    void looksUpAnIntersectionById() throws Exception {
        IntersectionCatalogue catalogue = new IntersectionCatalogue(workingFeed());
        catalogue.refresh();

        assertTrue(catalogue.registry().isKnown("INT-1001"));
        assertFalse(catalogue.registry().isKnown("INT-9999"));
    }

    @Test
    void keepsAnEmptyRegistryWhenTheFirstReadFails() {
        IntersectionCatalogue catalogue = new IntersectionCatalogue(brokenFeed());

        catalogue.refresh();

        assertTrue(catalogue.registry().all().isEmpty());
        assertTrue(catalogue.registry().districts().isEmpty());
    }
}