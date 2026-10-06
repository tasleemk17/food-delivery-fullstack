package com.fooddelivery.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The tokens below were created by the Node backend's own library
 * (jsonwebtoken) with SECRET, so these tests prove the gateway and Node
 * agree on what a valid token is.
 */
class JwtVerifierTest {

    static final String SECRET = "test-secret-for-gateway-unit-tests-0123456789abcdef";

    // id 64b...001, role user, expires 2100-01-01
    static final String USER_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjY0YjAwMDAwMDAwMDAwMDAwMDAwMDAwMSIsInJvbGUiOiJ1c2VyIiwiZXhwIjo0MTAyNDQ0ODAwLCJpYXQiOjE3OTExODQ0NTB9.alIqujA0nTCIldEHp3NRu2B_NZ66LjiBJ1RuZpcVLT4";
    // id 64b...002, role admin, expires 2100-01-01
    static final String ADMIN_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjY0YjAwMDAwMDAwMDAwMDAwMDAwMDAwMiIsInJvbGUiOiJhZG1pbiIsImV4cCI6NDEwMjQ0NDgwMCwiaWF0IjoxNzkxMTg0NDUwfQ._CnbDeLVlQZL6Ob5QHmDFkDX7ZE4U4Gzd1AM-ZOfarw";
    // expired in 2020
    static final String EXPIRED_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjY0YjAwMDAwMDAwMDAwMDAwMDAwMDAwMSIsInJvbGUiOiJ1c2VyIiwiZXhwIjoxNjAwMDAwMDAwLCJpYXQiOjE3OTExODQ0NTB9.4_h1au51NElDHGBzQDQYunf88v2bG9nF_t3fKOnv9fY";
    // valid signature but no "exp" claim
    static final String NO_EXPIRY_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjY0YjAwMDAwMDAwMDAwMDAwMDAwMDAwMSIsInJvbGUiOiJ1c2VyIn0.ycDcFRXLMiN2Up4u9Zz9dcAVR8IRt66ITkA6aeqzHYw";
    // role admin, but signed with a different secret
    static final String OTHER_SECRET_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjY0YjAwMDAwMDAwMDAwMDAwMDAwMDAwMSIsInJvbGUiOiJhZG1pbiIsImV4cCI6NDEwMjQ0NDgwMCwiaWF0IjoxNzkxMTg0NDUwfQ.J2mDPcjjX2hxZrzAzMSHoPm-RDwWQX81xXPPzVZDxbo";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-05T00:00:00Z"), ZoneOffset.UTC);

    private final JwtVerifier verifier = new JwtVerifier(SECRET, JwtVerifierTest::parse, NOW);

    static Map<String, Object> parse(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Test
    void acceptsAUserTokenFromTheNodeBackend() {
        AuthenticatedUser user = verifier.verify(USER_TOKEN);
        assertThat(user.userId()).isEqualTo("64b000000000000000000001");
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void readsTheAdminRole() {
        assertThat(verifier.verify(ADMIN_TOKEN).isAdmin()).isTrue();
    }

    @Test
    void rejectsATokenSignedWithAnotherSecret() {
        assertThatThrownBy(() -> verifier.verify(OTHER_SECRET_TOKEN)).hasMessage("bad signature");
    }

    @Test
    void rejectsATokenWhosePayloadWasEdited() {
        // Swap the payload for one that says role=admin, keep the original signature
        String[] parts = USER_TOKEN.split("\\.");
        String forgedPayload = base64Url("{\"id\":\"64b000000000000000000001\",\"role\":\"admin\",\"exp\":4102444800}");
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> verifier.verify(forged)).hasMessage("bad signature");
    }

    @Test
    void rejectsAlgNone() {
        String header = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"id\":\"x\",\"role\":\"admin\",\"exp\":4102444800}");

        assertThatThrownBy(() -> verifier.verify(header + "." + payload + "."))
                .hasMessage("unsupported algorithm");
    }

    @Test
    void rejectsAnExpiredToken() {
        assertThatThrownBy(() -> verifier.verify(EXPIRED_TOKEN)).hasMessage("token expired");
    }

    @Test
    void rejectsATokenWithoutExpiry() {
        assertThatThrownBy(() -> verifier.verify(NO_EXPIRY_TOKEN)).hasMessage("token has no expiry");
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> verifier.verify("not-a-jwt")).hasMessage("malformed token");
        assertThatThrownBy(() -> verifier.verify("a.b.c")).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> verifier.verify("")).hasMessage("missing token");
    }

    @Test
    void refusesToStartWithoutASecret() {
        assertThatThrownBy(() -> new JwtVerifier(" ", JwtVerifierTest::parse, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
