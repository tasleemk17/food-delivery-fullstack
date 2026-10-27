package com.fooddelivery.notifications.notify;

import com.fooddelivery.notifications.events.OrderEvent;
import com.fooddelivery.notifications.mail.EmailMessage;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Decides whether an order event deserves an email, and writes it.
 *
 * | Event                 | Email                                                       |
 * |-----------------------|-------------------------------------------------------------|
 * | ORDER_PLACED, COD     | "Order confirmed" (cash on delivery needs no payment step)   |
 * | ORDER_PLACED, STRIPE  | none: the customer is still paying, "paid" will confirm it   |
 * | ORDER_PAID            | "Payment received"                                          |
 * | ORDER_PAYMENT_FAILED  | "Payment failed"                                            |
 * | ORDER_STATUS_CHANGED  | "Your order is Out for delivery / Delivered ..."            |
 * | anything else         | none (a new event type must not break this service)         |
 */
@Component
public class EmailComposer {

    private static final String RUPEE = "₹";

    public Optional<EmailMessage> compose(OrderEvent event) {
        if (event == null || event.type() == null || event.order() == null) {
            return Optional.empty();
        }
        OrderEvent.Order order = event.order();
        if (order.email() == null || order.email().isBlank()) {
            return Optional.empty();
        }
        String ref = reference(order);

        return switch (event.type()) {
            case "ORDER_PLACED" -> "COD".equals(order.paymentMethod())
                    ? Optional.of(email(order, "Order confirmed #" + ref,
                            "Thank you! We have received your order and the kitchen is getting it ready. "
                                    + "You pay when it arrives."))
                    : Optional.empty();
            case "ORDER_PAID" -> Optional.of(email(order, "Payment received for order #" + ref,
                    "Your payment was received and your order is confirmed. The kitchen is getting it ready."));
            case "ORDER_PAYMENT_FAILED" -> Optional.of(email(order, "Payment failed for order #" + ref,
                    "Your payment did not go through, so this order was not placed. "
                            + "Your cart is still saved, so you can try again whenever you like."));
            case "ORDER_STATUS_CHANGED" -> Optional.of(email(order,
                    "Your order #" + ref + ": " + label(order),
                    statusHeadline(order)));
            default -> Optional.empty();
        };
    }

    private static String statusHeadline(OrderEvent.Order order) {
        return switch (order.status() == null ? "" : order.status()) {
            case "FOOD_PROCESSING" -> "The kitchen is preparing your order.";
            case "OUT_FOR_DELIVERY" -> "Your order is on its way to you.";
            case "DELIVERED" -> "Your order was delivered. Enjoy your meal!";
            default -> "The status of your order is now: " + label(order) + ".";
        };
    }

    private static String label(OrderEvent.Order order) {
        return order.statusLabel() != null ? order.statusLabel() : String.valueOf(order.status());
    }

    /** The first 8 characters of the order id: short enough to read out on the phone. */
    static String reference(OrderEvent.Order order) {
        String id = String.valueOf(order.orderId());
        return id.substring(0, Math.min(8, id.length())).toUpperCase(Locale.ROOT);
    }

    private static EmailMessage email(OrderEvent.Order order, String subject, String headline) {
        StringBuilder body = new StringBuilder();
        String name = order.firstName() == null || order.firstName().isBlank() ? "there" : order.firstName().trim();
        body.append("Hi ").append(name).append(",\n\n");
        body.append(headline).append("\n\n");
        body.append("Order #").append(reference(order)).append('\n');
        body.append("------------------------------\n");

        BigDecimal itemsTotal = BigDecimal.ZERO;
        for (OrderEvent.Item item : order.items()) {
            BigDecimal line = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()));
            itemsTotal = itemsTotal.add(line);
            body.append(String.format(Locale.ENGLISH, "%d x %s    %s%n", item.quantity(), item.name(), money(line)));
        }
        BigDecimal delivery = order.amount().subtract(itemsTotal);
        if (delivery.signum() > 0) {
            body.append("Delivery    ").append(money(delivery)).append('\n');
        }
        body.append("------------------------------\n");
        body.append("Total    ").append(money(order.amount())).append('\n');
        body.append("Payment: ").append("COD".equals(order.paymentMethod()) ? "Cash on delivery" : "Paid online")
                .append("\n\n");
        body.append("Thank you for ordering with Tomato.\n");
        return new EmailMessage(order.email().trim(), subject, body.toString());
    }

    private static String money(BigDecimal amount) {
        return String.format(Locale.ENGLISH, RUPEE + "%,.2f", amount);
    }
}
