package com.avijeet.hamdel.integration;

import com.avijeet.hamdel.adapter.inbound.rest.HeartbeatRequest;
import com.avijeet.hamdel.adapter.outbound.persistence.repository.HeartbeatEventJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("integration-test")
class HeartbeatIngestionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("hamdel")
            .withUsername("hamdel")
            .withPassword("hamdel");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",          postgres::getJdbcUrl);
        registry.add("spring.datasource.username",     postgres::getUsername);
        registry.add("spring.datasource.password",     postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired MockMvc mvc;
    @Autowired HeartbeatEventJpaRepository jpaRepository;

    @Test
    @DisplayName("POST /api/v1/heartbeat (JSON) → 202 → event persisted in PostgreSQL")
    void ingestJson_eventPersistedToDatabase() throws Exception {
        String eventId   = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();

        String body = """
                {
                  "eventId": "%s",
                  "sessionId": "%s",
                  "clientId": "client-integration",
                  "contentId": "content-integration",
                  "timestampMs": %d,
                  "videoStartTimeMs": 312.5,
                  "playbackFailed": false,
                  "rebufferDurationMs": 0,
                  "playbackDurationMs": 120000
                }
                """.formatted(eventId, sessionId, System.currentTimeMillis());

        mvc.perform(post("/api/v1/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(jpaRepository.existsByEventId(eventId)).isTrue());
    }

    @Test
    @DisplayName("Duplicate event_id is idempotent — no DB constraint violation")
    void ingestJson_duplicateEventIdIsIdempotent() throws Exception {
        String eventId   = UUID.randomUUID().toString();
        String sessionId = UUID.randomUUID().toString();

        String body = """
                {
                  "eventId": "%s",
                  "sessionId": "%s",
                  "clientId": "client-idempotency",
                  "contentId": "content-idempotency",
                  "timestampMs": %d,
                  "videoStartTimeMs": 200,
                  "playbackFailed": false,
                  "rebufferDurationMs": 0,
                  "playbackDurationMs": 60000
                }
                """.formatted(eventId, sessionId, System.currentTimeMillis());

        mvc.perform(post("/api/v1/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        mvc.perform(post("/api/v1/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted());

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(jpaRepository.findAll().stream()
                        .filter(e -> e.getEventId().equals(eventId))
                        .count()).isEqualTo(1));
    }
}
