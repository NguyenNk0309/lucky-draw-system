package com.marketplace.gateway.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DemoAuthService {
    private final byte[] secret;
    private final String customerPassword;
    private final String sellerPassword;
    private final Clock clock;

    public DemoAuthService(@Value("${demo-auth.secret}") String secret,
            @Value("${demo-auth.customer-password}") String customerPassword,
            @Value("${demo-auth.seller-password}") String sellerPassword, Clock clock) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.customerPassword = customerPassword;
        this.sellerPassword = sellerPassword;
        this.clock = clock;
    }

    public Login login(String username, String password) {
        Identity identity = switch (username) {
            case "customer" -> matches(password, customerPassword) ? new Identity("customer-1", "CUSTOMER") : null;
            case "seller" -> matches(password, sellerPassword) ? new Identity("seller-1", "SELLER") : null;
            default -> null;
        };
        if (identity == null) throw new UnauthorizedException("Invalid username or password");
        long expiresAt = clock.instant().plus(Duration.ofHours(8)).getEpochSecond();
        String payload = encode(identity.userId() + "|" + identity.role() + "|" + expiresAt);
        return new Login(payload + "." + sign(payload), identity.userId(), identity.role(), expiresAt);
    }

    public Identity verify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.US_ASCII),
                    parts[1].getBytes(StandardCharsets.US_ASCII))) throw new IllegalArgumentException();
            String[] payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)
                    .split("\\|", -1);
            if (payload.length != 3 || Long.parseLong(payload[2]) <= clock.instant().getEpochSecond()
                    || !("CUSTOMER".equals(payload[1]) || "SELLER".equals(payload[1]))) {
                throw new IllegalArgumentException();
            }
            return new Identity(payload[0], payload[1]);
        } catch (RuntimeException exception) {
            throw new UnauthorizedException("Invalid or expired token");
        }
    }

    private boolean matches(String supplied, String expected) {
        return MessageDigest.isEqual(supplied.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return encode(mac.doFinal(value.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) { throw new IllegalStateException("Cannot sign token", exception); }
    }

    private static String encode(String value) { return encode(value.getBytes(StandardCharsets.UTF_8)); }
    private static String encode(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }

    public record Identity(String userId, String role) {}
    public record Login(String token, String userId, String role, long expiresAt) {}
    public static class UnauthorizedException extends RuntimeException { public UnauthorizedException(String m) { super(m); } }
}
