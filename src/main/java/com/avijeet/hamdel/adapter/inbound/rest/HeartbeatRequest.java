package com.avijeet.hamdel.adapter.inbound.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * JSON request DTO for the heartbeat ingestion endpoint.
 */
public record HeartbeatRequest(
        @NotBlank String eventId,
        @NotBlank String sessionId,
        @NotBlank String clientId,
        @NotBlank String contentId,
        @Positive long   timestampMs,
        double videoStartTimeMs,
        boolean playbackFailed,
        double rebufferDurationMs,
        double playbackDurationMs,
        String playerVersion,
        String os,
        String cdn
) {}
