package com.fooddelivery.orders.node;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fooddelivery.orders.config.NodeProperties;
import com.fooddelivery.orders.service.OrderException;
import com.fooddelivery.orders.service.UpstreamUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

class NodeBackendClientTest {

    private WireMockServer node;
    private NodeBackendClient client;

    @BeforeEach
    void start() {
        node = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        node.start();
        client = clientFor("http://localhost:" + node.port(), Duration.ofMillis(500));
    }

    @AfterEach
    void stop() {
        node.stop();
    }

    private static NodeBackendClient clientFor(String url, Duration read) {
        return new NodeBackendClient(new NodeProperties(url, "secret-key", Duration.ofMillis(500), read),
                RestClient.builder());
    }

    @Test
    void fetchesCartAndSendsTheInternalKey() {
        node.stubFor(get(urlEqualTo("/internal/users/u1/cart")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"cart\":{\"f1\":2},\"foods\":[{\"id\":\"f1\",\"name\":\"Salad\",\"price\":120}]}")));

        CartSnapshot s = client.fetchCart("u1");

        assertThat(s.cart()).containsKey("f1");
        assertThat(s.foods()).singleElement().satisfies(f -> {
            assertThat(f.name()).isEqualTo("Salad");
            assertThat(f.price()).isEqualByComparingTo("120");
        });
        node.verify(getRequestedFor(urlEqualTo("/internal/users/u1/cart")).withHeader("X-Internal-Key", equalTo("secret-key")));
    }

    @Test
    void unknownUserIs404() {
        node.stubFor(get(urlEqualTo("/internal/users/u1/cart")).willReturn(aResponse().withStatus(404)));
        assertThatThrownBy(() -> client.fetchCart("u1"))
                .isInstanceOf(OrderException.class)
                .hasMessage("User not found")
                .extracting(e -> ((OrderException) e).status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void serverErrorAndBadKeyBecome503() {
        node.stubFor(get(urlEqualTo("/internal/users/u1/cart")).willReturn(aResponse().withStatus(500)));
        node.stubFor(get(urlEqualTo("/internal/users/u2/cart")).willReturn(aResponse().withStatus(401)));
        assertThatThrownBy(() -> client.fetchCart("u1")).isInstanceOf(UpstreamUnavailableException.class);
        assertThatThrownBy(() -> client.fetchCart("u2")).isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void slowNodeTimesOutAs503() {
        node.stubFor(get(urlEqualTo("/internal/users/u1/cart")).willReturn(aResponse().withFixedDelay(2000).withStatus(200)));
        long t0 = System.nanoTime();
        assertThatThrownBy(() -> client.fetchCart("u1")).isInstanceOf(UpstreamUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void nodeDownIs503() {
        int port = node.port();
        node.stop();
        NodeBackendClient dead = clientFor("http://localhost:" + port, Duration.ofMillis(500));
        assertThatThrownBy(() -> dead.fetchCart("u1")).isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void clearAndRemoveUnavailableCallTheRightEndpoints() {
        node.stubFor(post(urlEqualTo("/internal/users/u1/cart/clear")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{\"success\":true}")));
        node.stubFor(post(urlEqualTo("/internal/users/u1/cart/remove-unavailable")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json").withBody("{\"success\":true,\"removed\":1}")));
        client.clearCart("u1");
        client.removeUnavailable("u1");
        node.verify(1, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlEqualTo("/internal/users/u1/cart/clear")));
        node.verify(1, com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlEqualTo("/internal/users/u1/cart/remove-unavailable")));
    }
}
