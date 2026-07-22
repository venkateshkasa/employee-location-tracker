package com.employeetracker.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates cryptographically secure, URL-safe random tokens (e.g. for the
 * "Create Password" account-activation link).
 */
public final class TokenUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // 32 random bytes = 256 bits of entropy, Base64url-encoded (no padding)
    // so the token can be dropped straight into a URL query string.
    private static final int TOKEN_BYTE_LENGTH = 32;

    private TokenUtil() {
    }

    public static String generateSecureToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
