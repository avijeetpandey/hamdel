package com.avijeet.hamdel.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration — optimised for maximum throughput and durability.
 *
 * linger.ms=20          wider batching window for higher msg/s per send
 * compression=snappy    lower CPU than lz4 for protobuf payloads
 * max.block.ms=5s       fail fast when broker is unreachable so the circuit breaker trips quickly
 * acks=all + idempotence + retries=MAX_INT — zero data loss guarantee
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, byte[]> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,    bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG,                 "all");
        props.put(ProducerConfig.LINGER_MS_CONFIG,            20);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG,           65536);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG,     "snappy");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,   true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.RETRIES_CONFIG,              Integer.MAX_VALUE);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG,  120_000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG,   30_000);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG,         5_000);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG,        128L * 1024 * 1024);
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG,
                com.avijeet.hamdel.kafka.SessionIdPartitioner.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, byte[]> kafkaTemplate(ProducerFactory<String, byte[]> pf) {
        return new KafkaTemplate<>(pf);
    }
}
