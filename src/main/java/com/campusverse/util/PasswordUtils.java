package com.campusverse.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.HexFormat;

/**
 * Cryptographic utility for secure password hashing and verification
 * using PBKDF2WithHmacSHA256 and cryptographically random salts.
 */
public final class PasswordUtils {

    public static final String DEFAULT_DEMO_PASSWORD = "Campus@123";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordUtils() {}

    /**
     * Generates a 16-byte cryptographically secure random salt formatted as hex.
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * Computes the PBKDF2WithHmacSHA256 hash of a password using the given salt.
     */
    public static String hashPassword(String password, String saltHex) {
        if (password == null || saltHex == null) {
            throw new IllegalArgumentException("Password and salt cannot be null");
        }
        try {
            byte[] salt = HexFormat.of().parseHex(saltHex);
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to hash password with " + ALGORITHM, e);
        }
    }

    /**
     * Constant-time password verification to prevent timing attacks.
     */
    public static boolean verifyPassword(String password, String storedHashHex, String storedSaltHex) {
        if (password == null || storedHashHex == null || storedSaltHex == null) {
            return false;
        }
        try {
            String computedHashHex = hashPassword(password, storedSaltHex);
            return MessageDigest.isEqual(
                    computedHashHex.getBytes(StandardCharsets.UTF_8),
                    storedHashHex.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}

