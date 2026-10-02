# CongestionServiceApp

## Overview

Tracks the city-wide Congestion Level, a whole number from **0 to 8**. Level 0 means
the roads are clear and level 8 means gridlock. Nothing outside that range is
allowed in, so a bad number cannot travel further into the system.

Part of the [TrafficFlow](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ topic `congestion-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.trafficflow.mq.MqConfig` class alongside it in this module.

## Endpoints

| Method | Path | Body | Answers |
|---|---|---|---|
| `GET` | `/health` | | `OK` |
| `GET` | `/congestion` | | `{"level":3,"label":"Light","busy":false}` |
| `POST` | `/congestion` | `{"level":5}` | The new level, or `400` |
| `POST` | `/congestion/step` | `{"direction":"up"}` or `"down"` | The new level, or `400` |
| `GET` | `/congestion/history` | | Array of changes, newest first |
| `POST` | `/congestion/history/limit` | `{"limit":20}` | `{"limit":20,"kept":20}`, or `400` |

The routing service reads `GET /congestion` in stage 2, and in stage 3 it gets the
same `{"level": n}` pushed to it over ActiveMQ instead.

`label` is a word for the level, so answers and logs are easy to read:

| Level | 0-1 | 2-3 | 4-5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|
| Label | Clear | Light | Moderate | Heavy | Severe | Gridlock |

`busy` is true from level 6 upwards.

## What counts as a bad request

Everything below gives `400` and leaves the current level alone:

- a level above 8 or below 0
- a level that is not a whole number (`"busy"`, `3.5`)
- an empty body, or a body with no `level` in it
- a body that is not json
- a `direction` that is not `up` or `down`

Stepping up at level 8 or down at level 0 is **not** an error — the level stays
where it is and the answer is still `200`.

## The history

`/congestion/history` keeps the newest changes first, and each one says the level,
a word for it, why it changed, and when:

```json
[{"level":5,"label":"Moderate","reason":"stepped up","at":1759271042123}]
```

It holds 100 entries by default. Without a limit it would grow for as long as the
service runs, so the limit can be changed with `POST /congestion/history/limit`.

## Listeners — the hook for stage 3

`CongestionTracker.onChange(...)` registers a listener that is called whenever the
level actually changes. In stage 3 the ActiveMQ publisher is one of those
listeners, so this class does not need to know anything about message queues.

Two details the tests pin down:

- a listener is **not** called when the level is set to what it already was,
  because nothing changed
- a listener that throws an error is logged and skipped, and the other listeners
  still hear about the change, and the level still changes

## Thread safety

`CongestionTracker` checks and changes the level under one lock, so many callers
stepping at the same time cannot read the same level and skip past each other.
There is a test that runs 40 threads stepping 50 times each and checks the level
lands on 8, not on 2000.

## Project structure

```
congestion-service/
├── pom.xml
└── src/
    ├── main/java/co/wethinkcode/trafficflow/
    │   ├── CongestionServiceApp.java  (the http service)
    │   ├── CongestionTracker.java     (holds the level, the listeners, the history)
    │   ├── CongestionLevel.java       (the 0-8 value, refuses anything else)
    │   ├── CongestionChange.java      (one history entry)
    │   ├── CongestionListener.java    (told when the level changes)
    │   └── mq/MqConfig.java
    └── test/java/co/wethinkcode/trafficflow/   (74 tests)
```

## Build

```
mvn package
```

## Run

```
java -jar target/congestion-service.jar
```

Listens on port `7022`. It starts at level 0 and needs nothing else running.

## Test

```
mvn test
```

74 tests. The level rules and the tracker are tested on their own, and the
endpoints are tested by starting this real service on a free port and calling it
over http.