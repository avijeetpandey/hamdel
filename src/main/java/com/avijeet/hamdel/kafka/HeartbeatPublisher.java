package com.avijeet.hamdel.kafka;

import com.avijeet.hamdel.model.HeartbeatEvent;
import java.util.List;

public interface HeartbeatPublisher {

    void publish(HeartbeatEvent event);

    void publishBatch(List<HeartbeatEvent> events);

    void flush();
}
