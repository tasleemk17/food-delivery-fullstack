package com.fooddelivery.gateway.security;

import static com.fooddelivery.gateway.security.RouteAccess.Access.ADMIN;
import static com.fooddelivery.gateway.security.RouteAccess.Access.BLOCKED;
import static com.fooddelivery.gateway.security.RouteAccess.Access.PUBLIC;
import static com.fooddelivery.gateway.security.RouteAccess.Access.REJECT;
import static com.fooddelivery.gateway.security.RouteAccess.Access.USER;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RouteAccessTest {

    private final RouteAccess routes = new RouteAccess();

    @ParameterizedTest
    @CsvSource({
            "GET,/",
            "GET,/actuator/health",
            "POST,/api/user/login",
            "POST,/api/user/register",
            "GET,/api/food/list",
            "GET,/images/123-food.png",
            "HEAD,/images/123-food.png",
            "POST,/api/order/webhook",
            "OPTIONS,/api/order/place",
    })
    void publicRoutes(String method, String path) {
        assertThat(routes.classify(method, path)).isEqualTo(PUBLIC);
    }

    @ParameterizedTest
    @CsvSource({
            "POST,/api/order/place",
            "POST,/api/order/placecod",
            "POST,/api/order/userorders",
            "POST,/api/order/verify",
            "POST,/api/cart/add",
            "POST,/api/cart/get",
            // Public only for GET; other methods need a login
            "POST,/api/food/list",
            // Unknown paths are not public by default
            "GET,/api/something/new",
    })
    void routesThatNeedALogin(String method, String path) {
        assertThat(routes.classify(method, path)).isEqualTo(USER);
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/api/order/list",
            "POST,/api/order/status",
            "POST,/api/food/add",
            "POST,/api/food/remove",
            // Express ignores case and a trailing slash, so the gateway must too
            "GET,/API/ORDER/LIST",
            "GET,/api/order/list/",
            "DELETE,/api/food/remove",
    })
    void adminRoutes(String method, String path) {
        assertThat(routes.classify(method, path)).isEqualTo(ADMIN);
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/images/../api/order/list",
            "GET,/images/%2e%2e/api/order/list",
            "GET,/api//order/list",
            "GET,/api/./order/list",
            "GET,/images/..%2fapi",
    })
    void suspiciousPathsAreRejected(String method, String path) {
        assertThat(routes.classify(method, path)).isEqualTo(REJECT);
    }

    @ParameterizedTest
    @CsvSource({
            "GET,/internal/users/64b000000000000000000001/cart",
            "POST,/internal/users/64b000000000000000000001/cart/clear",
            "GET,/INTERNAL/users/x/cart",
            "GET,/internal",
            "GET,/internal/",
    })
    void internalRoutesAreBlocked(String method, String path) {
        assertThat(routes.classify(method, path)).isEqualTo(BLOCKED);
    }

    @Test
    void aPathThatOnlyStartsWithTheWordInternalIsNotBlocked() {
        assertThat(routes.classify("GET", "/internalstuff")).isEqualTo(USER);
    }

    @Test
    void emptyPathIsTheHomePage() {
        assertThat(routes.classify("GET", "")).isEqualTo(PUBLIC);
    }
}
