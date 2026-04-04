package com.avijeet.hamdel.adapter.outbound.kafka;

import com.avijeet.hamdel.application.service.KpiCalculationService;
import com.avijeet.hamdel.domain.model.HeartbeatEventProto;
import com.avijeet.hamdel.domain.port.outbound.TelemetryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaHeartbeatConsumer {

    private final TelemetryRepository   telemetryRepository;
    private final KpiCalculationService kpiCalculationService;
    private final MeterRegistry         meterRegistry;

    @KafkaListener(
            topics           = "${hamdel.kafka.topic:heartbeat-events}",
            groupId          = "${hamdel.kafka.consumer-group:hamdel-kpi-group}",
            containerFactory = "batchKafkaListenerContainerFactory"
    )
    public void consume(
            List<byte[]> payloads,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
            @Header(KafkaHeaders.OFFSET) List<Long> offsets) {

        log.debug("Consumed batch of {} events", payloads.size());
        meterRegistry.counter("hamdel.kafka.consumed.total").increment(payloads.size());

        List<HeartbeatEventProto> events = payloads.stream()
                .map(this::deserialize)
                .filter(e -> e != null)
                .filter(e -> !telemetryRepository.existsByEventId(e.getEventId()))
                .toList();

        events.forEach(telemetryRepository::save);
        kpiCalculationService.processBatch(events);
    }

    private HeartbeatEventProto deserialize(byte[] payload) {
        try {
            com.avijeet.hamdel.proto.HeartbeatEvent proto =
                    com.avijeet.hamdel.proto.HeartbeatEvent.parseFrom(payload);
            return HeartbeatEventProto.builder()
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

