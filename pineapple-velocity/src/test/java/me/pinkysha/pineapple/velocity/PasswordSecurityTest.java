package me.pinkysha.pineapple.velocity;

import me.pinkysha.pineapple.velocity.security.PasswordSecurity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordSecurityTest {

    @Test
    void testGenerateRandomPassword() {
        String pass1 = PasswordSecurity.generateRandomPassword(16);
        String pass2 = PasswordSecurity.generateRandomPassword(16);

        assertNotNull(pass1);
        assertEquals(16, pass1.length());
        assertNotNull(pass2);
        assertEquals(16, pass2.length());
        assertNotEquals(pass1, pass2);
    }

    @Test
    void testSaltGenerationAndChange() {
        String salt1 = PasswordSecurity.generateSalt();
        String salt2 = PasswordSecurity.generateSalt();

        assertNotNull(salt1);
        assertEquals(32, salt1.length()); // 16 bytes = 32 hex chars
        assertNotNull(salt2);
        assertEquals(32, salt2.length());
        assertNotEquals(salt1, salt2, "Each generated salt must be unique");
    }

    @Test
    void testHashAndVerification() {
        String password = "MySuperSecretPassword123!";
        String salt = PasswordSecurity.generateSalt();
        String hash = PasswordSecurity.hashPassword(password, salt);

        assertNotNull(hash);
        assertEquals(64, hash.length()); // 256 bits = 32 bytes = 64 hex chars

        // Verification should succeed
        assertTrue(PasswordSecurity.verifyPassword(password, salt, hash));

        // Wrong password should fail
        assertFalse(PasswordSecurity.verifyPassword("WrongPassword", salt, hash));

        // Wrong salt should fail
        String differentSalt = PasswordSecurity.generateSalt();
        assertFalse(PasswordSecurity.verifyPassword(password, differentSalt, hash));

        // Null checks
        assertFalse(PasswordSecurity.verifyPassword(null, salt, hash));
        assertFalse(PasswordSecurity.verifyPassword(password, null, hash));
        assertFalse(PasswordSecurity.verifyPassword(password, salt, null));
    }

    @Test
    void testSaltChangesOnPasswordChange() {
        String pass1 = "OldPassword123";
        String salt1 = PasswordSecurity.generateSalt();
        String hash1 = PasswordSecurity.hashPassword(pass1, salt1);

        String pass2 = "NewPassword456";
        String salt2 = PasswordSecurity.generateSalt();
        String hash2 = PasswordSecurity.hashPassword(pass2, salt2);

        assertNotEquals(salt1, salt2, "Salt must change on each password change");
        assertNotEquals(hash1, hash2);

        assertTrue(PasswordSecurity.verifyPassword(pass1, salt1, hash1));
        assertTrue(PasswordSecurity.verifyPassword(pass2, salt2, hash2));
        assertFalse(PasswordSecurity.verifyPassword(pass1, salt2, hash2));
    }
}
