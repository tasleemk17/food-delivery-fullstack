package com.fooddelivery.notifications.mail;

/** One email, ready to send. */
public record EmailMessage(String to, String subject, String body) {}
