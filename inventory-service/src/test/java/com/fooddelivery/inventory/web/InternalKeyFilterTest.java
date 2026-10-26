package com.fooddelivery.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Plain unit tests: no Spring and no Docker needed. */
class InternalKeyFilterTest {

    private MockHttpServletResponse call(String configuredKey, String path, String sentKey) throws Exception {
        InternalKeyFilter filter = new InternalKeyFilter(configuredKey);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        if (sentKey != null) {
            request.addHeader("X-Internal-Key", sentKey);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void theRightKeyLetsTheRequestThrough() throws Exception {
        assertThat(call("secret", "/internal/inventory/reservations", "secret").getStatus()).isEqualTo(200);
    }

    @Test
    void aWrongOrMissingKeyIs401() throws Exception {
        assertThat(call("secret", "/internal/inventory/reservations", "wrong").getStatus()).isEqualTo(401);
        assertThat(call("secret", "/internal/inventory/reservations", null).getStatus()).isEqualTo(401);
        // Same length but different: must still be rejected
        assertThat(call("secret", "/internal/inventory/reservations", "secrex").getStatus()).isEqualTo(401);
    }

    @Test
    void withoutAConfiguredKeyTheInternalRoutesAreSwitchedOff() throws Exception {
        assertThat(call("", "/internal/inventory/reservations", "anything").getStatus()).isEqualTo(503);
        assertThat(call(null, "/internal/inventory/reservations", "anything").getStatus()).isEqualTo(503);
    }

    @Test
    void otherPathsDoNotNeedTheKey() throws Exception {
        assertThat(call("secret", "/api/inventory/availability", null).getStatus()).isEqualTo(200);
    }
}
