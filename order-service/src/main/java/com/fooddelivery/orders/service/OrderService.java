package com.fooddelivery.orders.service;

import com.fooddelivery.orders.domain.Address;
import com.fooddelivery.orders.domain.OrderEntity;
import com.fooddelivery.orders.domain.OrderRepository;
import com.fooddelivery.orders.domain.OrderStatus;
import com.fooddelivery.orders.domain.PaymentMethod;
import com.fooddelivery.orders.inventory.InventoryPort;
import com.fooddelivery.orders.node.CartPort;
import com.fooddelivery.orders.node.CartSnapshot;
import com.fooddelivery.orders.payment.PaymentGateway;
import com.fooddelivery.orders.payment.PaymentProviderException;
import com.fooddelivery.orders.payment.PaymentRules;
import com.fooddelivery.orders.payment.StripeSessionInfo;
import com.fooddelivery.orders.payment.WebhookEvent;
import com.fooddelivery.orders.pricing.PricedOrder;
import com.fooddelivery.orders.pricing.PricingService;
import com.fooddelivery.orders.pricing.UnavailableItemException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Order placement and payment.
 *
 * Deliberately NOT @Transactional: every repository call is its own short
 * transaction, so a slow Node or Stripe call never holds a database connection.
 * The cart is read (and priced) and the stock is reserved before anything is
 * saved, so if Node or inventory-service is down, or a food is out of stock,
 * the caller gets an error and no order row exists.
 *
 * Stock life cycle: reserved when the order is placed, committed when it is
 * paid (COD: straight away), released when payment fails or expires. Commit
 * and release only change the reservation's label, so a failure there is
 * logged and never fails the order.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final CartPort cart;
    private final PricingService pricing;
    private final PaymentGateway payments;
    private final InventoryPort inventory;
    private final OrderTransitions transitions;
    private final Clock clock;

    public OrderService(OrderRepository orders, CartPort cart, PricingService pricing,
                        PaymentGateway payments, InventoryPort inventory, OrderTransitions transitions,
                        Clock clock) {
        this.orders = orders;
        this.cart = cart;
        this.pricing = pricing;
        this.payments = payments;
        this.inventory = inventory;
        this.transitions = transitions;
        this.clock = clock;
    }

    /** Cash on delivery: hold the stock, save the order, confirm the stock, then clear the cart. */
    public void placeCod(String userId, Address address) {
        PricedOrder priced = priceCart(userId);
        OrderEntity order = newOrder(userId, PaymentMethod.COD, OrderStatus.FOOD_PROCESSING, address, priced);
        reserveStock(order, priced);
        saveOrReleaseStock(order);
        commitStockQuietly(order.getId()); // nothing to wait for: cash on delivery is confirmed
        clearCartQuietly(userId);
    }

    /** Stripe: hold the stock, save a pending order, open a Checkout Session, return its URL. */
    public String placeStripe(String userId, Address address) {
        PricedOrder priced = priceCart(userId);
        OrderEntity order = newOrder(userId, PaymentMethod.STRIPE, OrderStatus.PAYMENT_PENDING, address, priced);
        reserveStock(order, priced);
        saveOrReleaseStock(order);

        List<PaymentGateway.Line> lines = priced.items().stream()
                .map(i -> new PaymentGateway.Line(i.name(), i.unitPrice(), i.quantity()))
                .toList();
        PaymentGateway.CheckoutSession session;
        try {
            session = payments.createCheckoutSession(order.getId(), lines, PricingService.DELIVERY_CHARGE);
        } catch (RuntimeException e) {
            transitions.markFailed(order.getId());
            releaseStockQuietly(order.getId());
            throw e;
        }
        orders.setStripeSessionId(order.getId(), session.id());
        return session.url();
    }

    /** The browser came back from Stripe: confirm with Stripe, then mark paid. */
    public boolean verify(String userId, UUID orderId) {
        OrderEntity order = orders.findWithItemsById(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND, "Order not found"));
        if (order.isPaid()) {
            return true;
        }
        if (order.getStripeSessionId() == null) {
            return false;
        }
        StripeSessionInfo session = payments.retrieveSession(order.getStripeSessionId());
        if (paysFor(session, order)) {
            markPaid(order);
            return true;
        }
        return false;
    }

    /** Stripe server-to-server call. Throws on a bad signature; any other failure makes Stripe retry. */
    public void handleWebhook(String payload, String signature) {
        WebhookEvent event = payments.parseWebhook(payload, signature);
        StripeSessionInfo session = event.session();
        UUID orderId = session == null ? null : parseUuid(session.orderId());
        if (orderId == null) {
            return;
        }
        if ("checkout.session.completed".equals(event.type())) {
            orders.findWithItemsById(orderId)
                    .filter(o -> paysFor(session, o))
                    .ifPresent(this::markPaid);
        } else if ("checkout.session.expired".equals(event.type())) {
            if (transitions.markFailed(orderId)) {
                releaseStockQuietly(orderId); // nobody will pay: give the stock back
            }
        }
    }

    /** Admin: move an order along. Unpaid Stripe orders cannot be moved. */
    public void updateStatus(UUID orderId, String statusLabel) {
        OrderStatus status = OrderStatus.fromLabel(statusLabel);
        if (status == null || !status.isAdminSettable()) {
            throw new OrderException(HttpStatus.BAD_REQUEST, "Invalid status");
        }
        if (!transitions.changeStatus(orderId, status)) {
            throw new OrderException(HttpStatus.CONFLICT, "Order not found or not paid yet");
        }
    }

    // ---------------------------------------------------------------- helpers

    private PricedOrder priceCart(String userId) {
        CartSnapshot snapshot = cart.fetchCart(userId); // throws 503 when Node is down
        try {
            return pricing.price(snapshot);
        } catch (UnavailableItemException e) {
            try {
                cart.removeUnavailable(userId);
            } catch (RuntimeException ignored) {
                log.warn("Could not clean unavailable items from cart of user {}", userId);
            }
            throw e;
        }
    }

    private OrderEntity newOrder(String userId, PaymentMethod method, OrderStatus status,
                                 Address address, PricedOrder priced) {
        OrderEntity order = new OrderEntity(userId, method, status, address, Instant.now(clock));
        priced.items().forEach(i -> order.addItem(i.foodId(), i.name(), i.unitPrice(), i.quantity()));
        order.setAmount(priced.amount());
        return order;
    }

    private boolean paysFor(StripeSessionInfo session, OrderEntity order) {
        List<PaymentGateway.Line> lines = order.getItems().stream()
                .map(i -> new PaymentGateway.Line(i.getName(), i.getUnitPrice(), i.getQuantity()))
                .toList();
        long expected = PaymentRules.orderTotalPaise(lines, PricingService.DELIVERY_CHARGE);
        return PaymentRules.sessionPaysForOrder(session, order.getId(), expected);
    }

    /** Only the call that actually flips paid=false to true confirms the stock and clears the cart. */
    private void markPaid(OrderEntity order) {
        if (transitions.markPaid(order.getId())) {
            commitStockQuietly(order.getId());
            clearCartQuietly(order.getUserId());
        }
    }

    /**
     * Holds the stock for this order. If a food is short the customer is told which one
     * and nothing is saved; if inventory-service is down the caller gets 503.
     */
    private void reserveStock(OrderEntity order, PricedOrder priced) {
        List<InventoryPort.StockLine> lines = priced.items().stream()
                .map(i -> new InventoryPort.StockLine(i.foodId(), i.quantity()))
                .toList();
        try {
            inventory.reserve(order.getId(), lines);
        } catch (OutOfStockException e) {
            throw new OrderException(HttpStatus.CONFLICT, outOfStockMessage(priced, e));
        }
    }

    private static String outOfStockMessage(PricedOrder priced, OutOfStockException e) {
        String name = priced.items().stream()
                .filter(i -> i.foodId().equals(e.foodId()))
                .map(PricedOrder.PricedItem::name)
                .findFirst()
                .orElse("An item in your cart");
        return e.available() <= 0
                ? name + " is out of stock. Please remove it from your cart and try again."
                : "Only " + e.available() + " left of " + name + ". Please reduce the quantity and try again.";
    }

    /** Saves the order (and its event); if saving fails the stock that was just held is given back. */
    private void saveOrReleaseStock(OrderEntity order) {
        try {
            transitions.place(order);
        } catch (RuntimeException e) {
            releaseStockQuietly(order.getId());
            throw e;
        }
    }

    private void commitStockQuietly(UUID orderId) {
        try {
            inventory.commit(orderId);
        } catch (RuntimeException e) {
            // The order is saved and the stock is already taken; only the label did not change
            log.warn("Could not commit the stock reservation of order {}: {}", orderId, e.getMessage());
        }
    }

    private void releaseStockQuietly(UUID orderId) {
        try {
            inventory.release(orderId);
        } catch (RuntimeException e) {
            log.warn("Could not release the stock reservation of order {}: {}", orderId, e.getMessage());
        }
    }

    private void clearCartQuietly(String userId) {
        try {
            cart.clearCart(userId);
        } catch (RuntimeException e) {
            // The order is saved; a stale cart is a nuisance, not a reason to fail the order
            log.warn("Could not clear cart of user {}: {}", userId, e.getMessage());
        }
    }

    private static UUID parseUuid(String s) {
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
