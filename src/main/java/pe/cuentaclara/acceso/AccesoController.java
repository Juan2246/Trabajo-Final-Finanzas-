package pe.cuentaclara.acceso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.comun.RelojNegocioComponent;
import pe.cuentaclara.config.AppProperties;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AccesoController {
    private final SegundoFactorService segundoFactor;
    private final AccesoService acceso;
    private final AppProperties properties;
    private final RelojNegocioComponent reloj;

    public AccesoController(
            AccesoService acceso,
            AppProperties properties,
            RelojNegocioComponent reloj,
            SegundoFactorService segundoFactor) {
        this.segundoFactor = segundoFactor;
        this.acceso = acceso;
        this.properties = properties;
        this.reloj = reloj;
    }

    @GetMapping("/health")
    public Map<String, Object> estadoServicio() {
        return Map.of("ok", true, "service", "Cuenta Clara");
    }

    @GetMapping("/challenge")
    public Map<String, String> verificacion() {
        return acceso.verificacion();
    }

    @PostMapping(value = "/login", consumes = "application/json")
    public ResponseEntity<?> login(
            @Valid @RequestBody AccesoEntradaDTO body, HttpServletRequest solicitud) {
        AccesoResultadoDTO resultado = acceso.login(body, solicitud.getRemoteAddr());
        if (resultado.tokenMfa() != null)
            return ResponseEntity.ok()
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(Map.of("mfa_required", true, "mfa_token", resultado.tokenMfa()));
        return respuestaSesion(resultado.usuario());
    }

    public record PasoMfaDTO(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100)
                    String token,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 40)
                    String codigo) {}

    @PostMapping(value = "/login/mfa", consumes = "application/json")
    public ResponseEntity<?> verificarMfa(@Valid @RequestBody PasoMfaDTO entrada) {
        return respuestaSesion(segundoFactor.verificarAcceso(entrada.token(), entrada.codigo()));
    }

    private ResponseEntity<?> respuestaSesion(UsuarioActualPrincipal usuario) {
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(usuario.sessionToken(), Duration.ofHours(8)))
                .body(
                        Map.of(
                                "user",
                                usuario.datosPublicos(),
                                "csrf",
                                usuario.csrf(),
                                "today",
                                reloj.hoy()));
    }

    @GetMapping("/me")
    public Map<String, Object> sesionActual(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario) {
        return Map.of(
                "user", usuario.datosPublicos(), "csrf", usuario.csrf(), "today", reloj.hoy());
    }

    @PostMapping(value = "/logout", consumes = "application/json")
    public ResponseEntity<?> logout(@AuthenticationPrincipal UsuarioActualPrincipal usuario) {
        acceso.logout(usuario);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
                .body(Map.of("ok", true));
    }

    @PostMapping(value = "/password", consumes = "application/json")
    public Map<String, Boolean> password(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @Valid @RequestBody ContrasenaEntradaDTO body) {
        acceso.cambiarContrasena(usuario, body);
        return Map.of("ok", true);
    }

    private String cookie(String value, Duration age) {
        return ResponseCookie.from("cc_session", value)
                .path("/")
                .httpOnly(true)
                .secure(properties.secureCookies())
                .sameSite("Lax")
                .maxAge(age)
                .build()
                .toString();
    }
}
