package com.avijeet.hamdel.service;

import com.avijeet.hamdel.model.HeartbeatEvent;
import com.avijeet.hamdel.model.SessionMetrics;
import com.avijeet.hamdel.repository.MetricsRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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

    private final MetricsRepository metricsRepository;
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

    public void processBatch(List<HeartbeatEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        // A single Kafka batch can span many sessions — group so each gets its own row.
        Map<String, List<HeartbeatEvent>> bySession = events.stream()
                .collect(Collectors.groupingBy(HeartbeatEvent::getSessionId));

        double lastVst = 0, lastPfr = 0, lastRebuf = 0;

        for (Map.Entry<String, List<HeartbeatEvent>> entry : bySession.entrySet()) {
            String sessionId    = entry.getKey();
            List<HeartbeatEvent> sessionEvents = entry.getValue();

            Instant windowStart = sessionEvents.stream().map(HeartbeatEvent::getTimestamp)
                    .min(Instant::compareTo).orElse(Instant.now());
            Instant windowEnd   = sessionEvents.stream().map(HeartbeatEvent::getTimestamp)
                    .max(Instant::compareTo).orElse(Instant.now());

            double avgVst = sessionEvents.stream()
                    .mapToDouble(HeartbeatEvent::getVideoStartTimeMs)
                    .average().orElse(0.0);

            long failures = sessionEvents.stream().filter(HeartbeatEvent::isPlaybackFailed).count();
            double pfr    = (double) failures / sessionEvents.size();

            double totalPlayback = sessionEvents.stream().mapToDouble(HeartbeatEvent::getPlaybackDurationMs).sum();
            double totalRebuffer = sessionEvents.stream().mapToDouble(HeartbeatEvent::getRebufferDurationMs).sum();
            double rebufferRatio = totalPlayback > 0 ? totalRebuffer / totalPlayback : 0.0;

            SessionMetrics metrics = SessionMetrics.builder()
                    .sessionId(sessionId)
                    .windowStart(windowStart)
                    .windowEnd(windowEnd)
                    .avgVideoStartTimMs(avgVst)
                    .playbackFailureRate(pfr)
                    .rebufferingRatio(rebufferRatio)
                    .eventCount(sessionEvents.size())
                    .build();

            metricsRepository.save(metrics);

            lastVst   = avgVst;
            lastPfr   = pfr;
            lastRebuf = rebufferRatio;

            log.debug("KPI sessionId={} VST={}ms PFR={} RebufRatio={} events={}",
                    sessionId, avgVst, pfr, rebufferRatio, sessionEvents.size());
        }

        // Gauges reflect the last session's values — approximate but sufficient for trend alerts.
        vstGauge.set(lastVst);
        pfrGauge.set(lastPfr);
        rebufGauge.set(lastRebuf);
    }
}
