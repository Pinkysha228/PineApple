package me.pinkysha.pineapple.velocity.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;

public final class PasswordSecurity {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int SALT_BYTES = 16;
    private static final int ITERATIONS = 65_536;
    private static final int KEY_LENGTH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Minimum length accepted for any password set through the web UI or commands. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    private PasswordSecurity() {
    }

    /**
     * Generates a secure, random alphanumeric password.
     *
     * @param length Desired password length (minimum 8, default 16).
     * @return Generated random password string.
     */
    public static String generateRandomPassword(int length) {
        int len = Math.max(8, length);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int index = RANDOM.nextInt(ALPHABET.length());
            sb.append(ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Generates a new random cryptographic salt.
     *
     * @return Hex-encoded 16-byte (32-character) salt.
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    /**
     * Hashes a password using PBKDF2WithHmacSHA256 and the provided hex-encoded salt.
     *
     * @param password Plaintext password.
     * @param saltHex  Hex-encoded salt string.
     * @return Hex-encoded 256-bit hash.
     */
    public static String hashPassword(String password, String saltHex) {
        if (password == null || saltHex == null) {
            throw new IllegalArgumentException("Password and salt must not be null");
        }
        byte[] salt = HexFormat.of().parseHex(saltHex);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to calculate PBKDF2 hash", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Securely verifies a plaintext password candidate against the stored salt and hash.
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param candidatePassword Plaintext password to test.
     * @param saltHex           Hex-encoded salt from config.
     * @param expectedHashHex   Hex-encoded hash from config.
     * @return true if candidate password matches, false otherwise.
     */
    public static boolean verifyPassword(String candidatePassword, String saltHex, String expectedHashHex) {
        if (candidatePassword == null || saltHex == null || expectedHashHex == null) {
            return false;
        }
        if (saltHex.isBlank() || expectedHashHex.isBlank()) {
            return false;
        }
        try {
            String calculatedHash = hashPassword(candidatePassword, saltHex);
            byte[] a = calculatedHash.getBytes(StandardCharsets.UTF_8);
            byte[] b = expectedHashHex.getBytes(StandardCharsets.UTF_8);
            return MessageDigest.isEqual(a, b);
        } catch (Exception e) {
            return false;
        }
    }
}
