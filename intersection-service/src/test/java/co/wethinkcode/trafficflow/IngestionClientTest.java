package co.wethinkcode.trafficflow;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks we can read the clean records from the ingestion service over http.
 * We start a small fake ingestion service so the test does not need the real
 * one to be running.
 */
class IngestionClientTest {

    private static Javalin fake;
    private static String fakeUrl;

    @BeforeAll
    static void startTheFakeIngestionService() {
        fake = Javalin.create().start(0);
        fakeUrl = "http://localhost:" + fake.port();

        // The real ingestion service answers with 17 records. We only need a few here.
        fake.get("/intersections", ctx -> ctx.json(List.of(
                new Intersection("INT-1001", "Downtown", "4-way", true),
                new Intersection("INT-1015", null, "4-way", true))));
    }

    @AfterAll
    static void stopTheFakeIngestionService() {
        fake.stop();
    }

    @Test
    void readsTheRecordsFromTheService() throws Exception {
        List<Intersection> records = new IngestionClient(fakeUrl).fetchAll();

        assertEquals(2, records.size());
    }

    @Test
    void keepsAllFourFields() throws Exception {
        List<Intersection> records = new IngestionClient(fakeUrl).fetchAll();

        Intersection first = records.get(0);
        assertEquals("INT-1001", first.id());
        assertEquals("Downtown", first.district());
        assertEquals("4-way", first.signalType());
        assertEquals(true, first.active());
    }

    @Test
    void keepsAMissingValueAsNull() throws Exception {
        List<Intersection> records = new IngestionClient(fakeUrl).fetchAll();

        assertNull(records.get(1).district());
    }

    @Test
    void anEmptyListIsStillAnEmptyList() throws Exception {
        Javalin empty = Javalin.create().start(0);
        empty.get("/intersections", ctx -> ctx.json(List.of()));

        List<Intersection> records = new IngestionClient("http://localhost:" + empty.port()).fetchAll();

        assertTrue(records.isEmpty());
        empty.stop();
    }

    @Test
    void aServiceThatAnswersWithAnErrorIsReported() {
        Javalin broken = Javalin.create().start(0);
        broken.get("/intersections", ctx -> ctx.status(503).result("not ready"));

        IngestionClient client = new IngestionClient("http://localhost:" + broken.port());

        IngestionUnavailableException error =
                assertThrows(IngestionUnavailableException.class, client::fetchAll);

        assertTrue(error.getMessage().contains("503"));
        broken.stop();
    }

    @Test
    void aServiceThatIsNotRunningIsReported() {
        // Start a server and stop it again, so we know the port is free.
        Javalin temp = Javalin.create().start(0);
        int deadPort = temp.port();
        temp.stop();

        IngestionClient client = new IngestionClient("http://localhost:" + deadPort);

        assertThrows(IngestionUnavailableException.class, client::fetchAll);
    }

    @Test
    void theErrorSaysWhichServiceWeCouldNotReach() {
        Javalin temp = Javalin.create().start(0);
        int deadPort = temp.port();
        temp.stop();

        IngestionClient client = new IngestionClient("http://localhost:" + deadPort);

        IngestionUnavailableException error =
                assertThrows(IngestionUnavailableException.class, client::fetchAll);

        assertTrue(error.getMessage().contains(String.valueOf(deadPort)));
    }

    @Test
    void aBadUrlIsReportedAndNotThrownAsSomethingElse() {
        IngestionClient client = new IngestionClient("not a url at all");

        assertThrows(IngestionUnavailableException.class, client::fetchAll);
    }

    @Test
    void jsonThatIsNotAnArrayIsReported() {
        Javalin wrong = Javalin.create().start(0);
        wrong.get("/intersections", ctx -> ctx.json("this is not a list"));

        IngestionClient client = new IngestionClient("http://localhost:" + wrong.port());

        assertThrows(IngestionUnavailableException.class, client::fetchAll);
        wrong.stop();
    }
}