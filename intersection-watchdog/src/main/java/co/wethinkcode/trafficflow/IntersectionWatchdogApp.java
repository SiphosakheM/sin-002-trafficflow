package co.wethinkcode.trafficflow;

import co.wethinkcode.trafficflow.mq.MessagingUnavailable;
import co.wethinkcode.trafficflow.mq.MqConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntersectionWatchdogApp {

    private static final Logger log = LoggerFactory.getLogger(IntersectionWatchdogApp.class);
    private static final int PORT = 7024;

    public static void main(String[] args) {
        WatchdogState state = new WatchdogState();
        HeartbeatConsumer consumer = new HeartbeatConsumer(new ObjectMapper(), state, 15000);
        try {
            consumer.start(MqConfig.BROKER_URL);
        } catch (MessagingUnavailable e) {
            log.warn("Could not start heartbeat consumer: {}", e.getMessage());
        }

        Javalin app = Javalin.create().start(PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(consumer::close));

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/status", ctx -> ctx.json(state.asMap()));
        app.get("/alert", ctx -> ctx.json(state.asMap()));
    }
}
