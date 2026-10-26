package com.fooddelivery.inventory.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fooddelivery.inventory.service.InsufficientStockException;
import com.fooddelivery.inventory.service.InventoryService;
import com.fooddelivery.inventory.service.InventoryService.Line;
import com.fooddelivery.inventory.service.InventoryService.ReservationResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web layer only: the service is a mock, no database or Docker needed. */
@WebMvcTest(InternalReservationController.class)
@Import(InternalKeyFilter.class)
@TestPropertySource(properties = "internal.key=test-key")
class InternalReservationControllerTest {

    private static final UUID ORDER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String BODY =
            "{\"orderId\":\"" + ORDER + "\",\"items\":[{\"foodId\":\"A\",\"quantity\":2}]}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private InventoryService inventory;

    @Test
    void withoutTheInternalKeyIs401AndTheServiceIsNotCalled() throws Exception {
        mvc.perform(post("/internal/inventory/reservations").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(inventory);
    }

    @Test
    void reserveReturnsTheReservation() throws Exception {
        given(inventory.reserve(ORDER, List.of(new Line("A", 2))))
                .willReturn(new ReservationResult(ORDER, "RESERVED", List.of(new Line("A", 2))));

        mvc.perform(post("/internal/inventory/reservations")
                        .header("X-Internal-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.items[0].foodId").value("A"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
    }

    @Test
    void notEnoughStockIs409AndNamesTheFood() throws Exception {
        given(inventory.reserve(any(), any())).willThrow(new InsufficientStockException("A", 1));

        mvc.perform(post("/internal/inventory/reservations")
                        .header("X-Internal-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Not enough stock"))
                .andExpect(jsonPath("$.data.foodId").value("A"))
                .andExpect(jsonPath("$.data.available").value(1));
    }

    @Test
    void anInvalidOrderIdIs400() throws Exception {
        mvc.perform(post("/internal/inventory/reservations")
                        .header("X-Internal-Key", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"not-a-uuid\",\"items\":[{\"foodId\":\"A\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/internal/inventory/reservations/not-a-uuid/commit").header("X-Internal-Key", "test-key"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMissingBodyIs400() throws Exception {
        mvc.perform(post("/internal/inventory/reservations").header("X-Internal-Key", "test-key"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void commitAndReleaseCallTheService() throws Exception {
        given(inventory.commit(ORDER)).willReturn(new ReservationResult(ORDER, "COMMITTED", List.of()));
        given(inventory.release(ORDER)).willReturn(new ReservationResult(ORDER, "RELEASED", List.of()));

        mvc.perform(post("/internal/inventory/reservations/" + ORDER + "/commit").header("X-Internal-Key", "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMMITTED"));
        mvc.perform(post("/internal/inventory/reservations/" + ORDER + "/release").header("X-Internal-Key", "test-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RELEASED"));
        verify(inventory).commit(ORDER);
        verify(inventory).release(ORDER);
    }
}
