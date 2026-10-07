# Stage 4 Documentation - Heartbeat & Watchdog Alerting

## Objective
Implement stage 4: Add heartbeat/dead-letter alerting in IntersectionWatchdogApp so it notices when the Intersection Service goes down. Specifically, intersection-service must publish heartbeats to intersection-heartbeat-queue, and intersection-watchdog must detect missed heartbeats and surface an alert.

## Changes Made

### 1. congestion-service/src/main/java/co/wethinkcode/trafficflow/mq/ActiveMqSender.java (modified)
- Updated to detect if destination name contains "queue" (case-insensitively) and create a Queue destination; otherwise create a Topic. This allows the same sender to work for both topics (congestion-topic) and queues (intersection-heartbeat-queue).
- Why: Stage 3 used topics; Stage 4 uses queues. Reusing the same MessageSender/ActiveMqSender abstraction keeps code consistent.

### 2. intersection-service/src/main/java/co/wethinkcode/trafficflow/mq/ (new files)
- ActiveMqSender.java (copied/compatible version) - sends messages to broker (creates Queue if destination name contains "queue").
- MessageSender.java, MessagingUnavailable.java - interfaces/exceptions for sending messages (same pattern as congestion-service).
- HeartbeatMessage.java - record representing heartbeat payload with fields: service, instance, at (ISO timestamp). Provides asJson/fromJson using Jackson.
- HeartbeatPublisher.java - publishes HeartbeatMessage to MqConfig.HEARTBEAT_QUEUE; tracks sentCount and lastInstance. Throws MessagingUnavailable on failure.
- HeartbeatScheduler.java - scheduled executor that periodically publishes heartbeats (default every 5 seconds) while running; starts immediately (scheduleAtFixedRate with initial delay 0). Runs as daemon thread; close() shuts down executor. Creates default instance using ActiveMqSender + ObjectMapper.
- Heartbeat.java - marker class (no-op).

Why: Need to periodically publish heartbeats from intersection-service to intersection-heartbeat-queue so watchdog can detect liveness. Following same MQ abstraction as congestion-service.

### 3. intersection-service/src/main/java/co/wethinkcode/trafficflow/IntersectionServiceApp.java (modified)
- On startup: create HeartbeatScheduler.createDefault(), call start(), and register shutdown hook to close() on JVM shutdown.
- Why: Heartbeats must be sent for as long as intersection-service is running; clean shutdown avoids dangling threads.

### 4. intersection-watchdog/pom.xml (modified)
- Added jackson-databind dependency (2.17.2). 
- Why: Watchdog needs to parse heartbeat JSON messages (same as other services).

### 5. intersection-watchdog/src/main/java/co/wethinkcode/trafficflow/mq/ (new files)
- ActiveMqSender.java, MessageSender.java, MessagingUnavailable.java - for sending (if needed); also ActiveMqReceiver.java provided as alternative receiver pattern.
- HeartbeatMessage.java - same heartbeat message structure for parsing incoming heartbeats.

### 6. intersection-watchdog/src/main/java/co/wethinkcode/trafficflow/ (new files)
- WatchdogState.java - holds alert state (intersectionDown flag, lastAlertAt). Exposes asMap() with fields intersectionDown, alertActive (same as intersectionDown), and lastAlertAt when set. 
- HeartbeatListener.java - JMS MessageListener that updates lastSeen and marks state up on receiving valid heartbeat; logs debug on success, warns on unreadable messages.
- HeartbeatMonitor.java - alternative monitor implementation that listens and also has timeout monitoring thread; updates state on alert/receipt. (Retained as additional implementation; app uses HeartbeatConsumer.)
- HeartbeatConsumer.java - JMS consumer that listens on MqConfig.HEARTBEAT_QUEUE, records lastSeen timestamp on each heartbeat, starts daemon monitoring thread that checks timeout every second. If no heartbeat within timeoutMillis (default 15000ms), marks state down. On receipt, marks state up. Provides close() to clean up resources/threads.
- Why: Need to detect missed heartbeats. Consumer pattern with timeout check is straightforward - if watchdog stops receiving heartbeats for > timeout, intersection-service is considered down and alert is raised.

### 7. intersection-watchdog/src/main/java/co/wethinkcode/trafficflow/IntersectionWatchdogApp.java (modified)
- Starts HeartbeatConsumer (with ObjectMapper, WatchdogState, 15000ms timeout) and subscribes to MqConfig.BROKER_URL / MqConfig.HEARTBEAT_QUEUE. On MessagingUnavailable, logs warning but continues (service still serves HTTP endpoints).
- Registers shutdown hook to close consumer.
- Exposes endpoints: /health (OK), /status (returns watchdog state), /alert (returns watchdog state).
- Why: Surfaces alert state via HTTP endpoints as required; state reflects whether intersection is down due to missed heartbeats.

## How it works
1. IntersectionServiceApp starts HeartbeatScheduler which publishes {"service":"intersection-service","instance":<uuid>,"at":<timestamp>} to intersection-heartbeat-queue every 5 seconds via ActiveMQ.
2. Watchdog listens on the same queue; on each message received, updates lastSeen and sets alert state to UP.
3. Watchdog monitors lastSeen; if no heartbeat for > 15 seconds, sets alert state to DOWN (intersectionDown/alertActive true). Logs an alert message.
4. /status and /alert return the current state so it’s observable.

## Configuration
- Heartbeat interval (intersection-service): 5000ms (default in HeartbeatScheduler)
- Heartbeat timeout (watchdog): 15000ms (default in HeartbeatConsumer/HeartbeatMonitor)
- Queue name: MqConfig.HEARTBEAT_QUEUE = "intersection-heartbeat-queue"
- Broker: MqConfig.BROKER_URL = "tcp://localhost:61616"

## Files edited/added
- Modified: congestion-service/mq/ActiveMqSender.java (queue/topic detection)
- Modified: intersection-service/IntersectionServiceApp.java (start scheduler)
- Modified: intersection-watchdog/pom.xml (jackson)
- Modified: intersection-watchdog/IntersectionWatchdogApp.java (consumer + endpoints)
- Added (intersection-service/mq): ActiveMqSender, MessageSender, MessagingUnavailable, HeartbeatMessage, HeartbeatPublisher, HeartbeatScheduler, Heartbeat
- Added (intersection-watchdog): HeartbeatConsumer, HeartbeatListener, HeartbeatMonitor, WatchdogState, mq/ActiveMqSender, mq/MessageSender, mq/MessagingUnavailable, mq/ActiveMqReceiver, mq/HeartbeatMessage

All changes compile and existing tests still pass. Broker must be running for heartbeat messages to be sent/received.
