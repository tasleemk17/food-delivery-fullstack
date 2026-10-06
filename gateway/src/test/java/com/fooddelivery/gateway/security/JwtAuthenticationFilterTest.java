package com.fooddelivery.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Runs the filter on fake requests. "forwarded" is the request the gateway
 * would pass on to the backend; it stays null when the gateway answers itself.
 */
class JwtAuthenticationFilterTest {

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
            new JwtVerifier(JwtVerifierTest.SECRET, JwtVerifierTest::parse, Clock.systemUTC()),
            new RouteAccess());

    private ServerWebExchange forwarded;

    private final GatewayFilterChain chain = exchange -> {
        forwarded = exchange;
        return Mono.empty();
    };

    private MockServerWebExchange run(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, chain).block();
        return exchange;
    }

    private HttpHeaders forwardedHeaders() {
        return forwarded.getRequest().getHeaders();
    }

    @Test
    void publicRouteNeedsNoToken() {
        run(MockServerHttpRequest.get("/api/food/list").build());
        assertThat(forwarded).isNotNull();
    }

    @Test
    void missingTokenIsStoppedAtTheGateway() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.post("/api/order/place").build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded).isNull();
    }

    @Test
    void tokenSignedWithAnotherSecretIsStopped() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.post("/api/order/place")
                .header("token", JwtVerifierTest.OTHER_SECRET_TOKEN).build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded).isNull();
    }

    @Test
    void expiredTokenSaysSessionExpired() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.post("/api/order/place")
                .header("token", JwtVerifierTest.EXPIRED_TOKEN).build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("Session expired");
    }

    @Test
    void validTokenAddsTheUserHeaders() {
        run(MockServerHttpRequest.post("/api/order/place")
                .header("token", JwtVerifierTest.USER_TOKEN).build());

        assertThat(forwardedHeaders().getFirst("X-User-Id")).isEqualTo("64b000000000000000000001");
        assertThat(forwardedHeaders().getFirst("X-User-Role")).isEqualTo("user");
    }

    @Test
    void bearerHeaderIsAlsoAccepted() {
        run(MockServerHttpRequest.post("/api/order/place")
                .header("Authorization", "Bearer " + JwtVerifierTest.USER_TOKEN).build());

        assertThat(forwarded).isNotNull();
    }

    @Test
    void identityHeadersSentByTheClientAreReplaced() {
        run(MockServerHttpRequest.post("/api/order/place")
                .header("token", JwtVerifierTest.USER_TOKEN)
                .header("X-User-Id", "someone-else")
                .header("X-User-Role", "admin")
                .build());

        assertThat(forwardedHeaders().get("X-User-Id")).containsExactly("64b000000000000000000001");
        assertThat(forwardedHeaders().get("X-User-Role")).containsExactly("user");
    }

    @Test
    void identityHeadersAreRemovedOnPublicRoutesToo() {
        run(MockServerHttpRequest.get("/api/food/list").header("X-User-Role", "admin").build());

        assertThat(forwardedHeaders().containsKey("X-User-Role")).isFalse();
    }

    @Test
    void normalUserCannotCallAnAdminRoute() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/order/list")
                .header("token", JwtVerifierTest.USER_TOKEN).build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forwarded).isNull();
    }

    @Test
    void adminCanCallAnAdminRoute() {
        run(MockServerHttpRequest.get("/api/order/list")
                .header("token", JwtVerifierTest.ADMIN_TOKEN).build());

        assertThat(forwardedHeaders().getFirst("X-User-Role")).isEqualTo("admin");
    }

    @Test
    void internalRoutesAreHiddenEvenFromAdmins() {
        MockServerWebExchange exchange = run(MockServerHttpRequest
                .get("/internal/users/64b000000000000000000001/cart")
                .header("token", JwtVerifierTest.ADMIN_TOKEN).build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(forwarded).isNull();
    }

    @Test
    void pathTraversalIsRejected() {
        // URI.create keeps "/../" exactly as sent, like a hand-crafted request would
        MockServerWebExchange exchange = run(MockServerHttpRequest
                .method(HttpMethod.GET, URI.create("/images/../api/order/list")).build());

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(forwarded).isNull();
    }
}
