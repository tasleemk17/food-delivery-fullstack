package com.fooddelivery.gateway.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the JWT check into the gateway. */
@Configuration
public class SecurityConfig {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<Map<String, Object>>() {};

    /** jwt.secret comes from the JWT_SECRET environment variable (see application.yml). */
    @Bean
    public JwtVerifier jwtVerifier(@Value("${jwt.secret:}") String secret, ObjectMapper objectMapper) {
        return new JwtVerifier(secret, json -> {
            try {
                return objectMapper.readValue(json, JSON_OBJECT);
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("not valid JSON", e);
            }
        }, Clock.systemUTC());
    }

    @Bean
    public RouteAccess routeAccess() {
        return new RouteAccess();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtVerifier jwtVerifier, RouteAccess routeAccess) {
        return new JwtAuthenticationFilter(jwtVerifier, routeAccess);
    }
}
