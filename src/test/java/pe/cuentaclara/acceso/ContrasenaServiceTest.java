package pe.cuentaclara.acceso;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class ContrasenaServiceTest {
    private final ContrasenaService contrasenas = new ContrasenaService();

    @Test
    void acceptsLegacyHashesAndIdentifiesUpgrade() throws Exception {
        String legacy =
                new String(
                        getClass().getResourceAsStream("/legacy-password.txt").readAllBytes(),
                        StandardCharsets.UTF_8);
        assertTrue(contrasenas.verificar("PruebaSegura2026!", legacy));
        assertFalse(contrasenas.verificar("OtraSegura2026!", legacy));
        assertTrue(contrasenas.necesitaActualizarHash(legacy));
    }

    @Test
    void newPasswordsUseIndependentSalts() {
        String first = contrasenas.generarHash("PruebaSegura2026!");
        String second = contrasenas.generarHash("PruebaSegura2026!");
        assertNotEquals(first, second);
        assertFalse(contrasenas.necesitaActualizarHash(first));
        assertTrue(contrasenas.verificar("PruebaSegura2026!", first));
        assertFalse(contrasenas.verificar("OtraSegura2026!", first));
    }

    @Test
    void rejectsWeakPasswords() {
        assertThrows(RuntimeException.class, () -> contrasenas.generarHash("short"));
        assertThrows(RuntimeException.class, () -> contrasenas.generarHash("alllowercase123"));
        assertThrows(RuntimeException.class, () -> contrasenas.generarHash("A".repeat(129)));
    }
}
