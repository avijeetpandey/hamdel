package com.avijeet.hamdel.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Phase 3 — dead-letter publisher for poison/undeserializable Kafka messages.
 * Sends the raw bytes to a dedicated DLT topic with a header identifying the
 * reason. Downstream teams can replay or inspect the DLT independently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterPublisher {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final MeterRegistry                 meterRegistry;

    @Value("${hamdel.kafka.dlt:heartbeat-events-dlt}")
    private String dltTopic;

    public void publish(byte[] rawPayload, String reason) {
        kafkaTemplate.send(dltTopic, rawPayload)
                .exceptionally(ex -> {
                    log.error("Failed to publish to DLT topic={} reason={}", dltTopic, reason, ex);
                    return null;
                });
        meterRegistry.counter("hamdel.kafka.dlt.published", "reason", reason).increment();
        log.warn("Event sent to DLT: reason={} dlt={}", reason, dltTopic);
    }
}
