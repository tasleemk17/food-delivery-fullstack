package com.fooddelivery.gateway.security;

import java.nio.charset.StandardCharsets;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Runs on every request that goes through the gateway.
 *
 * 1. Removes any X-User-Id / X-User-Role headers the client sent, so nobody
 *    can claim to be another user by adding a header.
 * 2. Lets public routes through without a login.
 * 3. For everything else, verifies the JWT and answers 401 itself if it is
 *    missing, forged or expired, so the request never reaches the backend.
 * 4. Answers 403 if a non-admin calls an admin route.
 * 5. Forwards the request with X-User-Id and X-User-Role set from the token.
 */
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";

    private final JwtVerifier verifier;
    private final RouteAccess routes;

    public JwtAuthenticationFilter(JwtVerifier verifier, RouteAccess routes) {
        this.verifier = verifier;
        this.routes = routes;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        ServerHttpRequest withoutIdentity = request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_ROLE_HEADER);
                })
                .build();

        // Raw path, so encoded tricks like %2e%2e are seen before any decoding
        RouteAccess.Access access = routes.classify(request.getMethod().name(), request.getURI().getRawPath());

        if (access == RouteAccess.Access.REJECT) {
            return reject(exchange, HttpStatus.BAD_REQUEST, "Invalid path");
        }
        if (access == RouteAccess.Access.PUBLIC) {
            return chain.filter(exchange.mutate().request(withoutIdentity).build());
        }

        AuthenticatedUser user;
        try {
            user = verifier.verify(readToken(request));
        } catch (InvalidTokenException e) {
            String message = "token expired".equals(e.getMessage())
                    ? "Session expired, please log in again"
                    : "Not authorized, please log in again";
            return reject(exchange, HttpStatus.UNAUTHORIZED, message);
        }

        if (access == RouteAccess.Access.ADMIN && !user.isAdmin()) {
            return reject(exchange, HttpStatus.FORBIDDEN, "Admin access only");
        }

        ServerHttpRequest withIdentity = withoutIdentity.mutate()
                .header(USER_ID_HEADER, user.userId())
                .header(USER_ROLE_HEADER, user.role())
                .build();
        return chain.filter(exchange.mutate().request(withIdentity).build());
    }

    /** Same two places the Node backend reads the token from. */
    private static String readToken(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst("token");
        if (token != null && !token.isBlank()) {
            return token;
        }
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    /** Answers in the same JSON shape the Node backend uses: { success, message }. */
    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"success\":false,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    /** Runs before the gateway's own routing filters. */
    @Override
    public int getOrder() {
        return -100;
    }
}
