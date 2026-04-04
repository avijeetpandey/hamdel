package com.avijeet.hamdel.infrastructure.config;

import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

/**
 * Configures Tomcat to use Virtual Threads (Project Loom) for request handling.
 * Each inbound HTTP request runs on a virtual thread, enabling high concurrency
 * without a large thread pool.
 */
@Configuration
public class VirtualThreadConfig {

    @Bean
    public TomcatProtocolHandlerCustomizer<?> virtualThreadTomcatCustomizer() {
        return protocolHandler ->
                protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
}
