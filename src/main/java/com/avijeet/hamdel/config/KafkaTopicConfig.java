package com.avijeet.hamdel.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Map;

/**
 * Ensures the heartbeat topic is created with replication factor 3 and
 * min.insync.replicas=2 for durability guarantees.
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${hamdel.kafka.topic:heartbeat-events}")
    private String topic;

    @Value("${hamdel.kafka.partitions:12}")
    private int partitions;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        return new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
    }

    @Bean
    public NewTopic heartbeatTopic() {
        // 24 partitions supports up to 24 concurrent consumer threads; drives KEDA max scale-out.
        return new NewTopic(topic, partitions, (short) 3)
                .configs(Map.of("min.insync.replicas", "2"));
    }

    @Bean
    public NewTopic heartbeatDlt(@Value("${hamdel.kafka.dlt:heartbeat-events-dlt}") String dlt) {
        // Dead-letter topic for poison/undeserializable messages; 7-day retention for inspection.
        return new NewTopic(dlt, 3, (short) 3)
                .configs(Map.of("min.insync.replicas", "2",
                                "retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000)));
    }
}
