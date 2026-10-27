package com.fooddelivery.notifications.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Builds real MIME messages but sends nothing: the "mail server" is a stub. */
class SmtpEmailSenderTest {

    private static class StubMailSender extends JavaMailSenderImpl {
        MimeMessage sent;
        RuntimeException failWith;

        @Override
        public void send(MimeMessage message) {
            if (failWith != null) {
                throw failWith;
            }
            sent = message;
        }
    }

    private final StubMailSender stub = new StubMailSender();
    private final SmtpEmailSender sender = new SmtpEmailSender(stub, "Tomato <orders@tomato.local>");

    @Test
    void buildsTheEmailWithSenderRecipientSubjectAndBody() throws Exception {
        sender.send(new EmailMessage("asha@example.com", "Order confirmed #1", "Total ₹1,950.00"));

        stub.sent.saveChanges();
        assertThat(stub.sent.getFrom()[0].toString()).contains("orders@tomato.local");
        assertThat(stub.sent.getAllRecipients()[0].toString()).isEqualTo("asha@example.com");
        assertThat(stub.sent.getSubject()).isEqualTo("Order confirmed #1");
        // UTF-8, so the rupee sign arrives intact
        assertThat(stub.sent.getContentType()).containsIgnoringCase("UTF-8");
        assertThat(stub.sent.getContent()).isEqualTo("Total ₹1,950.00");
    }

    @Test
    void aMalformedAddressCannotBeFixedByTryingAgain() {
        assertThatThrownBy(() -> sender.send(new EmailMessage("not an email", "Hi", "Body")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMailServerProblemIsReportedSoTheMessageIsRetried() {
        stub.failWith = new MailSendException("connection refused");

        assertThatThrownBy(() -> sender.send(new EmailMessage("asha@example.com", "Hi", "Body")))
                .isInstanceOf(SmtpEmailSender.EmailSendException.class);
    }
}
