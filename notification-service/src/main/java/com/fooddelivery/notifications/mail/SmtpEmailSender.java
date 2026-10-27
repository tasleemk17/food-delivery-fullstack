package com.fooddelivery.notifications.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Sends through SMTP. In Docker this is MailHog, which keeps every email in a
 * test inbox (http://localhost:8025) instead of delivering it, so no real
 * customer is ever emailed while developing.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${notifications.mail-from:Tomato <orders@tomato.local>}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            // UTF-8, so the rupee sign and Indian names are not garbled
            MimeMessageHelper helper = new MimeMessageHelper(mime, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.body(), false);
            mailSender.send(mime);
        } catch (MessagingException e) {
            // For example a malformed address. Trying again cannot fix it, so this is not retried.
            throw new IllegalArgumentException("Could not build the email for " + message.to(), e);
        } catch (MailException e) {
            // SMTP server down or refusing: surface it so the message is retried
            throw new EmailSendException("Could not send the email to " + message.to(), e);
        }
    }

    /** The mail server could not be reached or refused the email. */
    public static class EmailSendException extends RuntimeException {
        public EmailSendException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
