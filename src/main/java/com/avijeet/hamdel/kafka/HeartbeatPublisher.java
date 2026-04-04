package com.avijeet.hamdel.kafka;

import com.avijeet.hamdel.model.HeartbeatEvent;

public interface HeartbeatPublisher {

    void publish(HeartbeatEvent event);
}
