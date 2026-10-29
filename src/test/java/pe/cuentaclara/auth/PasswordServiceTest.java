package pe.cuentaclara.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class PasswordServiceTest {
    private final PasswordService passwords = new PasswordService();

    @Test
    void acceptsLegacyHashesAndIdentifiesUpgrade() throws Exception {
        String legacy =
                new String(
                        getClass().getResourceAsStream("/legacy-password.txt").readAllBytes(),
                        StandardCharsets.UTF_8);
        assertTrue(passwords.matches("PruebaSegura2026!", legacy));
        assertFalse(passwords.matches("OtraSegura2026!", legacy));
        assertTrue(passwords.needsUpgrade(legacy));
    }

    @Test
    void newPasswordsUseIndependentSalts() {
        String first = passwords.encode("PruebaSegura2026!");
        String second = passwords.encode("PruebaSegura2026!");
        assertNotEquals(first, second);
        assertFalse(passwords.needsUpgrade(first));
        assertTrue(passwords.matches("PruebaSegura2026!", first));
        assertFalse(passwords.matches("OtraSegura2026!", first));
    }

    @Test
    void rejectsWeakPasswords() {
        assertThrows(RuntimeException.class, () -> passwords.encode("short"));
        assertThrows(RuntimeException.class, () -> passwords.encode("alllowercase123"));
        assertThrows(RuntimeException.class, () -> passwords.encode("A".repeat(129)));
    }
}
