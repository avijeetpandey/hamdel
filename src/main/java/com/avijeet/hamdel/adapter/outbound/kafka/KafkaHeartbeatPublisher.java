package com.avijeet.hamdel.adapter.outbound.kafka;

import com.avijeet.hamdel.domain.model.HeartbeatEventProto;
import com.avijeet.hamdel.domain.port.outbound.FallbackPublisher;
import com.avijeet.hamdel.domain.port.outbound.HeartbeatPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outbound adapter: publishes heartbeat events to Kafka.
 * Wraps send calls in a Resilience4j CircuitBreaker.
 * On circuit OPEN (Panic Mode), automatically reroutes to SQS fallback.
 */
@Slf4j
@Component
public class KafkaHeartbeatPublisher implements HeartbeatPublisher {

    private static final String CB_NAME = "kafkaProducer";

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final FallbackPublisher             fallbackPublisher;
    private final CircuitBreaker                circuitBreaker;
    private final MeterRegistry                 meterRegistry;
    private final String                        topic;
    private final AtomicBoolean                 panicMode = new AtomicBoolean(false);

    public KafkaHeartbeatPublisher(
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
    public void publish(HeartbeatEventProto event) {
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            activatePanicMode(event);
            return;
        }

        circuitBreaker.executeRunnable(() -> sendToKafka(event));
    }

    private void sendToKafka(HeartbeatEventProto event) {
        byte[] payload = serializeToProto(event);
        kafkaTemplate.send(topic, event.getSessionId(), payload)
                .exceptionally(ex -> {
                    log.error("Kafka send failed for event={}", event.getEventId(), ex);
                    throw new RuntimeException(ex);
                });
    }

    private void activatePanicMode(HeartbeatEventProto event) {
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
            log.info("Kafka recovered — resuming direct publishing");
        }
    }

    private byte[] serializeToProto(HeartbeatEventProto event) {
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
