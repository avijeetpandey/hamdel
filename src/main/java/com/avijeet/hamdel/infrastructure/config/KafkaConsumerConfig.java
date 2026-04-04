package com.avijeet.hamdel.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration — batch mode with manual commit for exactly-once semantics.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${hamdel.kafka.consumer-group:hamdel-kpi-group}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, byte[]> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,     bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,              groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,     "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,    false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,      500);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG,       1024);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,     500);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, byte[]> batchKafkaListenerContainerFactory(
            ConsumerFactory<String, byte[]> cf) {
        ConcurrentKafkaListenerContainerFactory<String, byte[]> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(cf);
        factory.setBatchListener(true);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        return factory;
    }
}
