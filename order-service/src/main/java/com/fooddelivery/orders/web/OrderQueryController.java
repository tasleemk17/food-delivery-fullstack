package com.fooddelivery.orders.web;

import com.fooddelivery.orders.domain.OrderRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoints, at the same paths the Node backend uses so the React apps
 * need no change when the gateway switches over (Step 7).
 *
 * The caller's identity comes from the X-User-Id / X-User-Role headers, which
 * the gateway sets after verifying the JWT (and strips if a client sends them).
 * This service must therefore only be reachable through the gateway.
 */
@RestController
@RequestMapping("/api/order")
public class OrderQueryController {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";

    private final OrderRepository orders;

    public OrderQueryController(OrderRepository orders) {
        this.orders = orders;
    }

    /** The logged-in user's orders, newest first. POST to match the existing frontend call. */
    @PostMapping("/userorders")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> userOrders(
            @RequestHeader(value = USER_ID_HEADER, required = false) String userId) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authorized, please log in again"));
        }
        List<OrderResponse> data = orders.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /** Every order, oldest first (admin panel). */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> listOrders(
            @RequestHeader(value = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(value = USER_ROLE_HEADER, required = false) String role) {

        if (userId == null || userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Not authorized, please log in again"));
        }
        if (!"admin".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Admin access only"));
        }
        List<OrderResponse> data = orders.findAllByOrderByCreatedAtAsc().stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
