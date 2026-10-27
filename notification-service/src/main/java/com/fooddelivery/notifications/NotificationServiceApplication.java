package com.fooddelivery.notifications;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * notification-service: listens to the order events on Kafka and emails the customer.
 *
 * It never calls order-service. Everything it needs is inside the event, so
 * the two services can be deployed, restarted and scaled on their own.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
