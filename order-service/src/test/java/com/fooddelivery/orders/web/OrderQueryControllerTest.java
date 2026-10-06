package com.fooddelivery.orders.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.orders.domain.Address;
import com.fooddelivery.orders.domain.OrderEntity;
import com.fooddelivery.orders.domain.OrderRepository;
import com.fooddelivery.orders.domain.OrderStatus;
import com.fooddelivery.orders.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: the repository is a mock, no database or Docker needed. */
@WebMvcTest(OrderQueryController.class)
class OrderQueryControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OrderRepository orders;

    private static OrderEntity sampleOrder() {
        Address address = new Address("Asha", "K", "a@b.com", "1 MG Road", "Pune", "MH", "411001", "India", "9999999999");
        OrderEntity order = new OrderEntity("user-1", PaymentMethod.COD, OrderStatus.FOOD_PROCESSING,
                address, Instant.parse("2026-10-05T10:00:00Z"));
        order.addItem("food-1", "Greek Salad", new BigDecimal("120.00"), 2);
        order.addItem("food-2", "Veg Roll", new BigDecimal("90.00"), 1);
        order.setAmount(new BigDecimal("380.00"));
        return order;
    }

    @Test
    void userOrdersWithoutTheGatewayHeaderIs401() throws Exception {
        mvc.perform(post("/api/order/userorders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(orders);
    }

    @Test
    void userOrdersReturnsTheSameJsonShapeAsTheNodeBackend() throws Exception {
        given(orders.findByUserIdOrderByCreatedAtDesc("user-1")).willReturn(List.of(sampleOrder()));

        mvc.perform(post("/api/order/userorders").header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].amount").value(380))
                .andExpect(jsonPath("$.data[0].status").value("Food Processing"))
                .andExpect(jsonPath("$.data[0].paymentMethod").value("cod"))
                .andExpect(jsonPath("$.data[0].payment").value(false))
                .andExpect(jsonPath("$.data[0].items[0].name").value("Greek Salad"))
                .andExpect(jsonPath("$.data[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$.data[0].items[0].price").value(120))
                .andExpect(jsonPath("$.data[0].address.city").value("Pune"));
    }

    @Test
    void adminListWithoutLoginIs401() throws Exception {
        mvc.perform(get("/api/order/list")).andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test
    void adminListForANormalUserIs403() throws Exception {
        mvc.perform(get("/api/order/list").header("X-User-Id", "user-1").header("X-User-Role", "user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Admin access only"));
        verifyNoInteractions(orders);
    }

    @Test
    void adminListForAnAdmin() throws Exception {
        given(orders.findAllByOrderByCreatedAtAsc()).willReturn(List.of(sampleOrder()));

        mvc.perform(get("/api/order/list").header("X-User-Id", "admin-1").header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
