package com.fooddelivery.orders.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where the Node backend is and how long we wait for it. */
@ConfigurationProperties("node")
public record NodeProperties(
        @DefaultValue("http://localhost:4000") String baseUrl,
        @DefaultValue("") String internalKey,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout) {
}
