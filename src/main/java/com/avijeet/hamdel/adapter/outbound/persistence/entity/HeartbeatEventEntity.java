package com.avijeet.hamdel.adapter.outbound.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "heartbeat_events",
    uniqueConstraints = @UniqueConstraint(name = "uc_event_id", columnNames = "event_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeartbeatEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "content_id", nullable = false, length = 128)
    private String contentId;

    @Column(name = "ts", nullable = false)
    private Instant timestamp;

    @Column(name = "video_start_time_ms")
    private double videoStartTimeMs;

    @Column(name = "playback_failed")
    private boolean playbackFailed;

    @Column(name = "rebuffer_duration_ms")
    private double rebufferDurationMs;

    @Column(name = "playback_duration_ms")
    private double playbackDurationMs;

    @Column(name = "player_version", length = 32)
    private String playerVersion;

    @Column(name = "os", length = 32)
    private String os;

    @Column(name = "cdn", length = 64)
    private String cdn;
}
