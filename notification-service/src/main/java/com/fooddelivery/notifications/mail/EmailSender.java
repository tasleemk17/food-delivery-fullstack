package com.fooddelivery.notifications.mail;

/** Sends an email. Throws if it could not be sent, so the caller can try again later. */
public interface EmailSender {

    void send(EmailMessage message);
}
