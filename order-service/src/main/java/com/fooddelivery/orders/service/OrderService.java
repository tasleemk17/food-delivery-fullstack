package com.fooddelivery.orders.service;

import com.fooddelivery.orders.domain.Address;
import com.fooddelivery.orders.domain.OrderEntity;
import com.fooddelivery.orders.domain.OrderRepository;
import com.fooddelivery.orders.domain.OrderStatus;
import com.fooddelivery.orders.domain.PaymentMethod;
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
 * The cart is read (and priced) before anything is saved, so if Node is down
 * the caller gets 503 and no order row exists.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final CartPort cart;
    private final PricingService pricing;
    private final PaymentGateway payments;
    private final Clock clock;

    public OrderService(OrderRepository orders, CartPort cart, PricingService pricing,
                        PaymentGateway payments, Clock clock) {
        this.orders = orders;
        this.cart = cart;
        this.pricing = pricing;
        this.payments = payments;
        this.clock = clock;
    }

    /** Cash on delivery: save the order, then clear the cart. */
    public void placeCod(String userId, Address address) {
        PricedOrder priced = priceCart(userId);
        OrderEntity order = newOrder(userId, PaymentMethod.COD, OrderStatus.FOOD_PROCESSING, address, priced);
        orders.save(order);
        clearCartQuietly(userId);
    }

    /** Stripe: save a pending order, open a Checkout Session, return its URL. */
    public String placeStripe(String userId, Address address) {
        PricedOrder priced = priceCart(userId);
        OrderEntity order = orders.save(
                newOrder(userId, PaymentMethod.STRIPE, OrderStatus.PAYMENT_PENDING, address, priced));

        List<PaymentGateway.Line> lines = priced.items().stream()
                .map(i -> new PaymentGateway.Line(i.name(), i.unitPrice(), i.quantity()))
                .toList();
        PaymentGateway.CheckoutSession session;
        try {
            session = payments.createCheckoutSession(order.getId(), lines, PricingService.DELIVERY_CHARGE);
        } catch (RuntimeException e) {
            orders.markFailedIfUnpaid(order.getId());
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
            orders.markFailedIfUnpaid(orderId);
        }
    }

    /** Admin: move an order along. Unpaid Stripe orders cannot be moved. */
    public void updateStatus(UUID orderId, String statusLabel) {
        OrderStatus status = OrderStatus.fromLabel(statusLabel);
        if (status == null || !status.isAdminSettable()) {
            throw new OrderException(HttpStatus.BAD_REQUEST, "Invalid status");
        }
        if (orders.updateStatusIfPayable(orderId, status) == 0) {
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

    /** Only the call that actually flips paid=false to true clears the cart. */
    private void markPaid(OrderEntity order) {
        if (orders.markPaidIfUnpaid(order.getId()) == 1) {
            clearCartQuietly(order.getUserId());
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
