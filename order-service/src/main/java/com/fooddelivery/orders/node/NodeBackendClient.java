package com.fooddelivery.orders.node;

import com.fooddelivery.orders.config.NodeProperties;
import com.fooddelivery.orders.service.OrderException;
import com.fooddelivery.orders.service.UpstreamUnavailableException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Calls Node's /internal endpoints with the shared key.
 *
 * Every call has a short connect and read timeout. If Node is down or slow we
 * fail fast with UpstreamUnavailableException (HTTP 503) instead of letting the
 * request hang, and the caller has not saved anything yet.
 */
@Component
public class NodeBackendClient implements CartPort {

    private static final Logger log = LoggerFactory.getLogger(NodeBackendClient.class);

    private final RestClient client;

    public NodeBackendClient(NodeProperties props, RestClient.Builder builder) {
        // HTTP/1.1 only: Express does not speak the h2c upgrade the JDK client tries by default.
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
    public CartSnapshot fetchCart(String userId) {
        NodeCartResponse body = call("fetch cart", () -> client.get()
                .uri("/internal/users/{userId}/cart", userId)
                .retrieve()
                .body(NodeCartResponse.class));
        if (body == null || !body.success()) {
            throw new UpstreamUnavailableException("Node returned an unusable cart response", null);
        }
        List<CartSnapshot.Food> foods = body.foods() == null ? List.of() : body.foods().stream()
                .map(f -> new CartSnapshot.Food(f.id(), f.name(), f.price()))
                .toList();
        return new CartSnapshot(body.cart(), foods);
    }

    @Override
    public void clearCart(String userId) {
        call("clear cart", () -> client.post()
                .uri("/internal/users/{userId}/cart/clear", userId)
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public void removeUnavailable(String userId) {
        call("remove unavailable items", () -> client.post()
                .uri("/internal/users/{userId}/cart/remove-unavailable", userId)
                .retrieve()
                .toBodilessEntity());
    }

    /** Turns every way a call can fail into the right exception. */
    private <T> T call(String what, java.util.function.Supplier<T> request) {
        try {
            return request.get();
        } catch (ResourceAccessException e) {
            // Connection refused, DNS failure, connect or read timeout
            throw new UpstreamUnavailableException("Node unreachable while trying to " + what, e);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                throw new OrderException(HttpStatus.NOT_FOUND, "User not found");
            }
            // 5xx: Node is unwell. 401/503 from the key check: a configuration problem.
            // None of these are the customer's fault, so they get the same 503.
            log.error("Node answered {} while trying to {}", e.getStatusCode(), what);
            throw new UpstreamUnavailableException("Node answered " + e.getStatusCode() + " while trying to " + what, e);
        }
    }

    private record NodeCartResponse(boolean success, Map<String, java.math.BigDecimal> cart, List<NodeFood> foods) {
    }

    private record NodeFood(String id, String name, java.math.BigDecimal price) {
    }
}
