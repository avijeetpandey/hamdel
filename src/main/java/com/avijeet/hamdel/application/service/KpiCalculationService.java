package com.avijeet.hamdel.application.service;

import com.avijeet.hamdel.domain.model.HeartbeatEventProto;
import com.avijeet.hamdel.domain.model.SessionMetrics;
import com.avijeet.hamdel.domain.port.outbound.MetricsRepository;
import com.avijeet.hamdel.domain.port.outbound.TelemetryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Application service that calculates KPI metrics from a batch of heartbeat events.
 * KPIs: Video Start Time (VST), Playback Failure Rate (PFR), Rebuffering Ratio.
 *
 * Gauges are backed by AtomicReference so Micrometer keeps a strong reference —
 * avoids the classic "gauge returns NaN after GC" pitfall.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KpiCalculationService {

    private final TelemetryRepository telemetryRepository;
    private final MetricsRepository   metricsRepository;
    private final MeterRegistry        meterRegistry;

    private final AtomicReference<Double> vstGauge      = new AtomicReference<>(0.0);
    private final AtomicReference<Double> pfrGauge      = new AtomicReference<>(0.0);
    private final AtomicReference<Double> rebufGauge    = new AtomicReference<>(0.0);

    @PostConstruct
    void registerGauges() {
        meterRegistry.gauge("hamdel.kpi.vst_ms",     vstGauge,   AtomicReference::get);
        meterRegistry.gauge("hamdel.kpi.pfr",         pfrGauge,   AtomicReference::get);
        meterRegistry.gauge("hamdel.kpi.rebuf_ratio", rebufGauge, AtomicReference::get);
    }

    public void processBatch(List<HeartbeatEventProto> events) {
        if (events.isEmpty()) {
            return;
        }

        String sessionId    = events.get(0).getSessionId();
        Instant windowStart = events.stream().map(HeartbeatEventProto::getTimestamp).min(Instant::compareTo).orElse(Instant.now());
        Instant windowEnd   = events.stream().map(HeartbeatEventProto::getTimestamp).max(Instant::compareTo).orElse(Instant.now());

        double avgVst = events.stream()
                .mapToDouble(HeartbeatEventProto::getVideoStartTimeMs)
                .average().orElse(0.0);

        long failures = events.stream().filter(HeartbeatEventProto::isPlaybackFailed).count();
        double pfr    = (double) failures / events.size();

        double totalPlayback = events.stream().mapToDouble(HeartbeatEventProto::getPlaybackDurationMs).sum();
        double totalRebuffer = events.stream().mapToDouble(HeartbeatEventProto::getRebufferDurationMs).sum();
        double rebufferRatio = totalPlayback > 0 ? totalRebuffer / totalPlayback : 0.0;

        SessionMetrics metrics = SessionMetrics.builder()
                .sessionId(sessionId)
                .windowStart(windowStart)
                .windowEnd(windowEnd)
                .avgVideoStartTimMs(avgVst)
                .playbackFailureRate(pfr)
                .rebufferingRatio(rebufferRatio)
                .eventCount(events.size())
                .build();

        metricsRepository.save(metrics);

        vstGauge.set(avgVst);
        pfrGauge.set(pfr);
        rebufGauge.set(rebufferRatio);

        log.debug("KPI sessionId={} VST={}ms PFR={} RebufRatio={}", sessionId, avgVst, pfr, rebufferRatio);
    }
}
