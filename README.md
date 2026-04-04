# Hamdel — High-Throughput Log Ingestion Microservice

**Hamdel** is a high-throughput heartbeat ingestion pipeline built with Java 21, Spring Boot 3.3.5, and a Hexagonal (Ports & Adapters) Architecture.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           INBOUND ADAPTERS                              │
│  REST Controller (Virtual Threads)  │  Protobuf binary  │  JSON        │
└──────────────────────────┬──────────────────────────────────────────────┘
                           │
       ┌───────────────────▼───────────────────┐
       │         APPLICATION LAYER             │
       │  HeartbeatIngestionService            │
       │  KpiCalculationService                │
       └───────────────────┬───────────────────┘
                           │
         ┌─────────────────┼──────────────────────┐
         │                 │                      │
  ┌──────▼──────┐  ┌───────▼────────┐  ┌──────────▼────────┐
  │   Kafka     │  │ Resilience4j   │  │    PostgreSQL      │
  │  Producer   │  │ Circuit Breaker│  │  JPA (batch 100)   │
  │ (acks=all)  │  │  ↓ OPEN        │  │  Idempotency guard │
  │ SessionId   │  │  In-Memory     │  └───────────────────┘
  │ Partitioner │  │  Fallback Queue│
  └──────┬──────┘  └───────────────┘
         │             (LinkedBlockingQueue, 10k slots — no AWS required)
  ┌──────▼──────────────────────────────────────┐
  │           KAFKA TOPIC: heartbeat-events     │
  │  12 partitions • RF=3 • acks=all            │
  │  min.insync.replicas=2                      │
  └──────┬──────────────────────────────────────┘
         │
  ┌──────▼──────────────────────────────────────┐
  │          CONSUMER GROUP: hamdel-kpi-group   │
  │  Batch Listener • concurrency=3             │
  │  Idempotency: event_id DB constraint        │
  │  KPIs: VST, PFR, Rebuffering Ratio          │
  └─────────────────────────────────────────────┘

  ┌────────────────────────────────────────────────┐
  │               OBSERVABILITY                    │
  │  /actuator/prometheus  →  Prometheus  →  Grafana│
  │  Panels: Ingestion RPS, P99 Latency,           │
  │          Kafka Lag, Fallback Active, KPIs      │
  └────────────────────────────────────────────────┘
```

---

## Local Setup (Single Command)

### Prerequisites

| Tool          | Version    |
|---------------|------------|
| Docker        | ≥ 24       |
| Docker Compose| ≥ 2.24     |

```bash
git clone https://github.com/avijeet/hamdel.git
cd hamdel
docker-compose up --build
```

This starts:

| Service       | URL                                   |
|---------------|---------------------------------------|
| **Hamdel**    | http://localhost:8080                 |
| **Grafana**   | http://localhost:3000 (admin / admin) |
| **Prometheus**| http://localhost:9090                 |
| **Kafka**     | localhost:9092                        |
| **PostgreSQL**| localhost:5432 (hamdel / hamdel)      |

---

## API

### Ingest Heartbeat (JSON)

```http
POST /api/v1/heartbeat
Content-Type: application/json

{
  "eventId":           "550e8400-e29b-41d4-a716-446655440000",
  "sessionId":         "session-123",
  "clientId":          "client-456",
  "contentId":         "movie-inception",
  "timestampMs":       1712198400000,
  "videoStartTimeMs":  312.5,
  "playbackFailed":    false,
  "rebufferDurationMs": 0,
  "playbackDurationMs": 120000,
  "playerVersion":     "5.2.1",
  "os":                "iOS",
  "cdn":               "cloudfront"
}
```

**Response:** `202 Accepted` (no body) in < 5ms.

### Ingest Heartbeat (Protobuf binary)

```http
POST /api/v1/heartbeat
Content-Type: application/x-protobuf

<binary protobuf payload>
```

### Health / Metrics

```
GET /actuator/health
GET /actuator/prometheus
```

---

## Load Testing with k6

```bash
# Install k6: https://k6.io/docs/getting-started/installation/
k6 run load-test/k6-load-test.js

# Override target URL
k6 run -e BASE_URL=http://localhost:8080 load-test/k6-load-test.js
```

The script ramps up to **2 000 VUs** and enforces the P99 < 5ms SLA threshold.

---

## Running Tests

```bash
# Unit tests only
./mvnw test -Dtest="*Test" -DfailIfNoTests=false

# All tests including Testcontainers integration tests
./mvnw verify
```

Integration tests spin up real PostgreSQL and Kafka containers via Testcontainers. Docker must be running.

---

## Key Design Decisions

| Concern          | Solution                                                         |
|------------------|------------------------------------------------------------------|
| High throughput  | Java 21 Virtual Threads — no thread-per-request bottleneck       |
| Protocol         | Protobuf binary — minimal network overhead                       |
| Ordering         | Custom `SessionIdPartitioner` — murmur2 hash of sessionId        |
| Durability       | `acks=all`, RF=3, `min.insync.replicas=2`                        |
| Resilience       | Resilience4j CB → `InMemoryFallbackPublisher` (10k-slot queue)   |
| Local dev        | No AWS or external cloud services required                       |
| Idempotency      | `event_id` UNIQUE DB constraint + pre-check in consumer          |
| KPIs             | VST avg, PFR, Rebuffering Ratio computed per batch               |
| Observability    | Micrometer → Prometheus → pre-provisioned Grafana dashboard      |
| Architecture     | Hexagonal (Ports & Adapters) — zero framework coupling in domain |
