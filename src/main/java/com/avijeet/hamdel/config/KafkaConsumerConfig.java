package com.avijeet.hamdel.config;

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
 * Kafka consumer configuration — batch mode with manual commit.
 *
 * max.poll.records=1000  larger batches reduce per-record overhead
 * fetch.min.bytes=64KB   wait for more data before returning a fetch
 * session.timeout.ms=45s allow longer processing before broker triggers rebalance
 * max.poll.interval.ms=300s upper bound for one poll batch; prevents spurious rebalances
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
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG,      1000);
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG,       65536);
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG,     500);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,    45_000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 15_000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,  300_000);
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
        factory.getContainerProperties().setIdleEventInterval(30_000L);
        return factory;
    }
}
