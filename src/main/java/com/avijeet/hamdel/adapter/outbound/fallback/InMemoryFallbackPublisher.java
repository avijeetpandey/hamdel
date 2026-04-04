package com.avijeet.hamdel.adapter.outbound.fallback;

import com.avijeet.hamdel.domain.model.HeartbeatEventProto;
import com.avijeet.hamdel.domain.port.outbound.FallbackPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * Local in-memory fallback buffer used when Kafka is unreachable.
 * Events are stored in a bounded queue and can be drained once Kafka recovers.
 * No external dependencies required — works out of the box locally.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryFallbackPublisher implements FallbackPublisher {

    private static final int MAX_BUFFER_SIZE = 10_000;

    private final LinkedBlockingQueue<HeartbeatEventProto> buffer =
            new LinkedBlockingQueue<>(MAX_BUFFER_SIZE);

    private final MeterRegistry meterRegistry;

    @Override
    public void publish(HeartbeatEventProto event) {
        boolean accepted = buffer.offer(event);
        if (accepted) {
            meterRegistry.gauge("hamdel.fallback.buffer.size", buffer, LinkedBlockingQueue::size);
            log.warn("Kafka unavailable — event buffered locally: eventId={}, bufferSize={}",
                    event.getEventId(), buffer.size());
        } else {
            log.error("Fallback buffer full, dropping event: eventId={}", event.getEventId());
            meterRegistry.counter("hamdel.fallback.dropped").increment();
        }
    }

    public int bufferedCount() {
        return buffer.size();
    }
}
