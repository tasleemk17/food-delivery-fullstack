package com.fooddelivery.orders.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fooddelivery.orders.domain.Address;
import com.fooddelivery.orders.service.OrderException;
import com.fooddelivery.orders.service.OrderService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Write endpoints, same paths and JSON as the Node backend. Anything the browser
 * sends about items or prices is ignored: the cart is priced from Node's data.
 */
@RestController
@RequestMapping("/api/order")
public class OrderCommandController {

    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLE = "X-User-Role";
    private static final String NO_AUTH = "Not authorized, please log in again";

    private final OrderService service;

    public OrderCommandController(OrderService service) {
        this.service = service;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlaceRequest(AddressRequest address) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VerifyRequest(String orderId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusRequest(String orderId, String status) {}

    public record PlaceResponse(boolean success, @JsonProperty("session_url") String sessionUrl) {}

    @PostMapping("/place")
    public ResponseEntity<?> place(@RequestHeader(value = USER_ID, required = false) String userId,
                                   @RequestBody(required = false) PlaceRequest body) {
        if (blank(userId)) {
            return unauthorized();
        }
        Address address = addressOf(body);
        if (address == null) {
            return badAddress();
        }
        String url = service.placeStripe(userId, address);
        return ResponseEntity.ok(new PlaceResponse(true, url));
    }

    @PostMapping("/placecod")
    public ResponseEntity<?> placeCod(@RequestHeader(value = USER_ID, required = false) String userId,
                                      @RequestBody(required = false) PlaceRequest body) {
        if (blank(userId)) {
            return unauthorized();
        }
        Address address = addressOf(body);
        if (address == null) {
            return badAddress();
        }
        service.placeCod(userId, address);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "message", "Order Placed"));
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestHeader(value = USER_ID, required = false) String userId,
                                    @RequestBody(required = false) VerifyRequest body) {
        if (blank(userId)) {
            return unauthorized();
        }
        UUID orderId = parseUuid(body == null ? null : body.orderId());
        if (orderId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid order"));
        }
        boolean paid = service.verify(userId, orderId);
        return ResponseEntity.ok(Map.of("success", paid, "message", paid ? "Paid" : "Not Paid"));
    }

    @PostMapping("/status")
    public ResponseEntity<?> status(@RequestHeader(value = USER_ID, required = false) String userId,
                                    @RequestHeader(value = USER_ROLE, required = false) String role,
                                    @RequestBody(required = false) StatusRequest body) {
        if (blank(userId)) {
            return unauthorized();
        }
        if (!"admin".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Admin access only"));
        }
        if (body == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid status"));
        }
        // Status is checked first, like the Node backend
        if (com.fooddelivery.orders.domain.OrderStatus.fromLabel(body.status()) == null
                || !com.fooddelivery.orders.domain.OrderStatus.fromLabel(body.status()).isAdminSettable()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid status"));
        }
        UUID orderId = parseUuid(body.orderId());
        if (orderId == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid order"));
        }
        service.updateStatus(orderId, body.status());
        return ResponseEntity.ok(Map.of("success", true, "message", "Status Updated"));
    }

    /**
     * Stripe calls this directly. The body must stay a raw String: the signature
     * is computed over the exact bytes, so it must not be parsed and re-serialised.
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody String payload,
                                     @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        try {
            service.handleWebhook(payload, signature);
            return ResponseEntity.ok(Map.of("received", true));
        } catch (OrderException e) {
            throw e; // bad signature (400) / not configured (503): handled by the advice
        } catch (RuntimeException e) {
            // Non-2xx makes Stripe retry the event later
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("received", false));
        }
    }

    private static Address addressOf(PlaceRequest body) {
        return body == null || body.address() == null ? null : body.address().toAddressOrNull();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static UUID parseUuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(NO_AUTH));
    }

    private static ResponseEntity<?> badAddress() {
        return ResponseEntity.badRequest().body(ApiResponse.error("Please fill in the full delivery address"));
    }
}
