package com.fooddelivery.orders.pricing;

import com.fooddelivery.orders.node.CartSnapshot;
import com.fooddelivery.orders.service.OrderException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Prices a cart from the prices the Node backend holds, never from anything the
 * browser sent. Mirrors backend/services/pricing.js.
 */
@Component
public class PricingService {

    public static final BigDecimal DELIVERY_CHARGE = BigDecimal.valueOf(50);
    public static final int MAX_QTY_PER_ITEM = 50;

    public PricedOrder price(CartSnapshot snapshot) {
        Map<String, BigDecimal> cart = snapshot.cart();
        Map<String, CartSnapshot.Food> foods = new HashMap<>();
        for (CartSnapshot.Food f : snapshot.foods()) {
            foods.put(f.id(), f);
        }

        List<PricedOrder.PricedItem> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (Map.Entry<String, BigDecimal> e : cart.entrySet()) {
            BigDecimal qty = e.getValue();
            if (qty == null || qty.signum() <= 0) {
                continue; // a 0 left behind by "remove from cart" is not an item
            }
            if (qty.stripTrailingZeros().scale() > 0 || qty.compareTo(BigDecimal.valueOf(MAX_QTY_PER_ITEM)) > 0) {
                throw new OrderException(HttpStatus.BAD_REQUEST, "Invalid quantity in cart");
            }
            CartSnapshot.Food food = foods.get(e.getKey());
            if (food == null) {
                throw new UnavailableItemException();
            }
            int quantity = qty.intValueExact();
            items.add(new PricedOrder.PricedItem(food.id(), food.name(), food.price(), quantity));
            subtotal = subtotal.add(food.price().multiply(BigDecimal.valueOf(quantity)));
        }

        if (items.isEmpty()) {
            throw new OrderException(HttpStatus.BAD_REQUEST, "Your cart is empty");
        }
        return new PricedOrder(List.copyOf(items), subtotal.add(DELIVERY_CHARGE));
    }
}
