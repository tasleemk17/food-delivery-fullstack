package com.fooddelivery.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fooddelivery.orders.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** The conditional UPDATEs that make payment idempotent, against real PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class OrderRepositoryPaymentTest {

    @Autowired OrderRepository orders;
    @Autowired TestEntityManager em;

    private UUID save(PaymentMethod method, OrderStatus status) {
        OrderEntity o = new OrderEntity("user-1", method, status, OrderEntityTest.address(), Instant.now());
        o.addItem("f1", "Salad", new BigDecimal("120.00"), 2);
        o.setAmount(new BigDecimal("290.00"));
        UUID id = orders.saveAndFlush(o).getId();
        em.clear();
        return id;
    }

    private OrderEntity reload(UUID id) {
        em.clear();
        return orders.findWithItemsById(id).orElseThrow();
    }

    @Test
    void markPaidWorksOnceThenIsANoOp() {
        UUID id = save(PaymentMethod.STRIPE, OrderStatus.PAYMENT_PENDING);

        assertThat(orders.markPaidIfUnpaid(id)).isEqualTo(1);
        assertThat(orders.markPaidIfUnpaid(id)).isEqualTo(0); // the second caller loses the race

        OrderEntity o = reload(id);
        assertThat(o.isPaid()).isTrue();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.FOOD_PROCESSING);
    }

    @Test
    void expiredSessionFailsAnUnpaidOrderButNeverAPaidOne() {
        UUID unpaid = save(PaymentMethod.STRIPE, OrderStatus.PAYMENT_PENDING);
        UUID paid = save(PaymentMethod.STRIPE, OrderStatus.PAYMENT_PENDING);
        orders.markPaidIfUnpaid(paid);

        assertThat(orders.markFailedIfUnpaid(unpaid)).isEqualTo(1);
        assertThat(orders.markFailedIfUnpaid(paid)).isEqualTo(0);

        assertThat(reload(unpaid).getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(reload(paid).getStatus()).isEqualTo(OrderStatus.FOOD_PROCESSING);
    }

    @Test
    void unpaidStripeOrderCannotBeMovedIntoDelivery() {
        UUID id = save(PaymentMethod.STRIPE, OrderStatus.PAYMENT_PENDING);
        assertThat(orders.updateStatusIfPayable(id, OrderStatus.DELIVERED)).isEqualTo(0);

        orders.markPaidIfUnpaid(id);
        assertThat(orders.updateStatusIfPayable(id, OrderStatus.DELIVERED)).isEqualTo(1);
        assertThat(reload(id).getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void codOrderCanBeMovedWithoutPayment() {
        UUID id = save(PaymentMethod.COD, OrderStatus.FOOD_PROCESSING);
        assertThat(orders.updateStatusIfPayable(id, OrderStatus.OUT_FOR_DELIVERY)).isEqualTo(1);
    }

    @Test
    void unknownOrderUpdatesNothing() {
        assertThat(orders.updateStatusIfPayable(UUID.randomUUID(), OrderStatus.DELIVERED)).isEqualTo(0);
    }

    @Test
    void storesTheStripeSessionId() {
        UUID id = save(PaymentMethod.STRIPE, OrderStatus.PAYMENT_PENDING);
        orders.setStripeSessionId(id, "cs_test_123");
        assertThat(reload(id).getStripeSessionId()).isEqualTo("cs_test_123");
    }
}
