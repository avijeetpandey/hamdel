package com.avijeet.hamdel.service;

import com.avijeet.hamdel.model.HeartbeatEvent;
import com.avijeet.hamdel.buffer.HeartbeatEventBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeartbeatIngestionServiceTest {

    @Mock
    HeartbeatEventBuffer buffer;

    @InjectMocks
    HeartbeatIngestionService service;

    @Test
    void ingest_publishesToBuffer_returnsTrue() {
        HeartbeatEvent event = buildEvent();
        when(buffer.tryPublish(event)).thenReturn(true);

        boolean result = service.ingest(event);

        verify(buffer).tryPublish(event);
        assertThat(result).isTrue();
    }

    @Test
    void ingest_bufferFull_returnsFalse() {
        HeartbeatEvent event = buildEvent();
        when(buffer.tryPublish(event)).thenReturn(false);

        boolean result = service.ingest(event);

        assertThat(result).isFalse();
    }

    private HeartbeatEvent buildEvent() {
        return HeartbeatEvent.builder()
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

