package me.pinkysha.pineapple.velocity;

import me.pinkysha.pineapple.velocity.config.VelocityConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class VelocityConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void testFirstLaunchGeneratesRandomPasswordAndHashesIt() throws IOException {
        Path configDir = tempDir.resolve("first_launch");
        VelocityConfig config = new VelocityConfig(configDir, LoggerFactory.getLogger("PineAppleTest"));

        assertTrue(config.isFirstLaunch(), "Should detect first launch when config.properties does not exist");
        assertNotNull(config.getGeneratedPassword(), "Generated password should not be null on first launch");
        assertEquals(16, config.getGeneratedPassword().length(), "Generated password should be 16 chars");

        assertFalse(config.getPasswordHash().isEmpty(), "Password hash must be generated");
        assertFalse(config.getPasswordSalt().isEmpty(), "Password salt must be generated");

        // Verify generated password succeeds
        assertTrue(config.verifyPassword(config.getGeneratedPassword()));
        assertFalse(config.verifyPassword("wrongPassword"));

        // Verify config.properties on disk
        Path configFile = configDir.resolve("config.properties");
        assertTrue(Files.exists(configFile), "config.properties must be created");

        String fileContent = Files.readString(configFile, StandardCharsets.UTF_8);
        assertTrue(fileContent.contains("# After the first site launch, use the command changepassword"),
                "Config file should contain English comment instructing to use changepassword");
        assertFalse(fileContent.contains("auth.password="), "Plaintext password line must not exist in config");

        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            props.load(reader);
        }

        assertNull(props.getProperty("auth.password"), "Plaintext password must NOT be stored in config.properties");
        assertEquals(config.getPasswordHash(), props.getProperty("auth.password_hash"));
        assertEquals(config.getPasswordSalt(), props.getProperty("auth.password_salt"));
    }

    @Test
    void testSubsequentLaunchPreservesPassword() {
        Path configDir = tempDir.resolve("subsequent_launch");

        // First launch
        VelocityConfig first = new VelocityConfig(configDir, LoggerFactory.getLogger("PineAppleTest"));
        String generated = first.getGeneratedPassword();
        String salt1 = first.getPasswordSalt();
        String hash1 = first.getPasswordHash();

        // Second launch
        VelocityConfig second = new VelocityConfig(configDir, LoggerFactory.getLogger("PineAppleTest"));
        assertFalse(second.isFirstLaunch(), "Should not be first launch when config already exists");
        assertNull(second.getGeneratedPassword());
        assertEquals(salt1, second.getPasswordSalt());
        assertEquals(hash1, second.getPasswordHash());

        assertTrue(second.verifyPassword(generated));
    }

    @Test
    void testPasswordChangeGeneratesNewSalt() throws IOException {
        Path configDir = tempDir.resolve("change_password");
        VelocityConfig config = new VelocityConfig(configDir, LoggerFactory.getLogger("PineAppleTest"));

        String initialSalt = config.getPasswordSalt();
        String initialHash = config.getPasswordHash();

        // Update password
        boolean updated = config.updatePassword("SuperNewSecurePassword999!");
        assertTrue(updated);

        String newSalt = config.getPasswordSalt();
        String newHash = config.getPasswordHash();

        assertNotEquals(initialSalt, newSalt, "Cryptographic salt must change on every password change");
        assertNotEquals(initialHash, newHash, "Hash must change when password changes");

        assertTrue(config.verifyPassword("SuperNewSecurePassword999!"));
        assertFalse(config.verifyPassword(config.getGeneratedPassword()));

        // Check persisted properties
        Path configFile = configDir.resolve("config.properties");
        String content = Files.readString(configFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("# After the first site launch, use the command changepassword"));

        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            props.load(reader);
        }

        assertNull(props.getProperty("auth.password"));
        assertEquals(newSalt, props.getProperty("auth.password_salt"));
        assertEquals(newHash, props.getProperty("auth.password_hash"));
    }

    @Test
    void testManualPlaintextPasswordMigration() throws IOException {
        Path configDir = tempDir.resolve("migration");
        Files.createDirectories(configDir);
        Path configFile = configDir.resolve("config.properties");

        // Simulate admin manually writing plaintext auth.password in config.properties
        Properties initialProps = new Properties();
        initialProps.setProperty("server.port", "8080");
        initialProps.setProperty("auth.enabled", "true");
        initialProps.setProperty("auth.username", "admin");
        initialProps.setProperty("auth.password", "MyManualPlaintextSecret");
        try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            initialProps.store(writer, "Manual admin config");
        }

        // Load config
        VelocityConfig config = new VelocityConfig(configDir, LoggerFactory.getLogger("PineAppleTest"));

        assertTrue(config.verifyPassword("MyManualPlaintextSecret"));
        assertFalse(config.getPasswordHash().isEmpty());
        assertFalse(config.getPasswordSalt().isEmpty());

        // Verify config was rewritten to remove plaintext
        Path writtenFile = configDir.resolve("config.properties");
        String content = Files.readString(writtenFile, StandardCharsets.UTF_8);
        assertTrue(content.contains("# After the first site launch, use the command changepassword"));

        Properties loadedProps = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            loadedProps.load(reader);
        }

        assertNull(loadedProps.getProperty("auth.password"), "Plaintext auth.password should be removed after hashing");
        assertEquals(config.getPasswordHash(), loadedProps.getProperty("auth.password_hash"));
        assertEquals(config.getPasswordSalt(), loadedProps.getProperty("auth.password_salt"));
    }
}
