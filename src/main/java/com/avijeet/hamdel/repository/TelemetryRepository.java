package com.avijeet.hamdel.repository;

import com.avijeet.hamdel.model.HeartbeatEvent;

import java.util.List;

public interface TelemetryRepository {

    /**
     * Batch-persist a list of heartbeat events.
     * Implementations MUST handle duplicates gracefully (ON CONFLICT DO NOTHING).
     */
    void saveAll(List<HeartbeatEvent> events);

    /** Kept for backward-compatibility (integration tests, ad-hoc use). */
    default void save(HeartbeatEvent event) {
        saveAll(List.of(event));
    }

    boolean existsByEventId(String eventId);
}
