package com.avijeet.hamdel.infrastructure.config;

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
 * Kafka producer configuration.
 * - acks=all for zero data loss
 * - min.insync.replicas handled at broker; replication.factor=3 set in admin config
 * - Custom SessionIdPartitioner for session-based ordering
 * - Optimised batching: linger.ms=5, batch.size=65536
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
        props.put(ProducerConfig.LINGER_MS_CONFIG,            5);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG,           65536);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG,     "lz4");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,   true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG,
                com.avijeet.hamdel.adapter.outbound.kafka.SessionIdPartitioner.class);
        props.put(ProducerConfig.RETRIES_CONFIG,              Integer.MAX_VALUE);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, byte[]> kafkaTemplate(ProducerFactory<String, byte[]> pf) {
        return new KafkaTemplate<>(pf);
    }
}
