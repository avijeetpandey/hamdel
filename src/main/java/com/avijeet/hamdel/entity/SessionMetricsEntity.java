package com.avijeet.hamdel.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "session_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionMetricsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    @Column(name = "avg_vst_ms")
    private double avgVideoStartTimMs;

    @Column(name = "pfr")
    private double playbackFailureRate;

    @Column(name = "rebuffering_ratio")
    private double rebufferingRatio;

    @Column(name = "event_count")
    private long eventCount;
}
