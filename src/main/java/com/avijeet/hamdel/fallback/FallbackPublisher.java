package com.avijeet.hamdel.fallback;

import com.avijeet.hamdel.model.HeartbeatEvent;

import java.util.List;

public interface FallbackPublisher {

    void publish(HeartbeatEvent event);

    /**
     * Phase 4 — drain up to {@code maxItems} buffered events so the caller
     * (HeartbeatProducer) can replay them to Kafka after the circuit
     * breaker recovers.
     */
    List<HeartbeatEvent> drain(int maxItems);

    int bufferedCount();
}
