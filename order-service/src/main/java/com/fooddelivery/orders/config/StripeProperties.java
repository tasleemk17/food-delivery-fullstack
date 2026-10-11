package com.fooddelivery.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("stripe")
public record StripeProperties(
        @DefaultValue("") String secretKey,
        @DefaultValue("") String webhookSecret) {

    public boolean hasSecretKey() {
        return !secretKey.isBlank();
    }

    public boolean hasWebhookSecret() {
        return !webhookSecret.isBlank();
    }
}
