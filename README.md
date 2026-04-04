# Hamdel — High-Throughput Log Ingestion Microservice

**Hamdel** is a high-throughput heartbeat ingestion pipeline built with Java 17, Spring Boot 3, and a Hexagonal (Clean) Architecture. It was designed as a direct evolution of the Bifrost architecture.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           INBOUND ADAPTERS                              │
│  REST Controller (Virtual Threads)  │  Protobuf binary  │  JSON        │
└──────────────────────────┬──────────────────────────────────────────────┘
                           │
                  ┌────────▼────────┐
                  │  LMAX Disruptor │  ← Non-blocking ring buffer (65 536 slots)
                  │   Ring Buffer   │    O(1) publish, < 5ms SLA guaranteed
                  └────────┬────────┘
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
  │ SessionId   │  │  SQS Fallback  │  └───────────────────┘
  │ Partitioner │  │  (Panic Mode)  │
  └──────┬──────┘  └───────────────┘
         │
  ┌──────▼──────────────────────────────────────┐
  │           KAFKA TOPIC: heartbeat-events     │
  │  12 partitions • RF=3 • acks=all            │
  │  min.insync.replicas=2                      │
  └──────┬──────────────────────────────────────┘
         │
  ┌──────▼──────────────────────────────────────┐
  │          CONSUMER GROUP: hamdel-kpi-group   │
  │  Batch Listener (500 msgs) • concurrency=3  │
  │  Idempotency: event_id DB constraint        │
  │  KPIs: VST, PFR, Rebuffering Ratio          │
  └─────────────────────────────────────────────┘

  ┌────────────────────────────────────────────────┐
  │               OBSERVABILITY                    │
  │  /actuator/prometheus  →  Prometheus  →  Grafana│
  │  Panels: Ingestion RPS, P99 Latency,           │
  │          Kafka Lag, Panic Mode, KPIs           │
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

| Service      | URL                                   |
|--------------|---------------------------------------|
| **Hamdel**   | http://localhost:8080                 |
| **Grafana**  | http://localhost:3000 (admin / admin) |
| **Prometheus**| http://localhost:9090                |
| **Kafka**    | localhost:9092                        |
| **PostgreSQL**| localhost:5432 (hamdel / hamdel)     |
| **LocalStack (SQS)** | http://localhost:4566         |

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

| Concern          | Solution                                              |
|------------------|-------------------------------------------------------|
| High throughput  | Virtual Threads (Loom) + LMAX Disruptor ring buffer   |
| Protocol         | Protobuf binary — minimal network overhead            |
| Ordering         | Custom `SessionIdPartitioner` — murmur2 hash of sessionId |
| Durability       | `acks=all`, RF=3, `min.insync.replicas=2`            |
| Resilience       | Resilience4j CB → SQS FIFO Panic Mode fallback        |
| Retry            | Exponential backoff + jitter (max 5 retries, 30s cap) |
| Idempotency      | `event_id` UNIQUE DB constraint + pre-check           |
| KPIs             | VST, PFR, Rebuffering Ratio computed per batch        |
| Observability    | Micrometer → Prometheus → pre-provisioned Grafana dashboard |
| Architecture     | Hexagonal (Ports & Adapters) — zero framework coupling in domain |
