package com.fooddelivery.gateway.security;

/** The logged-in user, as read from a verified token. */
public record AuthenticatedUser(String userId, String role) {

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
