package com.fooddelivery.notifications.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fooddelivery.notifications.TestEvents;
import com.fooddelivery.notifications.TestcontainersConfiguration;
import com.fooddelivery.notifications.mail.EmailMessage;
import com.fooddelivery.notifications.mail.EmailSender;
import com.fooddelivery.notifications.mail.SmtpEmailSender;
import com.fooddelivery.notifications.notify.OrderEventProcessor.Result;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * A real PostgreSQL, a fake mail server, and Kafka switched off: this is about what the
 * processor does with one message, in particular "exactly once" and "never lost".
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(TestcontainersConfiguration.class)
class OrderEventProcessorTest {

    @Autowired
    OrderEventProcessor processor;
    @Autowired
    NotificationRepository notifications;
    @MockitoBean
    EmailSender sender;

    @BeforeEach
    void cleanDatabase() {
        notifications.deleteAll();
    }

    @Test
    void sendsTheEmailAndRemembersIt() {
        UUID event = UUID.randomUUID();

        assertThat(processor.process(TestEvents.codPlaced(event))).isEqualTo(Result.SENT);

        verify(sender).send(any(EmailMessage.class));
        assertThat(notifications.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getEventId()).isEqualTo(event);
            assertThat(n.getRecipient()).isEqualTo("asha@example.com");
            assertThat(n.getSubject()).isEqualTo("Order confirmed #0B932FBE");
            assertThat(n.getEventType()).isEqualTo("ORDER_PLACED");
            assertThat(n.getOrderId().toString()).isEqualTo(TestEvents.ORDER_ID);
        });
    }

    @Test
    void theSameEventTwiceSendsOneEmail() {
        UUID event = UUID.randomUUID();

        assertThat(processor.process(TestEvents.stripePaid(event))).isEqualTo(Result.SENT);
        // Kafka delivers at least once: the same message can come again
        assertThat(processor.process(TestEvents.stripePaid(event))).isEqualTo(Result.DUPLICATE);

        verify(sender, times(1)).send(any());
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void differentEventsOfTheSameOrderEachGetTheirOwnEmail() {
        processor.process(TestEvents.stripePaid(UUID.randomUUID()));
        processor.process(TestEvents.delivered(UUID.randomUUID()));

        verify(sender, times(2)).send(any());
        assertThat(notifications.findByOrderIdOrderBySentAt(UUID.fromString(TestEvents.ORDER_ID)))
                .extracting(NotificationRecord::getEventType)
                .containsExactly("ORDER_PAID", "ORDER_STATUS_CHANGED");
    }

    @Test
    void anEventThatNeedsNoEmailIsSkipped() {
        assertThat(processor.process(TestEvents.stripePlaced(UUID.randomUUID()))).isEqualTo(Result.SKIPPED);
        assertThat(processor.process(
                TestEvents.json(UUID.randomUUID(), "ORDER_SOMETHING_NEW", "COD", "X", "X", false)))
                .isEqualTo(Result.SKIPPED);

        verify(sender, never()).send(any());
        assertThat(notifications.count()).isZero();
    }

    @Test
    void ifTheMailServerIsDownNothingIsRememberedSoTheRetrySendsTheEmail() {
        UUID event = UUID.randomUUID();
        doThrow(new SmtpEmailSender.EmailSendException("connection refused", null)).when(sender).send(any());

        assertThatThrownBy(() -> processor.process(TestEvents.codPlaced(event)))
                .isInstanceOf(SmtpEmailSender.EmailSendException.class);
        // Rolled back together with the failed send, so the retry is not mistaken for a duplicate
        assertThat(notifications.count()).isZero();

        doNothing().when(sender).send(any());
        assertThat(processor.process(TestEvents.codPlaced(event))).isEqualTo(Result.SENT);
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void aMessageThatIsNotAValidOrderEventIsRejectedForGood() {
        assertThatThrownBy(() -> processor.process("this is not json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processor.process("{}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> processor.process(""))
                .isInstanceOf(IllegalArgumentException.class);

        verify(sender, never()).send(any());
    }
}
