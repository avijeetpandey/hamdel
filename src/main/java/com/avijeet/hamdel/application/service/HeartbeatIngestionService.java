package com.avijeet.hamdel.application.service;

import com.avijeet.hamdel.domain.model.HeartbeatEventProto;
import com.avijeet.hamdel.domain.port.inbound.IngestHeartbeatUseCase;
import com.avijeet.hamdel.domain.port.outbound.HeartbeatPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HeartbeatIngestionService implements IngestHeartbeatUseCase {

    private final HeartbeatPublisher publisher;

    @Override
    public void ingest(HeartbeatEventProto event) {
        publisher.publish(event);
    }
}

