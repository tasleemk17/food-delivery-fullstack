package com.fooddelivery.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The real secret comes from JWT_SECRET; tests use a fixed one
@SpringBootTest(properties = "jwt.secret=test-secret-for-gateway-unit-tests-0123456789abcdef")
class GatewayApplicationTests {

    // Fails if application.yml or the route configuration cannot be loaded.
    @Test
    void contextLoads() {
    }
}
