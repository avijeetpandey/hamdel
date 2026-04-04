package com.avijeet.hamdel.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class HeartbeatEventProto {

    String eventId;
    String sessionId;
    String clientId;
    String contentId;
    Instant timestamp;

    double videoStartTimeMs;
    boolean playbackFailed;
    double rebufferDurationMs;
    double playbackDurationMs;

    String playerVersion;
    String os;
    String cdn;
}
