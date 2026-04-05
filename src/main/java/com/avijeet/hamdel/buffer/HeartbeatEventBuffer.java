package com.avijeet.hamdel.buffer;

import com.avijeet.hamdel.model.HeartbeatEvent;
import com.avijeet.hamdel.kafka.HeartbeatPublisher;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LMAX Disruptor ring-buffer that decouples HTTP request threads from the Kafka producer thread.
 * Virtual threads call {@link #tryPublish} and return in under 1 µs; the single-consumer handler
 * drains the ring buffer and forwards events to {@link HeartbeatPublisher} without blocking.
 *
 * Buffer full → {@link #tryPublish} returns false → controller responds 429.
 */
@Slf4j
@Component
public class HeartbeatEventBuffer {

    private final HeartbeatPublisher publisher;
    private final MeterRegistry      meterRegistry;
    private final int                ringBufferSize;

    private Disruptor<HeartbeatEventHolder>  disruptor;
    private RingBuffer<HeartbeatEventHolder> ringBuffer;

    public HeartbeatEventBuffer(
            HeartbeatPublisher publisher,
            MeterRegistry meterRegistry,
            @Value("${hamdel.buffer.ring-buffer-size:65536}") int ringBufferSize) {
        this.publisher      = publisher;
        this.meterRegistry  = meterRegistry;
        this.ringBufferSize = ringBufferSize;
    }

    @PostConstruct
    public void start() {
        disruptor = new Disruptor<>(
                HeartbeatEventHolder::new,
                ringBufferSize,
                Thread.ofVirtual().factory(),
                ProducerType.MULTI,
                new YieldingWaitStrategy());

        disruptor.handleEventsWith((holder, sequence, endOfBatch) -> {
            try {
                publisher.publish(holder.event);
            } finally {
                holder.event = null; // release reference — helps GC
            }
        });

        disruptor.setDefaultExceptionHandler(new BufferExceptionHandler());
        ringBuffer = disruptor.start();

        // Expose remaining capacity as a gauge so Grafana/alert rules can act on it.
        meterRegistry.gauge("hamdel.buffer.remaining_capacity", ringBuffer,
                RingBuffer::remainingCapacity);

        log.info("HeartbeatEventBuffer started: ringBufferSize={}", ringBufferSize);
    }

    @PreDestroy
    public void stop() {
        if (disruptor != null) {
            disruptor.shutdown();
            log.info("HeartbeatEventBuffer stopped");
        }
    }

    /**
     * Non-blocking, lock-free publish to the ring buffer.
     *
     * @return true  — event accepted and will be forwarded to Kafka
     *         false — ring buffer is at capacity; caller should apply backpressure (429)
     */
    public boolean tryPublish(HeartbeatEvent event) {
        try {
            long sequence = ringBuffer.tryNext();
            try {
                ringBuffer.get(sequence).event = event;
            } finally {
                ringBuffer.publish(sequence);
            }
            meterRegistry.counter("hamdel.buffer.published").increment();
            return true;
        } catch (InsufficientCapacityException e) {
            meterRegistry.counter("hamdel.buffer.overflow").increment();
            log.warn("Ring buffer full — shedding load: eventId={}", event.getEventId());
            return false;
        }
    }

    /** Pre-allocated, mutable slot reused by every ring-buffer sequence. */
    public static final class HeartbeatEventHolder {
        public HeartbeatEvent event;
    }

    private static final class BufferExceptionHandler
            implements ExceptionHandler<HeartbeatEventHolder> {

        private static final org.slf4j.Logger logger =
                org.slf4j.LoggerFactory.getLogger(BufferExceptionHandler.class);

        @Override
        public void handleEventException(Throwable ex, long sequence, HeartbeatEventHolder holder) {
            logger.error("Disruptor handler exception at sequence={}", sequence, ex);
        }

        @Override
        public void handleOnStartException(Throwable ex) {
            logger.error("Disruptor failed to start", ex);
            throw new RuntimeException(ex);
        }

        @Override
        public void handleOnShutdownException(Throwable ex) {
            logger.warn("Disruptor shutdown error", ex);
        }
    }
}
