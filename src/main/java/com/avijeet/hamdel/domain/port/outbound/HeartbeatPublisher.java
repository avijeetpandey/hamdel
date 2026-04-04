package com.avijeet.hamdel.domain.port.outbound;

import com.avijeet.hamdel.domain.model.HeartbeatEventProto;

public interface HeartbeatPublisher {

    void publish(HeartbeatEventProto event);
}
