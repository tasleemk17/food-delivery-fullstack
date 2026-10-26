package com.fooddelivery.orders.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fooddelivery.orders.config.InventoryProperties;
import com.fooddelivery.orders.service.OrderException;
import com.fooddelivery.orders.service.OutOfStockException;
import com.fooddelivery.orders.service.UpstreamUnavailableException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls inventory-service's /internal endpoints with the shared key.
 *
 * Same rules as the Node client: short timeouts, and every way a call can fail
 * becomes the right exception. If inventory-service is down the caller gets 503
 * and nothing has been saved.
 */
@Component
public class InventoryClient implements InventoryPort {

    private static final Logger log = LoggerFactory.getLogger(InventoryClient.class);

    private final RestClient client;

    public InventoryClient(InventoryProperties props, RestClient.Builder builder) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.readTimeout());

        this.client = builder
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .defaultHeader("X-Internal-Key", props.internalKey())
                .build();
    }

    @Override
    public void reserve(UUID orderId, List<StockLine> lines) {
        List<Map<String, Object>> items = lines.stream()
                .map(l -> Map.<String, Object>of("foodId", l.foodId(), "quantity", l.quantity()))
                .toList();
        Map<String, Object> body = Map.of("orderId", orderId.toString(), "items", items);
        call("reserve stock", () -> client.post()
                .uri("/internal/inventory/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void commit(UUID orderId) {
        call("commit stock", () -> client.post()
                .uri("/internal/inventory/reservations/{orderId}/commit", orderId)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void release(UUID orderId) {
        call("release stock", () -> client.post()
                .uri("/internal/inventory/reservations/{orderId}/release", orderId)
                .retrieve()
                .toBodilessEntity());
    }

    /** Turns every way a call can fail into the right exception. */
    private void call(String what, Supplier<?> request) {
        try {
            request.get();
        } catch (ResourceAccessException e) {
            // Connection refused, DNS failure, connect or read timeout
            throw new UpstreamUnavailableException("Inventory unreachable while trying to " + what, e);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 409) {
                throw conflict(e);
            }
            if (status == 400) {
                log.error("Inventory rejected the request while trying to {}: {}", what, e.getResponseBodyAsString());
                throw new OrderException(HttpStatus.BAD_REQUEST, "Invalid order");
            }
            // 5xx, or 401/503 from the key check: a problem on our side, not the customer's
            log.error("Inventory answered {} while trying to {}", e.getStatusCode(), what);
            throw new UpstreamUnavailableException(
                    "Inventory answered " + e.getStatusCode() + " while trying to " + what, e);
        }
    }

    /** 409 is either "not enough stock" (names the food) or a state conflict such as "already released". */
    private static OrderException conflict(RestClientResponseException e) {
        ErrorBody body = null;
        try {
            body = e.getResponseBodyAs(ErrorBody.class);
        } catch (RuntimeException ignored) {
            // fall through to the generic conflict below
        }
        if (body != null && body.data() != null && body.data().foodId() != null) {
            return new OutOfStockException(body.data().foodId(), body.data().available());
        }
        String message = body != null && body.message() != null ? body.message() : "Stock conflict";
        return new OrderException(HttpStatus.CONFLICT, message);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorBody(String message, StockDetail data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record StockDetail(String foodId, int available) {
    }
}
