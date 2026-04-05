package com.avijeet.hamdel.controller;

import com.avijeet.hamdel.dto.HeartbeatRequest;
import com.avijeet.hamdel.model.HeartbeatEvent;
import com.avijeet.hamdel.usecase.IngestHeartbeatUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Inbound REST adapter.
 * Accepts both Protobuf binary (application/x-protobuf) and JSON payloads.
 * SLA: must return 202 Accepted within <5ms by delegating to the Disruptor buffer.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/heartbeat")
@RequiredArgsConstructor
public class HeartbeatIngestionController {

    private static final String PROTO_MEDIA_TYPE = "application/x-protobuf";

    private final IngestHeartbeatUseCase ingestHeartbeatUseCase;
    private final MeterRegistry          meterRegistry;

    /**
     * Protobuf binary ingestion endpoint — lowest overhead path.
     */
    @PostMapping(consumes = PROTO_MEDIA_TYPE)
    public ResponseEntity<Void> ingestProto(@RequestBody byte[] body) throws Exception {
        long start = System.nanoTime();
        com.avijeet.hamdel.proto.HeartbeatEvent proto = com.avijeet.hamdel.proto.HeartbeatEvent.parseFrom(body);
        HeartbeatEvent event = mapFromProto(proto);
        if (!ingestHeartbeatUseCase.ingest(event)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        recordLatency(start);
        return ResponseEntity.accepted().build();
    }

    /**
     * JSON ingestion endpoint — for human-readable clients and local testing.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> ingestJson(@Valid @RequestBody HeartbeatRequest request) {
        long start = System.nanoTime();
        HeartbeatEvent event = mapFromRequest(request);
        if (!ingestHeartbeatUseCase.ingest(event)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        recordLatency(start);
        return ResponseEntity.accepted().build();
    }

    private void recordLatency(long startNanos) {
        long latencyNs = System.nanoTime() - startNanos;
        meterRegistry.timer("hamdel.ingest.latency").record(latencyNs, TimeUnit.NANOSECONDS);
        if (TimeUnit.NANOSECONDS.toMillis(latencyNs) > 5) {
            log.warn("Ingestion latency exceeded 5ms SLA: {}ms", TimeUnit.NANOSECONDS.toMillis(latencyNs));
        }
    }

    private HeartbeatEvent mapFromProto(com.avijeet.hamdel.proto.HeartbeatEvent proto) {
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
    }

    private HeartbeatEvent mapFromRequest(HeartbeatRequest req) {
        return HeartbeatEvent.builder()
                .eventId(req.eventId())
                .sessionId(req.sessionId())
                .clientId(req.clientId())
                .contentId(req.contentId())
                .timestamp(Instant.ofEpochMilli(req.timestampMs()))
                .videoStartTimeMs(req.videoStartTimeMs())
                .playbackFailed(req.playbackFailed())
                .rebufferDurationMs(req.rebufferDurationMs())
                .playbackDurationMs(req.playbackDurationMs())
                .playerVersion(req.playerVersion())
                .os(req.os())
                .cdn(req.cdn())
                .build();
    }
}
