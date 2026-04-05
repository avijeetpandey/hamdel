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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Outbound adapter: publishes heartbeat events to Kafka in batches.
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
    private final List<HeartbeatEvent>          eventBuffer;
    private final int                           batchSize;
    private final long                          batchTimeoutMs;
    private final ScheduledExecutorService      scheduler;
    private volatile long                       lastFlushTime;

    public HeartbeatProducer(
            KafkaTemplate<String, byte[]> kafkaTemplate,
            FallbackPublisher fallbackPublisher,
            CircuitBreakerRegistry circuitBreakerRegistry,
            MeterRegistry meterRegistry,
            @Value("${hamdel.kafka.topic:heartbeat-events}") String topic,
            @Value("${hamdel.kafka.batch-size:100}") int batchSize,
            @Value("${hamdel.kafka.batch-timeout-ms:5000}") long batchTimeoutMs) {
        this.kafkaTemplate     = kafkaTemplate;
        this.fallbackPublisher = fallbackPublisher;
        this.circuitBreaker    = circuitBreakerRegistry.circuitBreaker(CB_NAME);
        this.meterRegistry     = meterRegistry;
        this.topic             = topic;
        this.batchSize         = batchSize;
        this.batchTimeoutMs    = batchTimeoutMs;
        this.eventBuffer       = new ArrayList<>(batchSize);
        this.scheduler         = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kafka-batch-flusher");
            t.setDaemon(true);
            return t;
        });
        this.lastFlushTime     = System.currentTimeMillis();

        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event -> handleStateTransition(event.getStateTransition()));

        // Schedule periodic flush task
        scheduler.scheduleAtFixedRate(this::flushIfNeeded, batchTimeoutMs, batchTimeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void publish(HeartbeatEvent event) {
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            activatePanicMode(event);
            return;
        }

        synchronized (eventBuffer) {
            eventBuffer.add(event);
            if (eventBuffer.size() >= batchSize) {
                sendBatchToKafka();
            }
        }
    }

    @Override
    public void publishBatch(List<HeartbeatEvent> events) {
        if (circuitBreaker.getState() == CircuitBreaker.State.OPEN) {
            for (HeartbeatEvent event : events) {
                activatePanicMode(event);
            }
            return;
        }

        if (events.isEmpty()) {
            return;
        }

        synchronized (eventBuffer) {
            eventBuffer.addAll(events);
            if (eventBuffer.size() >= batchSize) {
                sendBatchToKafka();
            }
        }
    }

    @Override
    public void flush() {
        synchronized (eventBuffer) {
            if (!eventBuffer.isEmpty()) {
                sendBatchToKafka();
            }
        }
    }

    private void flushIfNeeded() {
        synchronized (eventBuffer) {
            if (!eventBuffer.isEmpty()) {
                long timeSinceLastFlush = System.currentTimeMillis() - lastFlushTime;
                if (timeSinceLastFlush >= batchTimeoutMs) {
                    sendBatchToKafka();
                }
            }
        }
    }

    private void sendBatchToKafka() {
        if (eventBuffer.isEmpty()) {
            return;
        }

        List<HeartbeatEvent> batch = new ArrayList<>(eventBuffer);
        eventBuffer.clear();
        lastFlushTime = System.currentTimeMillis();

        circuitBreaker.executeRunnable(() -> {
            try {
                for (HeartbeatEvent event : batch) {
                    byte[] payload = serializeToProto(event);
                    kafkaTemplate.send(topic, event.getSessionId(), payload)
                            .exceptionally(ex -> {
                                log.error("Kafka send failed for event={}", event.getEventId(), ex);
                                throw new RuntimeException(ex);
                            });
                }
                meterRegistry.counter("hamdel.kafka.batch.sent").increment();
                meterRegistry.counter("hamdel.kafka.events.sent").increment(batch.size());
            } catch (Exception ex) {
                log.error("Batch send to Kafka failed, re-buffering {} events", batch.size(), ex);
                synchronized (eventBuffer) {
                    eventBuffer.addAll(batch);
                }
                throw ex;
            }
        });
    }

    private void activatePanicMode(HeartbeatEvent event) {
        if (panicMode.compareAndSet(false, true)) {
            log.warn("Kafka unavailable — buffering to local fallback");
            meterRegistry.counter("hamdel.fallback.activations").increment();
            flush();
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
            publishBatch(batch);
            total += batch.size();
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

