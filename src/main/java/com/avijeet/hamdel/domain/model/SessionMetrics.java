package com.avijeet.hamdel.domain.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class SessionMetrics {

    String sessionId;
    Instant windowStart;
    Instant windowEnd;

    double avgVideoStartTimMs;
    double playbackFailureRate;
    double rebufferingRatio;
    long   eventCount;
}
