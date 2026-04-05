package com.avijeet.hamdel.repository;

import com.avijeet.hamdel.repository.jpa.HeartbeatEventJpaRepository;
import com.avijeet.hamdel.model.HeartbeatEvent;
import com.avijeet.hamdel.repository.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

/**
 * Persists raw heartbeat events using a single JDBC batch statement per poll batch.
 * ON CONFLICT (event_id) DO NOTHING handles duplicates at the database level,
 * removing the need for a per-event existence pre-check in the consumer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelemetryJdbcRepository implements TelemetryRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO heartbeat_events
                (event_id, session_id, client_id, content_id, ts,
                 video_start_time_ms, playback_failed, rebuffer_duration_ms,
                 playback_duration_ms, player_version, os, cdn)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (event_id) DO NOTHING
            """;

    private final JdbcTemplate               jdbcTemplate;
    private final HeartbeatEventJpaRepository jpaRepository; // still used for reads

    @Override
    @Transactional
    public void saveAll(List<HeartbeatEvent> events) {
        if (events.isEmpty()) return;
        jdbcTemplate.batchUpdate(UPSERT_SQL, events, events.size(), (ps, e) -> {
            ps.setString(1, e.getEventId());
            ps.setString(2, e.getSessionId());
            ps.setString(3, e.getClientId());
            ps.setString(4, e.getContentId());
            ps.setTimestamp(5, Timestamp.from(e.getTimestamp()));
            ps.setDouble(6, e.getVideoStartTimeMs());
            ps.setBoolean(7, e.isPlaybackFailed());
            ps.setDouble(8, e.getRebufferDurationMs());
            ps.setDouble(9, e.getPlaybackDurationMs());
            ps.setString(10, e.getPlayerVersion());
            ps.setString(11, e.getOs());
            ps.setString(12, e.getCdn());
        });
        log.debug("Batch-inserted {} events", events.size());
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return jpaRepository.existsByEventId(eventId);
    }
}
