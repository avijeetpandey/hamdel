package com.avijeet.hamdel.application.service;

import com.avijeet.hamdel.domain.model.HeartbeatEventProto;
import com.avijeet.hamdel.domain.port.outbound.HeartbeatPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HeartbeatIngestionServiceTest {

    @Mock
    HeartbeatPublisher publisher;

    @InjectMocks
    HeartbeatIngestionService service;

    @Test
    void ingest_delegatesToPublisher() {
        HeartbeatEventProto event = buildEvent();
        service.ingest(event);
        verify(publisher).publish(event);
    }

    private HeartbeatEventProto buildEvent() {
        return HeartbeatEventProto.builder()
                .eventId(UUID.randomUUID().toString())
                .sessionId("session-1")
                .clientId("client-1")
                .contentId("content-1")
                .timestamp(Instant.now())
                .videoStartTimeMs(250)
                .playbackFailed(false)
                .rebufferDurationMs(0)
                .playbackDurationMs(60_000)
                .build();
    }
}

