package co.wethinkcode.trafficflow.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

public record HeartbeatMessage(String service, String instance, String at) {

    public HeartbeatMessage {
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("service must not be blank");
        }
        if (instance == null || instance.isBlank()) {
            throw new IllegalArgumentException("instance must not be blank");
        }
    }

    public static HeartbeatMessage now(String service, String instance) {
        return new HeartbeatMessage(service, instance, Instant.now().toString());
    }

    public String asJson(ObjectMapper json) {
        try {
            return json.writeValueAsString(new Body(service, instance, at));
        } catch (Exception e) {
            throw new IllegalStateException("Could not write the heartbeat message as json.", e);
        }
    }

    public static HeartbeatMessage fromJson(String body, ObjectMapper json) {
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("The heartbeat message was not json: " + body, e);
        }
        if (root == null || !root.has("service") || !root.get("service").isTextual()
                || !root.has("instance") || !root.get("instance").isTextual()
                || !root.has("at") || !root.get("at").isTextual()) {
            throw new IllegalArgumentException("The heartbeat message was missing fields: " + body);
        }
        return new HeartbeatMessage(root.get("service").asText(), root.get("instance").asText(), root.get("at").asText());
    }

    private record Body(String service, String instance, String at) {
    }
}
