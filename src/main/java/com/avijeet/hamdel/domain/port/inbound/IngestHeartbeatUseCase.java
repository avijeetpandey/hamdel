package com.avijeet.hamdel.domain.port.inbound;

import com.avijeet.hamdel.domain.model.HeartbeatEventProto;

public interface IngestHeartbeatUseCase {

    void ingest(HeartbeatEventProto event);
}
