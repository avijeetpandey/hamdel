package com.avijeet.hamdel.adapter.outbound.kafka;

import com.avijeet.hamdel.domain.model.HeartbeatEventProto;
import com.avijeet.hamdel.domain.port.outbound.FallbackPublisher;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaHeartbeatPublisherTest {

    @Mock KafkaTemplate<String, byte[]> kafkaTemplate;
    @Mock FallbackPublisher             fallbackPublisher;

    KafkaHeartbeatPublisher publisher;
    CircuitBreakerRegistry  cbRegistry;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build();
        cbRegistry = CircuitBreakerRegistry.of(config);

        publisher = new KafkaHeartbeatPublisher(
                kafkaTemplate, fallbackPublisher, cbRegistry,
                new SimpleMeterRegistry(), "heartbeat-events");
    }

    @Test
    @SuppressWarnings("unchecked")
    void publish_sendsToKafkaWhenCircuitClosed() {
        when(kafkaTemplate.send(anyString(), anyString(), any(byte[].class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        publisher.publish(buildEvent());
        verify(kafkaTemplate).send(anyString(), anyString(), any(byte[].class));
        verifyNoInteractions(fallbackPublisher);
    }

    @Test
    void publish_routesToFallbackWhenCircuitOpen() {
        CircuitBreaker cb = cbRegistry.circuitBreaker("kafkaProducer");
        cb.transitionToOpenState();

        publisher.publish(buildEvent());

        verify(fallbackPublisher).publish(any(HeartbeatEventProto.class));
        verifyNoInteractions(kafkaTemplate);
    }

    private HeartbeatEventProto buildEvent() {
        return HeartbeatEventProto.builder()
                .eventId(UUID.randomUUID().toString())
                .sessionId("session-1")
                .clientId("client-1")
                .contentId("content-1")
                .timestamp(Instant.now())
                .videoStartTimeMs(200)
                .playbackFailed(false)
                .rebufferDurationMs(0)
                .playbackDurationMs(60_000)
                .build();
    }
}
