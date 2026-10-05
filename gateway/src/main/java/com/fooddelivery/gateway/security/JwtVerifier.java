package com.fooddelivery.gateway.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies the login tokens issued by the Node backend (jsonwebtoken, HS256).
 *
 * Only what the gateway needs is implemented, using the JDK's own HMAC:
 *  - the algorithm must be HS256 (tokens with "alg":"none" or anything else are rejected)
 *  - the signature is compared in constant time (MessageDigest.isEqual)
 *  - the token must have an expiry ("exp") that is still in the future
 *  - the token must contain a user id ("id")
 *
 * A library such as jjwt or Spring Security's NimbusReactiveJwtDecoder could do
 * the same job; this class keeps the gateway free of extra dependencies and
 * makes every check explicit and unit-tested.
 */
public class JwtVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Decoder BASE64_URL = Base64.getUrlDecoder();

    private final SecretKeySpec key;
    private final Function<String, Map<String, Object>> jsonParser;
    private final Clock clock;

    /**
     * @param secret     the same JWT_SECRET the Node backend signs tokens with
     * @param jsonParser turns a JSON object string into a Map (Jackson in production)
     * @param clock      current time, injectable so tests can check expiry
     */
    public JwtVerifier(String secret, Function<String, Map<String, Object>> jsonParser, Clock clock) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET is not set for the gateway");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.jsonParser = jsonParser;
        this.clock = clock;
    }

    /** Returns the user in the token, or throws InvalidTokenException with the reason. */
    public AuthenticatedUser verify(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("missing token");
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new InvalidTokenException("malformed token");
        }

        Map<String, Object> header = decodeJson(parts[0]);
        if (!"HS256".equals(header.get("alg"))) {
            // Blocks the classic attack of sending "alg":"none" with no signature
            throw new InvalidTokenException("unsupported algorithm");
        }

        byte[] expected = sign(parts[0] + "." + parts[1]);
        byte[] actual = decodeBase64(parts[2]);
        // Constant-time comparison, so response timing does not leak how many bytes matched
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidTokenException("bad signature");
        }

        Map<String, Object> payload = decodeJson(parts[1]);

        if (!(payload.get("exp") instanceof Number exp)) {
            throw new InvalidTokenException("token has no expiry");
        }
        long nowSeconds = clock.instant().getEpochSecond();
        if (nowSeconds >= exp.longValue()) {
            throw new InvalidTokenException("token expired");
        }

        if (!(payload.get("id") instanceof String userId) || userId.isBlank()) {
            throw new InvalidTokenException("token has no user id");
        }
        String role = "admin".equals(payload.get("role")) ? "admin" : "user";

        return new AuthenticatedUser(userId, role);
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            return mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    private Map<String, Object> decodeJson(String part) {
        String json = new String(decodeBase64(part), StandardCharsets.UTF_8);
        try {
            Map<String, Object> map = jsonParser.apply(json);
            if (map == null) {
                throw new InvalidTokenException("malformed token");
            }
            return map;
        } catch (InvalidTokenException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InvalidTokenException("malformed token");
        }
    }

    private static byte[] decodeBase64(String part) {
        try {
            return BASE64_URL.decode(part);
        } catch (IllegalArgumentException e) {
            throw new InvalidTokenException("malformed token");
        }
    }
}
