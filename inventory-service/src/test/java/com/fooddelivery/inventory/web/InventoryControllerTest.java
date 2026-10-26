package com.fooddelivery.inventory.web;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.inventory.domain.StockItem;
import com.fooddelivery.inventory.service.InventoryService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: the service is a mock, no database or Docker needed. */
@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InventoryService inventory;

    @Test
    void availabilityIsPublicAndHidesQuantities() throws Exception {
        given(inventory.availability()).willReturn(Map.of("A", true, "B", false));

        mvc.perform(get("/api/inventory/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.A").value(true))
                .andExpect(jsonPath("$.data.B").value(false));
    }

    @Test
    void listWithoutTheGatewayHeaderIs401() throws Exception {
        mvc.perform(get("/api/inventory")).andExpect(status().isUnauthorized());
        verifyNoInteractions(inventory);
    }

    @Test
    void listForANormalUserIs403() throws Exception {
        mvc.perform(get("/api/inventory").header("X-User-Id", "u1").header("X-User-Role", "user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Admin access only"));
        verifyNoInteractions(inventory);
    }

    @Test
    void listForAnAdminReturnsTheStock() throws Exception {
        given(inventory.listStock()).willReturn(List.of(new StockItem("A", 4, Instant.now())));

        mvc.perform(get("/api/inventory").header("X-User-Id", "u1").header("X-User-Role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].foodId").value("A"))
                .andExpect(jsonPath("$.data[0].quantity").value(4));
    }

    @Test
    void anAdminCanSetStock() throws Exception {
        given(inventory.setStock("A", 7)).willReturn(new StockItem("A", 7, Instant.now()));

        mvc.perform(put("/api/inventory/A")
                        .header("X-User-Id", "u1")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(7));
        verify(inventory).setStock("A", 7);
    }

    @Test
    void aNormalUserCannotSetStock() throws Exception {
        mvc.perform(put("/api/inventory/A")
                        .header("X-User-Id", "u1")
                        .header("X-User-Role", "user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":7}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(inventory);
    }

    @Test
    void settingStockWithoutAQuantityIs400() throws Exception {
        mvc.perform(put("/api/inventory/A")
                        .header("X-User-Id", "u1")
                        .header("X-User-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
