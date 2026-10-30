package pe.cuentaclara.acceso;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.auditoria.AuditoriaService;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.comun.RelojNegocioComponent;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;

@Service
public class SegundoFactorService {
    private final SegundoFactorRepository factores;
    private final CodigoRecuperacionRepository codigos;
    private final PasoAccesoRepository pasos;
    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final SesionService servicioSesion;
    private final ContrasenaService contrasenas;
    private final CifradoSecretosComponent cifrado;
    private final RelojNegocioComponent reloj;
    private final AuditoriaService auditoria;
    private final TimeBasedOneTimePasswordGenerator generador =
            new TimeBasedOneTimePasswordGenerator();
    private final Base32 base32 = new Base32();
    private final SecureRandom random = new SecureRandom();

    public SegundoFactorService(
            SegundoFactorRepository factores,
            CodigoRecuperacionRepository codigos,
            PasoAccesoRepository pasos,
            UsuarioRepository usuarios,
            SesionRepository sesiones,
            SesionService servicioSesion,
            ContrasenaService contrasenas,
            CifradoSecretosComponent cifrado,
            RelojNegocioComponent reloj,
            AuditoriaService auditoria) {
        this.factores = factores;
        this.codigos = codigos;
        this.pasos = pasos;
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.servicioSesion = servicioSesion;
        this.contrasenas = contrasenas;
        this.cifrado = cifrado;
        this.reloj = reloj;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> estado(UsuarioActualPrincipal actual) {
        var factor = factores.findById(actual.id()).orElse(null);
        return Map.of(
                "enabled",
                factor != null && factor.getSecretoCifrado() != null,
                "recovery_remaining",
                codigos.countByUsuarioId(actual.id()));
    }

    @Transactional
    public String iniciarAcceso(UsuarioEntity usuario) {
        var factor = factores.findById(usuario.getId()).orElse(null);
        if (factor == null || factor.getSecretoCifrado() == null) return null;
        comprobarBloqueo(factor);
        pasos.deleteByVenceEnLessThan(reloj.segundosEpoch());
        pasos.deleteByUsuarioId(usuario.getId());
        String token = TokensAleatoriosUtil.token(32);
        PasoAccesoEntity paso = new PasoAccesoEntity();
        paso.setId(CifradoSecretosComponent.huella(token));
        paso.setUsuarioId(usuario.getId());
        paso.setVersionCredencial(version(usuario, factor));
        paso.setVenceEn(reloj.segundosEpoch() + 180);
        pasos.save(paso);
        return token;
    }

    @Transactional(noRollbackFor = VerificacionMfaException.class)
    public UsuarioActualPrincipal verificarAcceso(String token, String codigo) {
        var paso =
                pasos.findById(CifradoSecretosComponent.huella(token))
                        .orElseThrow(
                                () ->
                                        new ReglaNegocioException(
                                                "Vuelve a ingresar tu usuario y contraseña.", 401));
        UsuarioEntity usuario = usuarios.findById(paso.getUsuarioId()).orElseThrow();
        var factor = factores.findById(usuario.getId()).orElseThrow();
        if (paso.getVenceEn() <= reloj.segundosEpoch()
                || !version(usuario, factor).equals(paso.getVersionCredencial())
                || !Boolean.TRUE.equals(usuario.getActivo())
                || (usuario.getTienda() != null
                        && !Boolean.TRUE.equals(usuario.getTienda().getActivo())))
            throw new ReglaNegocioException(
                    "La verificación venció. Vuelve a iniciar sesión.", 401);
        verificarCodigo(factor, codigo, false);
        pasos.deleteByUsuarioId(usuario.getId());
        return servicioSesion.abrir(usuario);
    }

    @Transactional(noRollbackFor = VerificacionMfaException.class)
    public Map<String, String> preparar(
            UsuarioActualPrincipal actual, ConfirmacionSeguridadDTO entrada) {
        var factor =
                factores.findById(actual.id())
                        .orElseGet(
                                () -> {
                                    var f = new SegundoFactorEntity();
                                    f.setUsuarioId(actual.id());
                                    return factores.save(f);
                                });
        autenticar(actual, entrada, factor);
        if (factor.getSecretoCifrado() != null)
            throw new ReglaNegocioException("El segundo factor ya está activo.");
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        String secreto = base32.encodeToString(bytes).replace("=", "");
        factor.setPendienteCifrado(cifrado.cifrar(secreto));
        factor.setPendienteHasta(reloj.segundosEpoch() + 600);
        String uri =
                "otpauth://totp/"
                        + URLEncoder.encode(
                                "Cuenta Clara:" + actual.username(), StandardCharsets.UTF_8)
                        + "?secret="
                        + secreto
                        + "&issuer=Cuenta%20Clara&algorithm=SHA1&digits=6&period=30";
        return Map.of("secret", secreto, "qr", qr(uri));
    }

    @Transactional(noRollbackFor = VerificacionMfaException.class)
    public List<String> activar(UsuarioActualPrincipal actual, String codigo) {
        var factor =
                factores.findById(actual.id())
                        .orElseThrow(
                                () ->
                                        new ReglaNegocioException(
                                                "Primero configura tu autenticador."));
        if (factor.getSecretoCifrado() != null
                || factor.getPendienteCifrado() == null
                || factor.getPendienteHasta() <= reloj.segundosEpoch())
            throw new ReglaNegocioException("La configuración venció. Vuelve a generar el QR.");
        verificarCodigo(factor, codigo, true);
        factor.setSecretoCifrado(factor.getPendienteCifrado());
        factor.setPendienteCifrado(null);
        factor.setPendienteHasta(0L);
        factor.setHabilitadoDesde(reloj.ahora());
        revocarOtrasSesiones(actual);
        auditoria.registrar(actual.id(), actual.tiendaId(), "Segundo factor activado", null, "");
        return nuevosCodigos(actual.id());
    }

    @Transactional(noRollbackFor = VerificacionMfaException.class)
    public List<String> renovar(UsuarioActualPrincipal actual, ConfirmacionSeguridadDTO entrada) {
        var factor = obtenerActivo(actual);
        autenticar(actual, entrada, factor);
        verificarCodigo(factor, entrada.codigo(), false);
        auditoria.registrar(
                actual.id(), actual.tiendaId(), "Códigos de recuperación renovados", null, "");
        return nuevosCodigos(actual.id());
    }

    @Transactional(noRollbackFor = VerificacionMfaException.class)
    public void desactivar(UsuarioActualPrincipal actual, ConfirmacionSeguridadDTO entrada) {
        var factor = obtenerActivo(actual);
        autenticar(actual, entrada, factor);
        verificarCodigo(factor, entrada.codigo(), false);
        codigos.deleteByUsuarioId(actual.id());
        pasos.deleteByUsuarioId(actual.id());
        factor.setSecretoCifrado(null);
        factor.setPendienteCifrado(null);
        factor.setPendienteHasta(0L);
        factor.setUltimoPaso(-1L);
        factor.setHabilitadoDesde(null);
        revocarOtrasSesiones(actual);
        auditoria.registrar(actual.id(), actual.tiendaId(), "Segundo factor desactivado", null, "");
    }

    private SegundoFactorEntity obtenerActivo(UsuarioActualPrincipal actual) {
        return factores.findById(actual.id())
                .filter(f -> f.getSecretoCifrado() != null)
                .orElseThrow(() -> new ReglaNegocioException("Activa primero el segundo factor."));
    }

    private String version(UsuarioEntity u, SegundoFactorEntity f) {
        return CifradoSecretosComponent.huella(u.getPassword() + ":" + f.getSecretoCifrado());
    }

    private void autenticar(
            UsuarioActualPrincipal actual,
            ConfirmacionSeguridadDTO entrada,
            SegundoFactorEntity factor) {
        comprobarBloqueo(factor);
        if (!contrasenas.verificar(
                entrada.password(), usuarios.findById(actual.id()).orElseThrow().getPassword()))
            fallo(factor);
    }

    private void comprobarBloqueo(SegundoFactorEntity factor) {
        if (factor.getBloqueadoHasta() > reloj.segundosEpoch())
            throw new ReglaNegocioException("Demasiados intentos. Espera 15 minutos.", 429);
        if (factor.getBloqueadoHasta() > 0) {
            factor.setIntentos(0);
            factor.setBloqueadoHasta(0L);
        }
    }

    private void fallo(SegundoFactorEntity factor) {
        factor.setIntentos(factor.getIntentos() + 1);
        if (factor.getIntentos() >= 5) factor.setBloqueadoHasta(reloj.segundosEpoch() + 900);
        factores.save(factor);
        throw new VerificacionMfaException(
                "No se pudo verificar. Revisa la contraseña o usa un código nuevo.");
    }

    private void verificarCodigo(SegundoFactorEntity factor, String entrada, boolean pendiente) {
        comprobarBloqueo(factor);
        String codigo =
                entrada == null
                        ? ""
                        : entrada.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
        long pasoActual = reloj.segundosEpoch() / 30;
        if (codigo.matches("[0-9]{6}")) {
            String secreto =
                    cifrado.descifrar(
                            pendiente ? factor.getPendienteCifrado() : factor.getSecretoCifrado());
            for (long paso = pasoActual - 1; paso <= pasoActual + 1; paso++) {
                if (paso <= factor.getUltimoPaso()) continue;
                try {
                    if (generador.validateOneTimePassword(
                            new SecretKeySpec(base32.decode(secreto), "HmacSHA1"),
                            Instant.ofEpochSecond(paso * 30),
                            codigo)) {
                        factor.setUltimoPaso(paso);
                        factor.setIntentos(0);
                        factor.setBloqueadoHasta(0L);
                        return;
                    }
                } catch (java.security.InvalidKeyException e) {
                    throw new IllegalStateException(e);
                }
            }
        } else if (!pendiente && codigo.matches("[A-Z2-7]{20}")) {
            var recuperacion =
                    codigos.findById(CifradoSecretosComponent.huella(codigo))
                            .filter(c -> c.getUsuarioId().equals(factor.getUsuarioId()));
            if (recuperacion.isPresent()) {
                codigos.delete(recuperacion.get());
                factor.setIntentos(0);
                factor.setBloqueadoHasta(0L);
                return;
            }
        }
        fallo(factor);
    }

    private List<String> nuevosCodigos(Long usuarioId) {
        codigos.deleteByUsuarioId(usuarioId);
        codigos.flush();
        List<String> resultado = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            byte[] bytes = new byte[13];
            random.nextBytes(bytes);
            String codigo = base32.encodeToString(bytes).substring(0, 20);
            var registro = new CodigoRecuperacionEntity();
            registro.setId(CifradoSecretosComponent.huella(codigo));
            registro.setUsuarioId(usuarioId);
            codigos.save(registro);
            resultado.add(
                    codigo.substring(0, 5)
                            + "-"
                            + codigo.substring(5, 10)
                            + "-"
                            + codigo.substring(10, 15)
                            + "-"
                            + codigo.substring(15));
        }
        return resultado;
    }

    private void revocarOtrasSesiones(UsuarioActualPrincipal actual) {
        sesiones.findAllByUsuarioId(actual.id()).stream()
                .filter(s -> !s.getToken().equals(actual.sessionToken()))
                .forEach(sesiones::delete);
        pasos.deleteByUsuarioId(actual.id());
    }

    private String qr(String uri) {
        try {
            var matriz = new QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, 280, 280);
            var imagen = new BufferedImage(280, 280, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 280; y++)
                for (int x = 0; x < 280; x++)
                    imagen.setRGB(x, y, matriz.get(x, y) ? 0x132F28 : 0xFFFFFF);
            var salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", salida);
            return "data:image/png;base64,"
                    + Base64.getEncoder().encodeToString(salida.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el QR.", e);
        }
    }
}
