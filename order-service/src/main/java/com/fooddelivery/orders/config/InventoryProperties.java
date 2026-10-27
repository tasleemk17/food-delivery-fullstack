package com.fooddelivery.orders.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where inventory-service is and how long we wait for it. */
@ConfigurationProperties("inventory")
public record InventoryProperties(
        @DefaultValue("http://localhost:8082") String baseUrl,
        @DefaultValue("") String internalKey,
        @DefaultValue("2s") Duration connectTimeout,
        @DefaultValue("3s") Duration readTimeout) {
}
