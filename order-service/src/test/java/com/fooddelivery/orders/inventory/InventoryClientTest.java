package com.fooddelivery.orders.inventory;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fooddelivery.orders.config.InventoryProperties;
import com.fooddelivery.orders.inventory.InventoryPort.StockLine;
import com.fooddelivery.orders.service.OrderException;
import com.fooddelivery.orders.service.OutOfStockException;
import com.fooddelivery.orders.service.UpstreamUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;

/** A fake inventory-service (WireMock): no Docker and no database needed. */
class InventoryClientTest {

    private static final UUID ORDER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String RESERVE = "/internal/inventory/reservations";

    private WireMockServer inventory;
    private InventoryClient client;

    @BeforeEach
    void start() {
        inventory = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        inventory.start();
        client = clientFor("http://localhost:" + inventory.port(), Duration.ofMillis(500));
    }

    @AfterEach
    void stop() {
        inventory.stop();
    }

    private static InventoryClient clientFor(String url, Duration read) {
        return new InventoryClient(new InventoryProperties(url, "secret-key", Duration.ofMillis(500), read),
                RestClient.builder());
    }

    @Test
    void reserveSendsTheOrderTheItemsAndTheInternalKey() {
        inventory.stubFor(post(urlEqualTo(RESERVE)).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"data\":{\"status\":\"RESERVED\"}}")));

        client.reserve(ORDER, List.of(new StockLine("f1", 2), new StockLine("f2", 1)));

        inventory.verify(postRequestedFor(urlEqualTo(RESERVE))
                .withHeader("X-Internal-Key", equalTo("secret-key"))
                .withRequestBody(equalToJson("{\"orderId\":\"" + ORDER + "\","
                        + "\"items\":[{\"foodId\":\"f1\",\"quantity\":2},{\"foodId\":\"f2\",\"quantity\":1}]}")));
    }

    @Test
    void notEnoughStockBecomesOutOfStockWithTheFoodAndHowManyAreLeft() {
        inventory.stubFor(post(urlEqualTo(RESERVE)).willReturn(aResponse()
                .withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":false,\"data\":{\"foodId\":\"f2\",\"available\":1},\"message\":\"Not enough stock\"}")));

        assertThatThrownBy(() -> client.reserve(ORDER, List.of(new StockLine("f2", 3))))
                .isInstanceOfSatisfying(OutOfStockException.class, e -> {
                    assertThat(e.foodId()).isEqualTo("f2");
                    assertThat(e.available()).isEqualTo(1);
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void aConflictThatIsNotAboutStockKeepsItsMessage() {
        inventory.stubFor(post(urlEqualTo(RESERVE + "/" + ORDER + "/commit")).willReturn(aResponse()
                .withStatus(409)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":false,\"message\":\"This reservation was already released\"}")));

        assertThatThrownBy(() -> client.commit(ORDER))
                .isInstanceOfSatisfying(OrderException.class, e -> {
                    assertThat(e).isNotInstanceOf(OutOfStockException.class);
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getMessage()).isEqualTo("This reservation was already released");
                });
    }

    @Test
    void aConflictWithAnUnreadableBodyIsStillAConflict() {
        inventory.stubFor(post(urlEqualTo(RESERVE)).willReturn(aResponse().withStatus(409).withBody("oops")));

        assertThatThrownBy(() -> client.reserve(ORDER, List.of(new StockLine("f1", 1))))
                .isInstanceOfSatisfying(OrderException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void commitAndReleaseCallTheRightPaths() {
        inventory.stubFor(post(urlEqualTo(RESERVE + "/" + ORDER + "/commit")).willReturn(aResponse().withStatus(200)));
        inventory.stubFor(post(urlEqualTo(RESERVE + "/" + ORDER + "/release")).willReturn(aResponse().withStatus(200)));

        client.commit(ORDER);
        client.release(ORDER);

        inventory.verify(postRequestedFor(urlEqualTo(RESERVE + "/" + ORDER + "/commit"))
                .withHeader("X-Internal-Key", equalTo("secret-key")));
        inventory.verify(postRequestedFor(urlEqualTo(RESERVE + "/" + ORDER + "/release")));
    }

    @Test
    void aServerErrorOrABadKeyIs503ForTheCaller() {
        inventory.stubFor(post(urlEqualTo(RESERVE)).willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> client.reserve(ORDER, List.of(new StockLine("f1", 1))))
                .isInstanceOf(UpstreamUnavailableException.class);

        inventory.stubFor(post(urlEqualTo(RESERVE)).willReturn(aResponse().withStatus(401)));
        assertThatThrownBy(() -> client.reserve(ORDER, List.of(new StockLine("f1", 1))))
                .isInstanceOf(UpstreamUnavailableException.class);

        inventory.stubFor(post(urlEqualTo(RESERVE)).willReturn(aResponse().withStatus(503)));
        assertThatThrownBy(() -> client.reserve(ORDER, List.of(new StockLine("f1", 1))))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void aRejectedRequestIs400() {
        inventory.stubFor(post(urlEqualTo(RESERVE)).willReturn(aResponse()
                .withStatus(400)
                .withBody("{\"success\":false,\"message\":\"Quantity must be between 1 and 1000\"}")));

        assertThatThrownBy(() -> client.reserve(ORDER, List.of(new StockLine("f1", 5000))))
                .isInstanceOfSatisfying(OrderException.class,
                        e -> assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void aSlowInventoryServiceTimesOutAs503() {
        inventory.stubFor(post(urlEqualTo(RESERVE)).willReturn(aResponse().withFixedDelay(2000).withStatus(200)));

        assertThatThrownBy(() -> client.reserve(ORDER, List.of(new StockLine("f1", 1))))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void anUnreachableInventoryServiceIs503() {
        InventoryClient nobodyHome = clientFor("http://localhost:1", Duration.ofMillis(500));

        assertThatThrownBy(() -> nobodyHome.reserve(ORDER, List.of(new StockLine("f1", 1))))
                .isInstanceOf(UpstreamUnavailableException.class);
        assertThatThrownBy(() -> nobodyHome.commit(ORDER)).isInstanceOf(UpstreamUnavailableException.class);
    }
}
