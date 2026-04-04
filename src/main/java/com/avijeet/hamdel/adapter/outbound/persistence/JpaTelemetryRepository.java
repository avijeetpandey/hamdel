package com.avijeet.hamdel.adapter.outbound.persistence;

import com.avijeet.hamdel.adapter.outbound.persistence.entity.HeartbeatEventEntity;
import com.avijeet.hamdel.adapter.outbound.persistence.repository.HeartbeatEventJpaRepository;
import com.avijeet.hamdel.domain.model.HeartbeatEventProto;
import com.avijeet.hamdel.domain.port.outbound.TelemetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed implementation of TelemetryRepository.
 * Uses Spring Data's save which will execute a single INSERT per event.
 * Duplicate events (same event_id) are caught at the DB constraint level.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JpaTelemetryRepository implements TelemetryRepository {

    private final HeartbeatEventJpaRepository jpaRepository;

    @Override
    @Transactional
    public void save(HeartbeatEventProto event) {
        try {
            jpaRepository.save(toEntity(event));
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate event ignored: eventId={}", event.getEventId());
        }
    }

    @Override
    public boolean existsByEventId(String eventId) {
        return jpaRepository.existsByEventId(eventId);
    }

    private HeartbeatEventEntity toEntity(HeartbeatEventProto e) {
        return HeartbeatEventEntity.builder()
                .eventId(e.getEventId())
                .sessionId(e.getSessionId())
                .clientId(e.getClientId())
                .contentId(e.getContentId())
                .timestamp(e.getTimestamp())
                .videoStartTimeMs(e.getVideoStartTimeMs())
                .playbackFailed(e.isPlaybackFailed())
                .rebufferDurationMs(e.getRebufferDurationMs())
                .playbackDurationMs(e.getPlaybackDurationMs())
                .playerVersion(e.getPlayerVersion())
                .os(e.getOs())
                .cdn(e.getCdn())
                .build();
    }
}
