package com.avijeet.hamdel.infrastructure.config;

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
        return new NewTopic(topic, partitions, (short) 3)
                .configs(Map.of("min.insync.replicas", "2"));
    }
}
