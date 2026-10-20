package com.fooddelivery.inventory.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fooddelivery.inventory.service.InventoryException;
import com.fooddelivery.inventory.service.InventoryService;
import com.fooddelivery.inventory.service.InventoryService.Line;
import com.fooddelivery.inventory.service.InventoryService.ReservationResult;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by order-service, never by the browser (the gateway blocks /internal/**
 * and InternalKeyFilter checks the shared key).
 */
@RestController
@RequestMapping("/internal/inventory/reservations")
public class InternalReservationController {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemRequest(String foodId, int quantity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReserveRequest(String orderId, List<ItemRequest> items) {}

    private final InventoryService inventory;

    public InternalReservationController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResult>> reserve(@RequestBody(required = false) ReserveRequest body) {
        if (body == null || body.items() == null) {
            throw new InventoryException(HttpStatus.BAD_REQUEST, "Invalid request");
        }
        List<Line> lines = body.items().stream()
                .map(i -> i == null ? null : new Line(i.foodId(), i.quantity()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(inventory.reserve(parseOrderId(body.orderId()), lines)));
    }

    @PostMapping("/{orderId}/commit")
    public ResponseEntity<ApiResponse<ReservationResult>> commit(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok(inventory.commit(parseOrderId(orderId))));
    }

    @PostMapping("/{orderId}/release")
    public ResponseEntity<ApiResponse<ReservationResult>> release(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.ok(inventory.release(parseOrderId(orderId))));
    }

    private static UUID parseOrderId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InventoryException(HttpStatus.BAD_REQUEST, "Invalid order id");
        }
    }
}
