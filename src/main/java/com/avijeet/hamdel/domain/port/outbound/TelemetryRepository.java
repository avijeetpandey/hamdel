package com.avijeet.hamdel.domain.port.outbound;

import com.avijeet.hamdel.domain.model.HeartbeatEventProto;

public interface TelemetryRepository {

    void save(HeartbeatEventProto event);

    boolean existsByEventId(String eventId);
}
