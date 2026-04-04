package com.avijeet.hamdel.fallback;

import com.avijeet.hamdel.model.HeartbeatEvent;
import com.avijeet.hamdel.fallback.FallbackPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Phase 4 — enhanced in-memory fallback buffer.
 *
 * Additions vs. baseline:
 *  drain(n) — atomically removes up to n events so HeartbeatProducer can
 *              replay them once the circuit breaker transitions back to CLOSED.
 *  bufferedCount() — exposed as a metric so Grafana can alert on non-empty buffers.
 *
 * This buffer is intentionally non-durable (process restart loses buffered events).
 * For production zero-loss guarantees, replace with a durable queue or persist to
 * a local WAL / RocksDB before returning from publish().
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryFallbackPublisher implements FallbackPublisher {

    private static final int MAX_BUFFER_SIZE = 10_000;

    private final LinkedBlockingQueue<HeartbeatEvent> buffer =
            new LinkedBlockingQueue<>(MAX_BUFFER_SIZE);

    private final MeterRegistry meterRegistry;

    @Override
    public void publish(HeartbeatEvent event) {
        boolean accepted = buffer.offer(event);
        meterRegistry.gauge("hamdel.fallback.buffer.size", buffer, LinkedBlockingQueue::size);
        if (accepted) {
            log.warn("Kafka unavailable — event buffered locally: eventId={}, bufferSize={}",
                    event.getEventId(), buffer.size());
        } else {
            log.error("Fallback buffer full, dropping event: eventId={}", event.getEventId());
            meterRegistry.counter("hamdel.fallback.dropped").increment();
        }
    }

    @Override
    public List<HeartbeatEvent> drain(int maxItems) {
        List<HeartbeatEvent> drained = new ArrayList<>(maxItems);
        buffer.drainTo(drained, maxItems);
        if (!drained.isEmpty()) {
            log.info("Drained {} events from fallback buffer for Kafka replay", drained.size());
            meterRegistry.counter("hamdel.fallback.drained").increment(drained.size());
        }
        return drained;
    }

    @Override
    public int bufferedCount() {
        return buffer.size();
    }
}
