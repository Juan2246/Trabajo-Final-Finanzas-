package pe.cuentaclara.acceso;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.cuentaclara.config.AppProperties;
import pe.cuentaclara.config.SeguridadProperties;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;

@Component
public class CifradoSecretosComponent {
    private final SecretKeySpec clave;
    private final SecureRandom random = new SecureRandom();

    public CifradoSecretosComponent(
            AppProperties app, SeguridadProperties seguridad, DataSource source) throws Exception {
        String configurada = seguridad.encryptionKey();
        byte[] bytes;
        if (configurada != null && !configurada.isBlank())
            bytes = Base64.getDecoder().decode(configurada);
        else {
            Path archivo =
                    Path.of(app.databasePath()).toAbsolutePath().getParent().resolve("mfa.key");
            if (!Files.exists(archivo)) {
                Long existentes =
                        new JdbcTemplate(source)
                                .queryForObject(
                                        "SELECT count(*) FROM second_factors WHERE secret IS NOT"
                                            + " NULL OR pending_secret IS NOT NULL",
                                        Long.class);
                if (existentes != null && existentes > 0)
                    throw new IllegalStateException(
                            "Falta la clave MFA de esta base de datos. Restaura mfa.key desde su"
                                + " copia de seguridad.");
                byte[] nueva = new byte[32];
                random.nextBytes(nueva);
                Files.createDirectories(archivo.getParent());
                Files.write(archivo, nueva, StandardOpenOption.CREATE_NEW);
                try {
                    Files.setPosixFilePermissions(
                            archivo,
                            java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
                } catch (UnsupportedOperationException ignored) {
                    /* Windows usa los permisos de la carpeta de datos. */
                }
            }
            bytes = Files.readAllBytes(archivo);
        }
        if (bytes.length != 32)
            throw new IllegalStateException("La clave MFA debe contener 32 bytes.");
        clave = new SecretKeySpec(bytes, "AES");
    }

    public String cifrar(String texto) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(128, iv));
            byte[] resultado = cipher.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder()
                    .encodeToString(
                            java.nio.ByteBuffer.allocate(iv.length + resultado.length)
                                    .put(iv)
                                    .put(resultado)
                                    .array());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo proteger el segundo factor.", e);
        }
    }

    public String descifrar(String texto) {
        try {
            byte[] bytes = Base64.getDecoder().decode(texto);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(128, bytes, 0, 12));
            return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudo abrir el segundo factor. Revisa la clave del servidor.", e);
        }
    }

    public static String huella(String texto) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(texto.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
