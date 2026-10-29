package pe.cuentaclara.auth;

import java.security.SecureRandom;
import java.util.Base64;

public final class RandomTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomTokens() {}

    public static String token(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public static int digit() {
        return RANDOM.nextInt(9) + 1;
    }
}
