package com.fooddelivery.orders.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fooddelivery.orders.node.CartSnapshot;
import com.fooddelivery.orders.service.OrderException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PricingServiceTest {

    private final PricingService pricing = new PricingService();

    private static CartSnapshot.Food food(String id, String price) {
        return new CartSnapshot.Food(id, "Food " + id, new BigDecimal(price));
    }

    @Test
    void totalIsItemsPlusDeliveryUsingServerPrices() {
        CartSnapshot s = new CartSnapshot(
                Map.of("a", BigDecimal.valueOf(2), "b", BigDecimal.ONE),
                List.of(food("a", "120"), food("b", "90.50")));
        PricedOrder p = pricing.price(s);
        assertThat(p.amount()).isEqualByComparingTo("380.50"); // 240 + 90.50 + 50
        assertThat(p.items()).hasSize(2);
    }

    @Test
    void zeroQuantityEntriesAreIgnored() {
        CartSnapshot s = new CartSnapshot(
                Map.of("a", BigDecimal.ONE, "b", BigDecimal.ZERO),
                List.of(food("a", "100"), food("b", "10")));
        assertThat(pricing.price(s).items()).hasSize(1);
    }

    @Test
    void emptyCartIs400() {
        assertThatThrownBy(() -> pricing.price(new CartSnapshot(Map.of(), List.of())))
                .isInstanceOf(OrderException.class)
                .hasMessage("Your cart is empty")
                .extracting(e -> ((OrderException) e).status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void fractionalOrTooLargeQuantityIs400() {
        for (String q : new String[] {"1.5", "51", "-1"}) {
            CartSnapshot s = new CartSnapshot(Map.of("a", new BigDecimal(q)), List.of(food("a", "10")));
            if (q.equals("-1")) {
                // negative counts as "not an item" -> empty cart
                assertThatThrownBy(() -> pricing.price(s)).hasMessage("Your cart is empty");
            } else {
                assertThatThrownBy(() -> pricing.price(s)).hasMessage("Invalid quantity in cart");
            }
        }
    }

    @Test
    void deletedFoodIs409() {
        CartSnapshot s = new CartSnapshot(Map.of("gone", BigDecimal.ONE), List.of());
        assertThatThrownBy(() -> pricing.price(s))
                .isInstanceOf(UnavailableItemException.class)
                .extracting(e -> ((OrderException) e).status()).isEqualTo(HttpStatus.CONFLICT);
    }
}
