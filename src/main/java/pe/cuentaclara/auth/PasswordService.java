package pe.cuentaclara.auth;

import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Service;

import pe.cuentaclara.shared.BusinessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

@Service
public class PasswordService {
    private static final String PREFIX = "{pbkdf2}";
    private final Pbkdf2PasswordEncoder encoder =
            new Pbkdf2PasswordEncoder(
                    "",
                    16,
                    600000,
                    Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);

    public String encode(String password) {
        if (password == null
                || password.length() < 12
                || password.length() > 128
                || !password.matches("(?s).*[A-Z].*")
                || !password.matches("(?s).*[a-z].*")
                || !password.matches("(?s).*\\d.*")) {
            throw new BusinessException(
                    "La contraseña debe tener 12–128 caracteres, mayúsculas, minúsculas y"
                            + " números.");
        }
        return PREFIX + encoder.encode(password);
    }

    public boolean matches(String password, String saved) {
        if (password == null || password.length() > 128 || saved == null) return false;
        if (saved.startsWith(PREFIX))
            return encoder.matches(password, saved.substring(PREFIX.length()));
        // La versión anterior usaba la sal hexadecimal como texto UTF-8.
        try {
            String[] parts = saved.split(":", 2);
            if (parts.length != 2) return false;
            PBEKeySpec spec =
                    new PBEKeySpec(
                            password.toCharArray(),
                            parts[0].getBytes(StandardCharsets.UTF_8),
                            310000,
                            256);
            byte[] actual =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                            .generateSecret(spec)
                            .getEncoded();
            spec.clearPassword();
            return MessageDigest.isEqual(actual, HexFormat.of().parseHex(parts[1]));
        } catch (Exception error) {
            return false;
        }
    }

    public boolean needsUpgrade(String saved) {
        return !saved.startsWith(PREFIX);
    }
}
