package pe.cuentaclara.acceso;

import java.security.SecureRandom;
import java.util.Base64;

public final class TokensAleatorios {
    private static final SecureRandom RANDOM = new SecureRandom();

    private TokensAleatorios() {}

    public static String token(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static int digito() {
        return RANDOM.nextInt(9) + 1;
    }
}
