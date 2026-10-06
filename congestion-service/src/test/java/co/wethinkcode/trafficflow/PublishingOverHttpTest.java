package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.CongestionPublisher;
import co.wethinkcode.trafficflow.mq.MessageSender;
import co.wethinkcode.trafficflow.mq.MessagingUnavailable;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the real service over http and checks a message goes out each time the
 * level changes.
 *
 * This is the production wiring — {@code createApp(tracker, publisher)} — being
 * used exactly as {@code main} uses it, with only the broker faked.
 */
class PublishingOverHttpTest {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private RecordingSender sender;
    private CongestionTracker tracker;
    private Javalin app;

    /** Stands in for ActiveMQ and remembers what it was asked to send. */
    private static class RecordingSender implements MessageSender {
        final List<String> bodies = new ArrayList<>();

        @Override
        public void send(String destination, String body) {
            bodies.add(body);
        }
    }

    @BeforeEach
    void startTheService() {
        sender = new RecordingSender();
        tracker = new CongestionTracker(CongestionLevel.of(0));
        tracker.historyLimit(100);
        app = CongestionServiceApp
                .createApp(tracker, new CongestionPublisher(sender, json))
                .start(0);
    }

    @AfterEach
    void stopTheService() {
        app.stop();
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return http.send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void settingTheLevelOverHttpPublishesAMessage() throws Exception {
        assertEquals(200, post("/congestion", "{\"level\":5}").statusCode());

        assertEquals(List.of("{\"level\":5,\"label\":\"Moderate\"}"), sender.bodies);
    }

    @Test
    void steppingTheLevelOverHttpPublishesAMessage() throws Exception {
        assertEquals(200, post("/congestion", "{\"level\":5}").statusCode());
        assertEquals(200, post("/congestion/step", "{\"direction\":\"up\"}").statusCode());

        assertEquals(List.of(
                "{\"level\":5,\"label\":\"Moderate\"}",
                "{\"level\":6,\"label\":\"Heavy\"}"),
                sender.bodies);
    }

    @Test
    void startingTheServicePublishesNothing() {
        // The level is 0 when we start, and that is not a change.
        assertTrue(sender.bodies.isEmpty());
    }

    @Test
    void aRequestThatChangesNothingPublishesNothing() throws Exception {
        post("/congestion", "{\"level\":4}");
        sender.bodies.clear();

        post("/congestion", "{\"level\":4}");

        assertTrue(sender.bodies.isEmpty());
    }

    @Test
    void aRejectedRequestPublishesNothing() throws Exception {
        post("/congestion", "{\"level\":9}");
        post("/congestion", "nonsense");

        assertTrue(sender.bodies.isEmpty());
    }

    @Test
    void historyAndTheTopicAgreeOnWhatHappened() throws Exception {
        post("/congestion", "{\"level\":6}");

        // One change: one message on the topic and one new history entry.
        assertEquals(1, sender.bodies.size());
        assertEquals(6, tracker.currentLevel());
        assertEquals(6, tracker.history().get(0).level().value());
        assertEquals("set", tracker.history().get(0).reason());
    }

    @Test
    void oneHttpRequestMeansOneMessageWhateverTheBody() throws Exception {
        post("/congestion", "{\"level\":3}");
        post("/congestion", "{\"level\":5}");
        post("/congestion/step", "{\"direction\":\"up\"}");

        assertEquals(3, sender.bodies.size());
        assertEquals("{\"level\":3,\"label\":\"Light\"}", sender.bodies.get(0));
        assertEquals("{\"level\":5,\"label\":\"Moderate\"}", sender.bodies.get(1));
        assertEquals("{\"level\":6,\"label\":\"Heavy\"}", sender.bodies.get(2));
    }
}