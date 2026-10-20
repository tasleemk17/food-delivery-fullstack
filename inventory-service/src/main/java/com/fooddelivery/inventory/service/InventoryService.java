package com.fooddelivery.inventory.service;

import com.fooddelivery.inventory.domain.ReservationEntity;
import com.fooddelivery.inventory.domain.ReservationItemEntity;
import com.fooddelivery.inventory.domain.ReservationRepository;
import com.fooddelivery.inventory.domain.ReservationStatus;
import com.fooddelivery.inventory.domain.StockItem;
import com.fooddelivery.inventory.domain.StockRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock levels and reservations.
 *
 * A food with no stock row is "not tracked" and is always available, so adding
 * this service does not stop any existing food from being ordered until an
 * admin sets a stock level for it.
 */
@Service
public class InventoryService {

    static final int MAX_QUANTITY_PER_LINE = 1000;
    static final int MAX_STOCK = 1_000_000;
    private static final int MAX_FOOD_ID_LENGTH = 64;

    /** One food and a quantity, in a request or in the result. */
    public record Line(String foodId, int quantity) {}

    /**
     * What happened to an order's reservation.
     * status is RESERVED, COMMITTED or RELEASED, or NOT_TRACKED when the order
     * held no tracked food (so there was nothing to do).
     */
    public record ReservationResult(UUID orderId, String status, List<Line> items) {}

    static final String NOT_TRACKED = "NOT_TRACKED";

    private final StockRepository stock;
    private final ReservationRepository reservations;
    private final Clock clock;

    public InventoryService(StockRepository stock, ReservationRepository reservations, Clock clock) {
        this.stock = stock;
        this.reservations = reservations;
        this.clock = clock;
    }

    // ---- reservations (called by order-service) ----

    /**
     * Holds stock for an order. All or nothing: if any one food is short, the
     * whole transaction rolls back and no stock is taken. Calling it again with
     * the same order id returns the existing reservation instead of taking stock twice.
     */
    @Transactional
    public ReservationResult reserve(UUID orderId, List<Line> requested) {
        Map<String, Integer> wanted = mergeAndValidate(requested);

        Optional<ReservationEntity> existing = reservations.findById(orderId);
        if (existing.isPresent()) {
            if (existing.get().getStatus() == ReservationStatus.RELEASED) {
                throw new InventoryException(HttpStatus.CONFLICT, "This reservation was already released");
            }
            return toResult(existing.get());
        }

        Instant now = Instant.now(clock);
        ReservationEntity reservation = new ReservationEntity(orderId, now);
        // TreeMap order: every request locks rows in the same order, so two orders
        // that share foods cannot block each other in a circle (deadlock)
        for (Map.Entry<String, Integer> line : wanted.entrySet()) {
            String foodId = line.getKey();
            int quantity = line.getValue();
            if (!stock.existsById(foodId)) {
                continue; // not tracked: always available
            }
            if (stock.decrease(foodId, quantity, now) == 0) {
                int available = stock.findById(foodId).map(StockItem::getQuantity).orElse(0);
                throw new InsufficientStockException(foodId, available);
            }
            reservation.addItem(foodId, quantity);
        }
        if (reservation.getItems().isEmpty()) {
            return new ReservationResult(orderId, NOT_TRACKED, List.of());
        }
        reservations.save(reservation);
        return toResult(reservation);
    }

    /** The order was paid: the stock stays taken. Repeating the call is harmless. */
    @Transactional
    public ReservationResult commit(UUID orderId) {
        Optional<ReservationEntity> found = reservations.findById(orderId);
        if (found.isEmpty()) {
            return new ReservationResult(orderId, NOT_TRACKED, List.of());
        }
        ReservationEntity reservation = found.get();
        switch (reservation.getStatus()) {
            case RELEASED -> throw new InventoryException(HttpStatus.CONFLICT, "This reservation was already released");
            case RESERVED -> reservation.changeStatus(ReservationStatus.COMMITTED, Instant.now(clock));
            case COMMITTED -> { /* already done */ }
        }
        return toResult(reservation);
    }

    /** The order will not be paid: put the stock back. Repeating the call puts it back only once. */
    @Transactional
    public ReservationResult release(UUID orderId) {
        Optional<ReservationEntity> found = reservations.findById(orderId);
        if (found.isEmpty()) {
            return new ReservationResult(orderId, NOT_TRACKED, List.of());
        }
        ReservationEntity reservation = found.get();
        switch (reservation.getStatus()) {
            case COMMITTED -> throw new InventoryException(HttpStatus.CONFLICT, "A paid order cannot be released");
            case RELEASED -> { /* already done */ }
            case RESERVED -> {
                Instant now = Instant.now(clock);
                // Change the status BEFORE the stock updates: each update clears the
                // persistence context (after flushing), so a later change would be lost
                reservation.changeStatus(ReservationStatus.RELEASED, now);
                // sorted, for the same lock-order reason as in reserve()
                List<ReservationItemEntity> items = new ArrayList<>(reservation.getItems());
                items.sort((a, b) -> a.getFoodId().compareTo(b.getFoodId()));
                for (ReservationItemEntity item : items) {
                    stock.increase(item.getFoodId(), item.getQuantity(), now);
                }
            }
        }
        return toResult(reservation);
    }

    // ---- stock levels (admin) ----

    /** Sets how many units of a food are in stock. Creates the row if the food was not tracked yet. */
    @Transactional
    public StockItem setStock(String foodId, int quantity) {
        validateFoodId(foodId);
        if (quantity < 0 || quantity > MAX_STOCK) {
            throw new InventoryException(HttpStatus.BAD_REQUEST, "Quantity must be between 0 and " + MAX_STOCK);
        }
        Instant now = Instant.now(clock);
        StockItem item = stock.findById(foodId).orElse(null);
        if (item == null) {
            return stock.save(new StockItem(foodId, quantity, now));
        }
        item.setQuantity(quantity, now);
        return item;
    }

    @Transactional(readOnly = true)
    public List<StockItem> listStock() {
        return stock.findAllByOrderByFoodIdAsc();
    }

    /** For the menu: tracked foods only, true when at least one unit is left. */
    @Transactional(readOnly = true)
    public Map<String, Boolean> availability() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (StockItem item : stock.findAllByOrderByFoodIdAsc()) {
            result.put(item.getFoodId(), item.getQuantity() > 0);
        }
        return result;
    }

    // ---- helpers ----

    private static void validateFoodId(String foodId) {
        if (foodId == null || foodId.isBlank() || foodId.length() > MAX_FOOD_ID_LENGTH) {
            throw new InventoryException(HttpStatus.BAD_REQUEST, "Invalid food id");
        }
    }

    /** Checks every line and adds up repeated foods. Sorted by food id. */
    private static Map<String, Integer> mergeAndValidate(List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new InventoryException(HttpStatus.BAD_REQUEST, "No items to reserve");
        }
        Map<String, Integer> merged = new TreeMap<>();
        for (Line line : lines) {
            if (line == null) {
                throw new InventoryException(HttpStatus.BAD_REQUEST, "Invalid item");
            }
            validateFoodId(line.foodId());
            if (line.quantity() < 1 || line.quantity() > MAX_QUANTITY_PER_LINE) {
                throw new InventoryException(HttpStatus.BAD_REQUEST,
                        "Quantity must be between 1 and " + MAX_QUANTITY_PER_LINE);
            }
            int total = merged.merge(line.foodId(), line.quantity(), Integer::sum);
            if (total > MAX_QUANTITY_PER_LINE) {
                throw new InventoryException(HttpStatus.BAD_REQUEST,
                        "Quantity must be between 1 and " + MAX_QUANTITY_PER_LINE);
            }
        }
        return merged;
    }

    private static ReservationResult toResult(ReservationEntity reservation) {
        List<Line> items = reservation.getItems().stream()
                .map(i -> new Line(i.getFoodId(), i.getQuantity()))
                .sorted((a, b) -> a.foodId().compareTo(b.foodId()))
                .toList();
        return new ReservationResult(reservation.getOrderId(), reservation.getStatus().name(), items);
    }
}
