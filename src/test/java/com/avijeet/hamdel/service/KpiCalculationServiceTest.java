package com.avijeet.hamdel.service;

import com.avijeet.hamdel.model.HeartbeatEvent;
import com.avijeet.hamdel.repository.MetricsRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KpiCalculationServiceTest {

    @Mock MetricsRepository metricsRepository;

    KpiCalculationService service;

    @BeforeEach
    void setUp() {
        service = new KpiCalculationService(metricsRepository, new SimpleMeterRegistry());
    }

    @Test
    void processBatch_calculatesCorrectVst() {
        var events = List.of(
                event(200, false, 0, 60_000),
                event(400, false, 0, 60_000)
        );

        service.processBatch(events);

        var captor = ArgumentCaptor.forClass(com.avijeet.hamdel.model.SessionMetrics.class);
        verify(metricsRepository).save(captor.capture());
        assertThat(captor.getValue().getAvgVideoStartTimMs()).isEqualTo(300.0);
    }

    @Test
    void processBatch_calculatesCorrectPfr() {
        var events = List.of(
                event(200, true,  0, 60_000),
                event(200, false, 0, 60_000),
                event(200, false, 0, 60_000),
                event(200, false, 0, 60_000)
        );

        service.processBatch(events);

        var captor = ArgumentCaptor.forClass(com.avijeet.hamdel.model.SessionMetrics.class);
        verify(metricsRepository).save(captor.capture());
        assertThat(captor.getValue().getPlaybackFailureRate()).isEqualTo(0.25);
    }

    @Test
    void processBatch_calculatesRebufferingRatio() {
        var events = List.of(
                event(200, false, 6_000, 60_000),
                event(200, false, 0,     60_000)
        );

        service.processBatch(events);

        var captor = ArgumentCaptor.forClass(com.avijeet.hamdel.model.SessionMetrics.class);
        verify(metricsRepository).save(captor.capture());
        // 6000 / 120000 = 0.05
        assertThat(captor.getValue().getRebufferingRatio()).isEqualTo(0.05);
    }

    @Test
    void processBatch_doesNothingForEmptyList() {
        service.processBatch(List.of());
        org.mockito.Mockito.verifyNoInteractions(metricsRepository);
    }

    private HeartbeatEvent event(double vst, boolean failed, double rebuf, double playback) {
        return HeartbeatEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sessionId("session-test")
                .clientId("client-1")
                .contentId("content-1")
                .timestamp(Instant.now())
                .videoStartTimeMs(vst)
                .playbackFailed(failed)
                .rebufferDurationMs(rebuf)
                .playbackDurationMs(playback)
                .build();
    }
}
