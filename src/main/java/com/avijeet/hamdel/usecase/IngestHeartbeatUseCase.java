package com.avijeet.hamdel.usecase;

import com.avijeet.hamdel.model.HeartbeatEvent;

public interface IngestHeartbeatUseCase {

    /**
     * Attempts to enqueue the event for async processing.
     *
     * @return true  — event accepted into the ring buffer
     *         false — buffer at capacity; caller must apply backpressure (429)
     */
    boolean ingest(HeartbeatEvent event);
}
