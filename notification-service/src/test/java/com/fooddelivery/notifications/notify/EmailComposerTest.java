package com.fooddelivery.notifications.notify;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.notifications.TestEvents;
import com.fooddelivery.notifications.events.OrderEvent;
import com.fooddelivery.notifications.mail.EmailMessage;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Plain unit tests: no Spring, no Docker. */
class EmailComposerTest {

    private final EmailComposer composer = new EmailComposer();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private Optional<EmailMessage> compose(String json) throws Exception {
        return composer.compose(mapper.readValue(json, OrderEvent.class));
    }

    @Test
    void aCashOnDeliveryOrderIsConfirmedStraightAway() throws Exception {
        EmailMessage email = compose(TestEvents.codPlaced(UUID.randomUUID())).orElseThrow();

        assertThat(email.to()).isEqualTo("asha@example.com");
        assertThat(email.subject()).isEqualTo("Order confirmed #0B932FBE");
        assertThat(email.body())
                .contains("Hi Asha,")
                .contains("2 x food 7    ₹1,200.00")
                .contains("2 x food 8    ₹700.00")
                .contains("Delivery    ₹50.00")
                .contains("Total    ₹1,950.00")
                .contains("Cash on delivery")
                .contains("Order #0B932FBE");
    }

    @Test
    void aStripeOrderIsNotConfirmedUntilItIsPaid() throws Exception {
        assertThat(compose(TestEvents.stripePlaced(UUID.randomUUID()))).isEmpty();
    }

    @Test
    void paymentReceived() throws Exception {
        EmailMessage email = compose(TestEvents.stripePaid(UUID.randomUUID())).orElseThrow();

        assertThat(email.subject()).isEqualTo("Payment received for order #0B932FBE");
        assertThat(email.body()).contains("payment was received").contains("Paid online");
    }

    @Test
    void paymentFailedTellsTheCustomerTheCartIsSaved() throws Exception {
        EmailMessage email = compose(TestEvents.paymentFailed(UUID.randomUUID())).orElseThrow();

        assertThat(email.subject()).isEqualTo("Payment failed for order #0B932FBE");
        assertThat(email.body()).contains("did not go through").contains("cart is still saved");
    }

    @Test
    void statusChangesAreAnnounced() throws Exception {
        EmailMessage delivered = compose(TestEvents.delivered(UUID.randomUUID())).orElseThrow();
        EmailMessage onTheWay = compose(TestEvents.json(UUID.randomUUID(), "ORDER_STATUS_CHANGED", "COD",
                "OUT_FOR_DELIVERY", "Out for delivery", false)).orElseThrow();

        assertThat(delivered.subject()).isEqualTo("Your order #0B932FBE: Delivered");
        assertThat(delivered.body()).contains("was delivered");
        assertThat(onTheWay.subject()).isEqualTo("Your order #0B932FBE: Out for delivery");
        assertThat(onTheWay.body()).contains("on its way");
    }

    @Test
    void anEventTypeItDoesNotKnowIsIgnoredNotAnError() throws Exception {
        assertThat(compose(TestEvents.json(UUID.randomUUID(), "ORDER_SOMETHING_NEW", "COD", "X", "X", false)))
                .isEmpty();
    }

    @Test
    void withoutAnEmailAddressThereIsNobodyToWriteTo() throws Exception {
        String noEmail = TestEvents.codPlaced(UUID.randomUUID()).replace("asha@example.com", "");

        assertThat(compose(noEmail)).isEmpty();
    }

    @Test
    void withoutANameItSaysHiThere() throws Exception {
        String noName = TestEvents.codPlaced(UUID.randomUUID()).replace("\"firstName\":\"Asha\"", "\"firstName\":null");

        assertThat(compose(noName).orElseThrow().body()).startsWith("Hi there,");
    }
}
