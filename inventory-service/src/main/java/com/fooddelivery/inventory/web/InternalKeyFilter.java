package com.fooddelivery.inventory.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protects /internal/**, which only other services (order-service) may call.
 * The caller sends the shared key in the X-Internal-Key header.
 *
 * The gateway never routes /internal/** from the outside, so this key is a
 * second lock. If no key is configured the routes stay switched off (503)
 * instead of being left open.
 */
@Component
public class InternalKeyFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Internal-Key";

    private final String expectedKey;

    public InternalKeyFilter(@Value("${internal.key:}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (expectedKey == null || expectedKey.isBlank()) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Internal API is not configured");
            return;
        }
        String provided = request.getHeader(HEADER);
        // Constant-time comparison, so response time does not leak how much of the key matched
        boolean ok = provided != null && MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8), expectedKey.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal key");
            return;
        }
        chain.doFilter(request, response);
    }

    private static void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }
}
