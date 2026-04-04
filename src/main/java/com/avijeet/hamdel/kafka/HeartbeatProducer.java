package com.avijeet.hamdel.kafka;

import com.avijeet.hamdel.model.HeartbeatEvent;
import com.avijeet.hamdel.fallback.FallbackPublisher;
import com.avijeet.hamdel.kafka.HeartbeatPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outbound adapter: publishes heartbeat events to Kafka.
 * Wraps send calls in a Resilience4j CircuitBreaker.
 * On circuit OPEN (Panic Mode), automatically reroutes to the in-memory fallback buffer.
 */
@Slf4j
@Component
public class HeartbeatProducer implements HeartbeatPublisher {

    private static final String CB_NAME = "kafkaProducer";

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final FallbackPublisher             fallbackPublisher;
    private final CircuitBreaker                circuitBreaker;
    private final MeterRegistry                 meterRegistry;
    private final String                        topic;
    private final AtomicBoolean                 panicMode = new AtomicBoolean(false);

    public HeartbeatProducer(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            FallbackPublisher fallbackPublisher,
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry,
            @Value("${hamdel.kafka.topic:heartbeat-events}") String topic) {
        this.kafkaTemplate     = kafkaTemplate;
        this.fallbackPublisher = fallbackPublisher;
        this.circuitBreaker    = circuitBreakerRegistry.circuitBreaker(CB_NAME);
        this.meterRegistry     = meterRegistry;
        this.topic             = topic;

        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event -> handleStateTransition(event.getStateTransition()));
    }

    @Override
    public void publish(HeartbeatEvent event) {
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            activatePanicMode(event);
            return;
        }

        circuitBreaker.executeRunnable(() -> sendToKafka(event));
    }

    private void sendToKafka(HeartbeatEvent event) {
        byte[] payload = serializeToProto(event);
        kafkaTemplate.send(topic, event.getSessionId(), payload)
                .exceptionally(ex -> {
                    log.error("Kafka send failed for event={}", event.getEventId(), ex);
                    throw new RuntimeException(ex);
                });
    }

    private void activatePanicMode(HeartbeatEvent event) {
        if (panicMode.compareAndSet(false, true)) {
            log.warn("Kafka unavailable — buffering to local fallback");
            meterRegistry.counter("hamdel.fallback.activations").increment();
        }
        meterRegistry.gauge("hamdel.fallback.active", 1.0);
        fallbackPublisher.publish(event);
    }

    private void handleStateTransition(CircuitBreaker.StateTransition transition) {
        log.warn("Kafka CircuitBreaker state transition: {}", transition);
        if (transition == CircuitBreaker.StateTransition.OPEN_TO_CLOSED
                || transition == CircuitBreaker.StateTransition.HALF_OPEN_TO_CLOSED) {
            panicMode.set(false);
            meterRegistry.gauge("hamdel.fallback.active", 0.0);
            log.info("Kafka recovered — resuming direct publishing, draining fallback buffer");
            drainFallbackBuffer();
        }
    }

    /**
     * Drains the in-memory fallback buffer back to Kafka after recovery.
     * Runs on the CB event-listener thread; failures re-buffer the event rather than losing it.
     */
    private void drainFallbackBuffer() {
        int total = 0;
        List<HeartbeatEvent> batch;
        while (!(batch = fallbackPublisher.drain(500)).isEmpty()) {
            for (HeartbeatEvent event : batch) {
                try {
                    sendToKafka(event);
                    total++;
                } catch (Exception ex) {
                    log.error("Failed to replay fallback event to Kafka: eventId={}", event.getEventId(), ex);
                    // re-buffer rather than lose
                    fallbackPublisher.publish(event);
                }
            }
        }
        if (total > 0) {
            log.info("Fallback buffer drained: {} events replayed to Kafka", total);
            meterRegistry.counter("hamdel.fallback.replayed").increment(total);
        }
    }

    private byte[] serializeToProto(HeartbeatEvent event) {
        com.avijeet.hamdel.proto.HeartbeatEvent proto =
                com.avijeet.hamdel.proto.HeartbeatEvent.newBuilder()
                        .setEventId(event.getEventId())
                        .setSessionId(event.getSessionId())
                        .setClientId(event.getClientId())
                        .setContentId(event.getContentId())
                        .setTimestampMs(event.getTimestamp().toEpochMilli())
                        .setVideoStartTimeMs(event.getVideoStartTimeMs())
                        .setPlaybackFailed(event.isPlaybackFailed())
                        .setRebufferDurationMs(event.getRebufferDurationMs())
                        .setPlaybackDurationMs(event.getPlaybackDurationMs())
                        .setPlayerVersion(event.getPlayerVersion() != null ? event.getPlayerVersion() : "")
                        .setOs(event.getOs() != null ? event.getOs() : "")
                        .setCdn(event.getCdn() != null ? event.getCdn() : "")
                        .build();
        return proto.toByteArray();
    }
}
