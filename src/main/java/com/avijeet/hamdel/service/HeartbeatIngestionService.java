package com.avijeet.hamdel.service;

import com.avijeet.hamdel.model.HeartbeatEvent;
import com.avijeet.hamdel.usecase.IngestHeartbeatUseCase;
import com.avijeet.hamdel.buffer.HeartbeatEventBuffer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Phase 1 — delegates directly to the Disruptor ring buffer.
 * No IO, no locks: the method returns as soon as the sequence is published.
 */
@Service
@RequiredArgsConstructor
public class HeartbeatIngestionService implements IngestHeartbeatUseCase {

    private final HeartbeatEventBuffer buffer;

    @Override
    public boolean ingest(HeartbeatEvent event) {
        return buffer.tryPublish(event);
    }
}

