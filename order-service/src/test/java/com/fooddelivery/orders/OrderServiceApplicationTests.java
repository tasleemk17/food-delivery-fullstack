package com.fooddelivery.orders;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderServiceApplicationTests {

    // Starts the whole app against a real PostgreSQL: Flyway must create the
    // tables and Hibernate must agree with them (ddl-auto: validate).
    @Test
    void contextLoads() {
    }
}
