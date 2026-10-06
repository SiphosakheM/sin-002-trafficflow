# common — Asynchronous Decoupling (MQ)

## Overview

Part of the [TrafficFlow](../README.md) project. Holds the ActiveMQ broker shared
by the services below — not a service itself, so it has no port of its own. Two
independent integration points share this one broker:

### Topic: `congestion-topic`

Routing Service becomes aware of congestion changes via an ActiveMQ Topic instead of querying Congestion Service directly.

- Producer: `congestion-service` (`../congestion-service`)
- Consumer(s): `routing-service` (`../routing-service`)

Broker URL and topic name are shared via a common `co.wethinkcode.trafficflow.mq.MqConfig` class
(`BROKER_URL`, `TOPIC`). It's identical in every participating service's own source
tree — each service here is an independent Maven project with no shared parent pom,
so the common package is duplicated rather than imported from one place.

### Queue: `intersection-heartbeat-queue`

Intersection Watchdog notices when Intersection Service goes down by watching for
missed heartbeats / dead-lettered messages instead of polling its `/health` endpoint.

- Producer: `intersection-service` (`../intersection-service`)
- Consumer(s): `intersection-watchdog` (`../intersection-watchdog`)

Broker URL and queue name are shared the same way, via each service's own copy of
`co.wethinkcode.trafficflow.mq.MqConfig` (`BROKER_URL`, `HEARTBEAT_QUEUE`).

## Project structure

```
common/
├── docker-compose.yml
└── README.md
```

This folder holds the broker config and notes only. The publish/subscribe code
lives in the services listed above, each with its own
`src/main/java/co/wethinkcode/trafficflow/mq/MqConfig.java` carrying `BROKER_URL`
and `TOPIC` — the constants are duplicated per module because these are separate
Maven projects with no shared parent, and a shared jar would be a dependency
between services that are meant to be independent.

**Stage 3 is implemented:**

- `congestion-service` publishes `{"level": n}` to `congestion-topic` on every
  level change (`mq/CongestionPublisher` → `mq/ActiveMqSender`).
- `routing-service` subscribes to the same topic and keeps the last level
  (`TopicCongestionLookup`), instead of polling `GET /congestion`.

## Build

Nothing to build here directly — this folder just brings up the broker used by the
services listed above.

## Run

```
docker compose up -d
```

- Broker URL for clients: `tcp://localhost:61616`
- Web console: http://localhost:8161 (default admin/admin)

Then start the producer/consumer services as usual (`mvn package && java -jar ...`
from their own directories at the project root).

## Test

```
docker compose ps          # confirm the broker container is healthy
```

Verify end-to-end by changing the congestion level and confirming routing follows
it — no http call to 7022 appears in routing's log after the startup seed:

```bash
curl -X POST localhost:7022/congestion -H 'Content-Type: application/json' -d '{"level":8}'
sleep 2
curl -X POST localhost:7023/route -H 'Content-Type: application/json' \
  -d '{"from":"INT-1001","to":"INT-1004","distanceKm":10}'
# minutes: 15 -> 46
```

The web console (http://localhost:8161) shows the topic with its subscribers, if
you want to see the message rather than its effect.

## TODO

- [x] Add `activemq-client` publish logic to `congestion-service` on its stage/state-change endpoint.
- [x] Add `activemq-client` subscriber logic to consumer service(s) above, replacing any
  direct synchronous calls to `congestion-service`.
- [ ] Add `activemq-client` heartbeat-publish logic to `intersection-service` *(stage 4)*.
- [ ] Add `activemq-client` subscriber/alerting logic to `intersection-watchdog` *(stage 4)*.
