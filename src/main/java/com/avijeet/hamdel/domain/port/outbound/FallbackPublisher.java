package com.avijeet.hamdel.domain.port.outbound;

import com.avijeet.hamdel.domain.model.HeartbeatEventProto;

public interface FallbackPublisher {

    void publish(HeartbeatEventProto event);
}
