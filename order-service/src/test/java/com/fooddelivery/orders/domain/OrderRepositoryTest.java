package com.fooddelivery.orders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fooddelivery.orders.TestcontainersConfiguration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Runs against a real PostgreSQL (Testcontainers) with the Flyway schema,
 * not an in-memory database, so column types and constraints are the real ones.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orders;

    @Autowired
    private TestEntityManager entityManager;

    private OrderEntity order(String userId, Instant createdAt) {
        OrderEntity order = new OrderEntity(userId, PaymentMethod.COD, OrderStatus.FOOD_PROCESSING,
                OrderEntityTest.address(), createdAt);
        order.addItem("64b0000000000000000000f1", "Greek Salad", new BigDecimal("120.00"), 2);
        order.addItem("64b0000000000000000000f2", "Veg Roll", new BigDecimal("90.00"), 1);
        order.setAmount(new BigDecimal("380.00"));
        return order;
    }

    @Test
    void savesAnOrderWithItsItemsAndReadsThemBack() {
        OrderEntity saved = orders.saveAndFlush(order("user-1", Instant.now()));
        entityManager.clear(); // force a real read from the database

        List<OrderEntity> found = orders.findByUserIdOrderByCreatedAtDesc("user-1");

        assertThat(found).hasSize(1);
        OrderEntity order = found.get(0);
        assertThat(order.getId()).isEqualTo(saved.getId());
        assertThat(order.getAmount()).isEqualByComparingTo("380");
        assertThat(order.getItems()).extracting(OrderItemEntity::getName).containsExactly("Greek Salad", "Veg Roll");
        assertThat(order.getAddress().getCity()).isEqualTo("Pune");
    }

    @Test
    void returnsOnlyThatUsersOrdersNewestFirst() {
        Instant now = Instant.now();
        orders.save(order("user-1", now.minusSeconds(60)));
        orders.save(order("user-1", now));
        orders.save(order("user-2", now));
        orders.flush();
        entityManager.clear();

        List<OrderEntity> found = orders.findByUserIdOrderByCreatedAtDesc("user-1");

        assertThat(found).hasSize(2);
        assertThat(found.get(0).getCreatedAt()).isAfter(found.get(1).getCreatedAt());
        assertThat(found).allMatch(o -> o.getUserId().equals("user-1"));
    }

    @Test
    void adminListReturnsEveryOrderOldestFirst() {
        Instant now = Instant.now();
        orders.save(order("user-2", now));
        orders.save(order("user-1", now.minusSeconds(60)));
        orders.flush();
        entityManager.clear();

        List<OrderEntity> found = orders.findAllByOrderByCreatedAtAsc();

        assertThat(found).hasSize(2);
        assertThat(found.get(0).getUserId()).isEqualTo("user-1");
    }

    @Test
    void theDatabaseRejectsANegativeAmountEvenIfJavaChecksAreBypassed() {
        OrderEntity saved = orders.saveAndFlush(order("user-1", Instant.now()));

        // Bypass the entity's own checks with plain SQL: the CHECK constraint must still hold
        assertThatThrownBy(() -> {
            entityManager.getEntityManager()
                    .createNativeQuery("UPDATE orders SET amount = -1 WHERE id = :id")
                    .setParameter("id", saved.getId())
                    .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    void legacyIdMustBeUnique() {
        OrderEntity first = order("user-1", Instant.now());
        first.setLegacyId("6701a2b3c4d5e6f708091a2b");
        orders.saveAndFlush(first);

        OrderEntity second = order("user-1", Instant.now());
        second.setLegacyId("6701a2b3c4d5e6f708091a2b");

        assertThatThrownBy(() -> orders.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
