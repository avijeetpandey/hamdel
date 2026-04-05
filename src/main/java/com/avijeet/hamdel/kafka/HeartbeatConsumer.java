package com.avijeet.hamdel.kafka;

import com.avijeet.hamdel.service.KpiCalculationService;
import com.avijeet.hamdel.model.HeartbeatEvent;
import com.avijeet.hamdel.repository.TelemetryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Idempotency is enforced by ON CONFLICT (event_id) DO NOTHING at the database level,
 * removing the per-event SELECT pre-check and halving write pressure.
 *
 * The consumer bean is conditional on {@code hamdel.consumer.enabled=true} so the same
 * JAR can run as an ingress-only pod (consumer disabled) or a processor pod (enabled).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hamdel.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class HeartbeatConsumer {

    private final TelemetryRepository      telemetryRepository;
    private final KpiCalculationService    kpiCalculationService;
    private final DeadLetterPublisher deadLetterPublisher;
    private final MeterRegistry            meterRegistry;

    @KafkaListener(
            topics           = "${hamdel.kafka.topic:heartbeat-events}",
            groupId          = "${hamdel.kafka.consumer-group:hamdel-kpi-group}",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consume(
            List<byte[]> payloads,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions) {

        log.info("Consumed batch: size={} partitions={}", payloads.size(), partitions);
        meterRegistry.counter("hamdel.kafka.consumed.total").increment(payloads.size());

        List<HeartbeatEvent> events = new ArrayList<>(payloads.size());
        List<byte[]> failed = new ArrayList<>();

        for (byte[] payload : payloads) {
            HeartbeatEvent event = deserialize(payload);
            if (event != null) {
                events.add(event);
            } else {
                failed.add(payload);
            }
        }

        // Route poison messages to DLT — never block the healthy batch
        failed.forEach(p -> deadLetterPublisher.publish(p, "deserialization-failure"));
        if (!failed.isEmpty()) {
            meterRegistry.counter("hamdel.kafka.dlt.routed").increment(failed.size());
        }

        if (!events.isEmpty()) {
            telemetryRepository.saveAll(events);
            kpiCalculationService.processBatch(events);
        }

        log.info("Processed batch: total={} ok={} dlt={}", payloads.size(), events.size(), failed.size());
    }

    private HeartbeatEvent deserialize(byte[] payload) {
        try {
            com.avijeet.hamdel.proto.HeartbeatEvent proto =
                    com.avijeet.hamdel.proto.HeartbeatEvent.parseFrom(payload);
            return HeartbeatEvent.builder()
                    .eventId(proto.getEventId())
                    .sessionId(proto.getSessionId())
                    .clientId(proto.getClientId())
                    .contentId(proto.getContentId())
                    .timestamp(Instant.ofEpochMilli(proto.getTimestampMs()))
                    .videoStartTimeMs(proto.getVideoStartTimeMs())
                    .playbackFailed(proto.getPlaybackFailed())
                    .rebufferDurationMs(proto.getRebufferDurationMs())
                    .playbackDurationMs(proto.getPlaybackDurationMs())
                    .playerVersion(proto.getPlayerVersion())
                    .os(proto.getOs())
                    .cdn(proto.getCdn())
                    .build();
        } catch (Exception e) {
            log.error("Failed to deserialize Kafka payload", e);
            meterRegistry.counter("hamdel.kafka.deserialization.errors").increment();
            return null;
        }
    }
}


