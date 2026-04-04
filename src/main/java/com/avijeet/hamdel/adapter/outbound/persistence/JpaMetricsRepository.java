package com.avijeet.hamdel.adapter.outbound.persistence;

import com.avijeet.hamdel.adapter.outbound.persistence.entity.SessionMetricsEntity;
import com.avijeet.hamdel.adapter.outbound.persistence.repository.SessionMetricsJpaRepository;
import com.avijeet.hamdel.domain.model.SessionMetrics;
import com.avijeet.hamdel.domain.port.outbound.MetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaMetricsRepository implements MetricsRepository {

    private final SessionMetricsJpaRepository jpaRepository;

    @Override
    @Transactional
    public void save(SessionMetrics metrics) {
        jpaRepository.save(toEntity(metrics));
    }

    private SessionMetricsEntity toEntity(SessionMetrics m) {
        return SessionMetricsEntity.builder()
                .sessionId(m.getSessionId())
                .windowStart(m.getWindowStart())
                .windowEnd(m.getWindowEnd())
                .avgVideoStartTimMs(m.getAvgVideoStartTimMs())
                .playbackFailureRate(m.getPlaybackFailureRate())
                .rebufferingRatio(m.getRebufferingRatio())
                .eventCount(m.getEventCount())
                .build();
    }
}
