package com.fooddelivery.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fooddelivery.inventory.TestcontainersConfiguration;
import com.fooddelivery.inventory.domain.ReservationRepository;
import com.fooddelivery.inventory.domain.StockItem;
import com.fooddelivery.inventory.domain.StockRepository;
import com.fooddelivery.inventory.service.InventoryService.Line;
import com.fooddelivery.inventory.service.InventoryService.ReservationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

/** Runs against a real PostgreSQL (Testcontainers), because the point is what the database guarantees. */
// The Kafka listener is switched off: these tests are about the database, not about events
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(TestcontainersConfiguration.class)
class InventoryServiceTest {

    @Autowired
    InventoryService inventory;
    @Autowired
    StockRepository stock;
    @Autowired
    ReservationRepository reservations;

    @BeforeEach
    void cleanDatabase() {
        reservations.deleteAll();
        stock.deleteAll();
    }

    private int stockOf(String foodId) {
        return stock.findById(foodId).map(StockItem::getQuantity).orElseThrow();
    }

    @Test
    void reservingTakesStock() {
        inventory.setStock("A", 5);

        ReservationResult result = inventory.reserve(UUID.randomUUID(), List.of(new Line("A", 2)));

        assertThat(result.status()).isEqualTo("RESERVED");
        assertThat(result.items()).containsExactly(new Line("A", 2));
        assertThat(stockOf("A")).isEqualTo(3);
    }

    @Test
    void ifOneFoodIsShortNothingIsTaken() {
        inventory.setStock("A", 5);
        inventory.setStock("B", 1);
        UUID order = UUID.randomUUID();

        assertThatThrownBy(() -> inventory.reserve(order, List.of(new Line("A", 2), new Line("B", 2))))
                .isInstanceOfSatisfying(InsufficientStockException.class, e -> {
                    assertThat(e.foodId()).isEqualTo("B");
                    assertThat(e.available()).isEqualTo(1);
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                });

        // A was decreased first, but the whole transaction rolled back
        assertThat(stockOf("A")).isEqualTo(5);
        assertThat(stockOf("B")).isEqualTo(1);
        assertThat(reservations.findById(order)).isEmpty();
    }

    @Test
    void aFoodWithoutAStockRowIsAlwaysAvailable() {
        UUID order = UUID.randomUUID();

        ReservationResult result = inventory.reserve(order, List.of(new Line("untracked", 500)));

        assertThat(result.status()).isEqualTo("NOT_TRACKED");
        assertThat(result.items()).isEmpty();
        assertThat(reservations.findById(order)).isEmpty();
    }

    @Test
    void onlyTrackedFoodsAreHeldInAMixedOrder() {
        inventory.setStock("A", 4);

        ReservationResult result = inventory.reserve(UUID.randomUUID(),
                List.of(new Line("A", 1), new Line("untracked", 3)));

        assertThat(result.items()).containsExactly(new Line("A", 1));
        assertThat(stockOf("A")).isEqualTo(3);
    }

    @Test
    void theSameFoodOnTwoLinesIsAddedUp() {
        inventory.setStock("A", 10);

        ReservationResult result = inventory.reserve(UUID.randomUUID(),
                List.of(new Line("A", 1), new Line("A", 2)));

        assertThat(result.items()).containsExactly(new Line("A", 3));
        assertThat(stockOf("A")).isEqualTo(7);
    }

    @Test
    void repeatingTheSameReservationTakesStockOnlyOnce() {
        inventory.setStock("A", 5);
        UUID order = UUID.randomUUID();

        inventory.reserve(order, List.of(new Line("A", 2)));
        ReservationResult again = inventory.reserve(order, List.of(new Line("A", 2)));

        assertThat(again.status()).isEqualTo("RESERVED");
        assertThat(stockOf("A")).isEqualTo(3);
    }

    @Test
    void releasingPutsStockBackExactlyOnce() {
        inventory.setStock("A", 5);
        UUID order = UUID.randomUUID();
        inventory.reserve(order, List.of(new Line("A", 2)));

        assertThat(inventory.release(order).status()).isEqualTo("RELEASED");
        assertThat(stockOf("A")).isEqualTo(5);

        // A retry must not add the stock a second time
        assertThat(inventory.release(order).status()).isEqualTo("RELEASED");
        assertThat(stockOf("A")).isEqualTo(5);
    }

    @Test
    void committingKeepsTheStockTakenAndCanBeRepeated() {
        inventory.setStock("A", 5);
        UUID order = UUID.randomUUID();
        inventory.reserve(order, List.of(new Line("A", 2)));

        assertThat(inventory.commit(order).status()).isEqualTo("COMMITTED");
        assertThat(inventory.commit(order).status()).isEqualTo("COMMITTED");
        assertThat(stockOf("A")).isEqualTo(3);
    }

    @Test
    void aPaidOrderCannotBeReleased() {
        inventory.setStock("A", 5);
        UUID order = UUID.randomUUID();
        inventory.reserve(order, List.of(new Line("A", 2)));
        inventory.commit(order);

        assertThatThrownBy(() -> inventory.release(order))
                .isInstanceOfSatisfying(InventoryException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(stockOf("A")).isEqualTo(3);
    }

    @Test
    void aReleasedReservationCannotBeCommittedOrReservedAgain() {
        inventory.setStock("A", 5);
        UUID order = UUID.randomUUID();
        inventory.reserve(order, List.of(new Line("A", 2)));
        inventory.release(order);

        assertThatThrownBy(() -> inventory.commit(order)).isInstanceOf(InventoryException.class);
        assertThatThrownBy(() -> inventory.reserve(order, List.of(new Line("A", 2))))
                .isInstanceOf(InventoryException.class);
        assertThat(stockOf("A")).isEqualTo(5);
    }

    @Test
    void commitAndReleaseOfAnUnknownOrderDoNothing() {
        assertThat(inventory.commit(UUID.randomUUID()).status()).isEqualTo("NOT_TRACKED");
        assertThat(inventory.release(UUID.randomUUID()).status()).isEqualTo("NOT_TRACKED");
    }

    @Test
    void tenOrdersRacingForThreeUnitsGetExactlyThree() throws Exception {
        inventory.setStock("A", 3);
        int orders = 10;
        ExecutorService pool = Executors.newFixedThreadPool(orders);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < orders; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    inventory.reserve(UUID.randomUUID(), List.of(new Line("A", 1)));
                    return true;
                } catch (InsufficientStockException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get(30, TimeUnit.SECONDS)) {
                succeeded++;
            }
        }
        pool.shutdown();

        // The stock never goes below zero and is never given out twice
        assertThat(succeeded).isEqualTo(3);
        assertThat(stockOf("A")).isZero();
    }

    @Test
    void badRequestsAreRejected() {
        assertThatThrownBy(() -> inventory.reserve(UUID.randomUUID(), List.of()))
                .isInstanceOfSatisfying(InventoryException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> inventory.reserve(UUID.randomUUID(), List.of(new Line("A", 0))))
                .isInstanceOf(InventoryException.class);
        assertThatThrownBy(() -> inventory.reserve(UUID.randomUUID(), List.of(new Line(" ", 1))))
                .isInstanceOf(InventoryException.class);
        assertThatThrownBy(() -> inventory.reserve(UUID.randomUUID(), List.of(new Line("A", 1001))))
                .isInstanceOf(InventoryException.class);
        assertThatThrownBy(() -> inventory.setStock("A", -1)).isInstanceOf(InventoryException.class);
    }

    @Test
    void settingStockCreatesThenUpdatesTheRow() {
        inventory.setStock("A", 5);
        inventory.setStock("A", 9);

        assertThat(stock.count()).isEqualTo(1);
        assertThat(stockOf("A")).isEqualTo(9);
    }

    @Test
    void stoppingTrackingMakesTheFoodAlwaysAvailableAgain() {
        inventory.setStock("A", 1);
        inventory.reserve(UUID.randomUUID(), List.of(new Line("A", 1)));
        assertThatThrownBy(() -> inventory.reserve(UUID.randomUUID(), List.of(new Line("A", 1))))
                .isInstanceOf(InsufficientStockException.class);

        inventory.stopTracking("A");
        inventory.stopTracking("A"); // repeating is harmless

        assertThat(stock.existsById("A")).isFalse();
        assertThat(inventory.reserve(UUID.randomUUID(), List.of(new Line("A", 50))).status())
                .isEqualTo("NOT_TRACKED");
        assertThat(inventory.availability()).isEmpty();
    }

    @Test
    void releasingAfterTrackingStoppedDoesNotFail() {
        inventory.setStock("A", 3);
        UUID order = UUID.randomUUID();
        inventory.reserve(order, List.of(new Line("A", 2)));
        inventory.stopTracking("A");

        assertThat(inventory.release(order).status()).isEqualTo("RELEASED");
        assertThat(stock.existsById("A")).isFalse(); // nothing was put back into a row that is gone
    }

    @Test
    void availabilityShowsOnlyWhetherTrackedFoodsAreInStock() {
        inventory.setStock("A", 0);
        inventory.setStock("B", 2);

        assertThat(inventory.availability()).containsEntry("A", false).containsEntry("B", true).hasSize(2);
    }
}
