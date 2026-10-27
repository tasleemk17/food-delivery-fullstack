package com.fooddelivery.inventory.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fooddelivery.inventory.TestcontainersConfiguration;
import com.fooddelivery.inventory.domain.ReservationRepository;
import com.fooddelivery.inventory.domain.ReservationStatus;
import com.fooddelivery.inventory.domain.StockItem;
import com.fooddelivery.inventory.domain.StockRepository;
import com.fooddelivery.inventory.events.InventoryEventProcessor.Result;
import com.fooddelivery.inventory.service.InventoryService;
import com.fooddelivery.inventory.service.InventoryService.Line;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** A real PostgreSQL with Kafka switched off: what does ONE event do to the reserved stock? */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(TestcontainersConfiguration.class)
class InventoryEventProcessorTest {

    @Autowired
    InventoryEventProcessor processor;
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

    private UUID reserveTwoOfFive() {
        inventory.setStock("A", 5);
        UUID order = UUID.randomUUID();
        inventory.reserve(order, List.of(new Line("A", 2)));
        return order;
    }

    private int stockOfA() {
        return stock.findById("A").map(StockItem::getQuantity).orElseThrow();
    }

    private ReservationStatus statusOf(UUID order) {
        return reservations.findById(order).orElseThrow().getStatus();
    }

    @Test
    void aPaidOrderCommitsItsStockAndTheStockStaysTaken() {
        UUID order = reserveTwoOfFive();

        assertThat(processor.process(TestEvents.paid(order))).isEqualTo(Result.COMMITTED);

        assertThat(statusOf(order)).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(stockOfA()).isEqualTo(3);
    }

    @Test
    void aCashOnDeliveryOrderCommitsStraightAway() {
        UUID order = reserveTwoOfFive();

        assertThat(processor.process(TestEvents.codPlaced(order))).isEqualTo(Result.COMMITTED);

        assertThat(statusOf(order)).isEqualTo(ReservationStatus.COMMITTED);
    }

    @Test
    void aStripeOrderThatIsOnlyPlacedKeepsHoldingItsStockUntilItIsPaid() {
        UUID order = reserveTwoOfFive();

        assertThat(processor.process(TestEvents.stripePlaced(order))).isEqualTo(Result.IGNORED);

        assertThat(statusOf(order)).isEqualTo(ReservationStatus.RESERVED);
        assertThat(stockOfA()).isEqualTo(3);
    }

    @Test
    void aFailedPaymentGivesTheStockBack() {
        UUID order = reserveTwoOfFive();

        assertThat(processor.process(TestEvents.failed(order))).isEqualTo(Result.RELEASED);

        assertThat(statusOf(order)).isEqualTo(ReservationStatus.RELEASED);
        assertThat(stockOfA()).isEqualTo(5);
    }

    @Test
    void theSameEventTwiceChangesNothingTheSecondTime() {
        UUID order = reserveTwoOfFive();

        processor.process(TestEvents.failed(order));
        // Kafka may deliver it again: the stock must not be put back a second time
        processor.process(TestEvents.failed(order));
        assertThat(stockOfA()).isEqualTo(5);

        UUID other = reserveTwoOfFive();
        processor.process(TestEvents.paid(other));
        processor.process(TestEvents.paid(other));
        assertThat(statusOf(other)).isEqualTo(ReservationStatus.COMMITTED);
    }

    @Test
    void anOrderWithNoReservationIsLeftAlone() {
        // All its foods were untracked, so nothing was ever reserved
        assertThat(processor.process(TestEvents.paid(UUID.randomUUID()))).isEqualTo(Result.COMMITTED);
        assertThat(processor.process(TestEvents.failed(UUID.randomUUID()))).isEqualTo(Result.RELEASED);
        assertThat(reservations.count()).isZero();
    }

    @Test
    void aPaymentThatComesInAfterTheStockWasGivenBackIsParkedForAPerson() {
        UUID order = reserveTwoOfFive();
        processor.process(TestEvents.failed(order));

        assertThatThrownBy(() -> processor.process(TestEvents.paid(order)))
                .isInstanceOf(UnprocessableEventException.class);

        // The stock stays released: nothing is silently taken back
        assertThat(statusOf(order)).isEqualTo(ReservationStatus.RELEASED);
        assertThat(stockOfA()).isEqualTo(5);
    }

    @Test
    void aFailedPaymentForAnOrderThatWasAlreadyPaidIsParkedForAPerson() {
        UUID order = reserveTwoOfFive();
        processor.process(TestEvents.paid(order));

        assertThatThrownBy(() -> processor.process(TestEvents.failed(order)))
                .isInstanceOf(UnprocessableEventException.class);

        assertThat(statusOf(order)).isEqualTo(ReservationStatus.COMMITTED);
        assertThat(stockOfA()).isEqualTo(3);
    }

    @Test
    void eventsItDoesNotKnowAreIgnored() {
        UUID order = reserveTwoOfFive();

        assertThat(processor.process(TestEvents.json("ORDER_STATUS_CHANGED", "STRIPE", order)))
                .isEqualTo(Result.IGNORED);
        assertThat(processor.process(TestEvents.json("ORDER_SOMETHING_NEW", "STRIPE", order)))
                .isEqualTo(Result.IGNORED);

        assertThat(statusOf(order)).isEqualTo(ReservationStatus.RESERVED);
    }

    @Test
    void aMessageThatIsNotAValidOrderEventIsRejectedForGood() {
        assertThatThrownBy(() -> processor.process("this is not json"))
                .isInstanceOf(UnprocessableEventException.class);
        assertThatThrownBy(() -> processor.process("{}"))
                .isInstanceOf(UnprocessableEventException.class);
        assertThatThrownBy(() -> processor.process("{\"type\":\"ORDER_PAID\"}"))
                .isInstanceOf(UnprocessableEventException.class);
    }
}
