package com.fooddelivery.gateway.security;

import java.util.Locale;
import java.util.Set;

/**
 * Decides who may call each path.
 *
 * Default is deny: any path not listed as public needs a valid login.
 * Paths are normalised the way Express matches them (case-insensitive,
 * trailing slash ignored), so "/API/ORDER/LIST/" cannot slip past the admin
 * rule while still reaching the same Node route.
 */
public class RouteAccess {

    public enum Access { PUBLIC, USER, ADMIN, REJECT }

    /** Exact "METHOD path" pairs that anyone may call. */
    private static final Set<String> PUBLIC_ROUTES = Set.of(
            "GET /",
            "GET /actuator/health",
            "POST /api/user/login",
            "POST /api/user/register",
            "GET /api/food/list",
            // Stripe calls this server-to-server; the backend checks Stripe's signature
            "POST /api/order/webhook");

    /** Paths only admins may call, whatever the HTTP method. */
    private static final Set<String> ADMIN_PATHS = Set.of(
            "/api/order/list",
            "/api/order/status",
            "/api/food/add",
            "/api/food/remove");

    public Access classify(String method, String rawPath) {
        String httpMethod = method == null ? "" : method.toUpperCase(Locale.ROOT);

        // CORS preflight requests carry no token and are answered by the gateway itself
        if ("OPTIONS".equals(httpMethod)) {
            return Access.PUBLIC;
        }

        String path = (rawPath == null || rawPath.isEmpty()) ? "/" : rawPath.toLowerCase(Locale.ROOT);
        if (isSuspicious(path)) {
            return Access.REJECT;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        if (ADMIN_PATHS.contains(path)) {
            return Access.ADMIN;
        }

        String lookupMethod = "HEAD".equals(httpMethod) ? "GET" : httpMethod;
        if (PUBLIC_ROUTES.contains(lookupMethod + " " + path)) {
            return Access.PUBLIC;
        }
        // Food images
        if ("GET".equals(lookupMethod) && path.startsWith("/images/")) {
            return Access.PUBLIC;
        }
        return Access.USER;
    }

    /**
     * Rejects paths that could be read one way here and another way by the
     * backend: "..", ".", empty segments, backslashes and encoded dots or slashes.
     */
    private static boolean isSuspicious(String path) {
        if (path.contains("\\") || path.contains("//")
                || path.contains("%2e") || path.contains("%2f") || path.contains("%5c")) {
            return true;
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return true;
            }
        }
        return false;
    }
}
