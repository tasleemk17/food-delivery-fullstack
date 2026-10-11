package com.fooddelivery.orders.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.orders.payment.InvalidWebhookException;
import com.fooddelivery.orders.service.OrderException;
import com.fooddelivery.orders.service.OrderService;
import com.fooddelivery.orders.service.UpstreamUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderCommandController.class)
class OrderCommandControllerTest {

    private static final String ADDRESS = "{\"firstName\":\"Asha\",\"lastName\":\"K\",\"email\":\"a@b.com\","
            + "\"street\":\"1 MG Road\",\"city\":\"Pune\",\"state\":\"MH\",\"zipcode\":\"411001\","
            + "\"country\":\"India\",\"phone\":\"9999999999\"}";
    // items and amount from the browser must be ignored, not rejected
    private static final String PLACE_BODY = "{\"address\":" + ADDRESS + ",\"items\":[{\"_id\":\"x\"}],\"amount\":1}";

    @Autowired MockMvc mvc;
    @MockitoBean OrderService service;

    @Test
    void placeReturnsSessionUrl() throws Exception {
        given(service.placeStripe(any(), any())).willReturn("https://stripe/pay");
        mvc.perform(post("/api/order/place").header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON).content(PLACE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.session_url").value("https://stripe/pay"));
    }

    @Test
    void placeCodReturns201() throws Exception {
        mvc.perform(post("/api/order/placecod").header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON).content(PLACE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Order Placed"));
        verify(service).placeCod(any(), any());
    }

    @Test
    void missingUserIs401AndIncompleteAddressIs400() throws Exception {
        mvc.perform(post("/api/order/placecod").contentType(MediaType.APPLICATION_JSON).content(PLACE_BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/order/placecod").header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"address\":{\"firstName\":\"A\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please fill in the full delivery address"));
        mvc.perform(post("/api/order/placecod").header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void nodeDownIs503WithGenericMessage() throws Exception {
        given(service.placeStripe(any(), any())).willThrow(new UpstreamUnavailableException("connection refused", null));
        mvc.perform(post("/api/order/place").header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON).content(PLACE_BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Service temporarily unavailable, please try again"));
    }

    @Test
    void unexpectedErrorIs500() throws Exception {
        given(service.placeStripe(any(), any())).willThrow(new IllegalStateException("db"));
        mvc.perform(post("/api/order/place").header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON).content(PLACE_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Error"));
    }

    @Test
    void verifyShapes() throws Exception {
        UUID id = UUID.randomUUID();
        given(service.verify("u1", id)).willReturn(true);
        mvc.perform(post("/api/order/verify").header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"" + id + "\"}"))
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.message").value("Paid"));

        given(service.verify("u1", id)).willReturn(false);
        mvc.perform(post("/api/order/verify").header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"" + id + "\"}"))
                .andExpect(jsonPath("$.success").value(false)).andExpect(jsonPath("$.message").value("Not Paid"));

        mvc.perform(post("/api/order/verify").header("X-User-Id", "u1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"nope\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid order"));
    }

    @Test
    void statusIsAdminOnly() throws Exception {
        UUID id = UUID.randomUUID();
        String body = "{\"orderId\":\"" + id + "\",\"status\":\"Delivered\"}";
        mvc.perform(post("/api/order/status").header("X-User-Id", "u1").header("X-User-Role", "user")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/order/status").header("X-User-Id", "a1").header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Status Updated"));
        verify(service).updateStatus(id, "Delivered");

        mvc.perform(post("/api/order/status").header("X-User-Id", "a1").header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":\"" + id + "\",\"status\":\"Payment Failed\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid status"));
    }

    @Test
    void statusConflictIs409() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new OrderException(HttpStatus.CONFLICT, "Order not found or not paid yet"))
                .when(service).updateStatus(id, "Delivered");
        mvc.perform(post("/api/order/status").header("X-User-Id", "a1").header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"" + id + "\",\"status\":\"Delivered\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void webhookNeedsNoUserHeaderAndPassesRawBody() throws Exception {
        mvc.perform(post("/api/order/webhook").header("Stripe-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"raw\": 1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.received").value(true));
        verify(service).handleWebhook("{\"raw\": 1}", "sig");
    }

    @Test
    void webhookBadSignatureIs400AndOtherFailuresMakeStripeRetry() throws Exception {
        doThrow(new InvalidWebhookException(HttpStatus.BAD_REQUEST, "Invalid signature"))
                .when(service).handleWebhook("bad", "sig");
        mvc.perform(post("/api/order/webhook").header("Stripe-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON).content("bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Invalid signature"));

        doThrow(new IllegalStateException("db down")).when(service).handleWebhook("ok", "sig");
        mvc.perform(post("/api/order/webhook").header("Stripe-Signature", "sig")
                        .contentType(MediaType.APPLICATION_JSON).content("ok"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.received").value(false));
    }
}
