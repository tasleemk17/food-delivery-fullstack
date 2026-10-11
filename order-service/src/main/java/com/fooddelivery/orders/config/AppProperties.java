package com.fooddelivery.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app")
public record AppProperties(@DefaultValue("http://localhost:5173") String frontendUrl) {
}
