package com.fooddelivery.inventory.web;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fooddelivery.inventory.domain.StockItem;
import com.fooddelivery.inventory.service.InventoryException;
import com.fooddelivery.inventory.service.InventoryService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stock levels for the admin panel, plus a public "is it in stock" list for the menu.
 *
 * Identity comes from the X-User-Id / X-User-Role headers that the gateway sets
 * after verifying the JWT (and strips if a client sends them). This service must
 * therefore only be reachable through the gateway.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    static final String USER_ID_HEADER = "X-User-Id";
    static final String USER_ROLE_HEADER = "X-User-Role";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SetStockRequest(Integer quantity) {}

    public record StockResponse(String foodId, int quantity) {
        static StockResponse from(StockItem item) {
            return new StockResponse(item.getFoodId(), item.getQuantity());
        }
    }

    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    /** Public: tracked foods and whether any unit is left. Quantities are not exposed. */
    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> availability() {
        return ResponseEntity.ok(ApiResponse.ok(inventory.availability()));
    }

    /** Admin: every tracked food with its stock level. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StockResponse>>> list(
            @RequestHeader(value = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(value = USER_ROLE_HEADER, required = false) String role) {
        requireAdmin(userId, role);
        List<StockResponse> data = inventory.listStock().stream().map(StockResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.ok(data));
    }

    /** Admin: set the stock of one food (starts tracking it if it was not tracked yet). */
    @PutMapping("/{foodId}")
    public ResponseEntity<ApiResponse<StockResponse>> setStock(
            @RequestHeader(value = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
            @PathVariable String foodId,
            @RequestBody(required = false) SetStockRequest body) {
        requireAdmin(userId, role);
        if (body == null || body.quantity() == null) {
            throw new InventoryException(HttpStatus.BAD_REQUEST, "Quantity is required");
        }
        return ResponseEntity.ok(ApiResponse.ok(StockResponse.from(inventory.setStock(foodId, body.quantity()))));
    }

    /** Admin: stop tracking a food, so it is always available again. */
    @DeleteMapping("/{foodId}")
    public ResponseEntity<ApiResponse<Void>> stopTracking(
            @RequestHeader(value = USER_ID_HEADER, required = false) String userId,
            @RequestHeader(value = USER_ROLE_HEADER, required = false) String role,
            @PathVariable String foodId) {
        requireAdmin(userId, role);
        inventory.stopTracking(foodId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private static void requireAdmin(String userId, String role) {
        if (userId == null || userId.isBlank()) {
            throw new InventoryException(HttpStatus.UNAUTHORIZED, "Not authorized, please log in again");
        }
        if (!"admin".equals(role)) {
            throw new InventoryException(HttpStatus.FORBIDDEN, "Admin access only");
        }
    }
}
