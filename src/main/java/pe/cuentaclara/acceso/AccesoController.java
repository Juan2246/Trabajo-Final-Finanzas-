package pe.cuentaclara.acceso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.comun.RelojNegocio;
import pe.cuentaclara.config.AppProperties;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AccesoController {
    private final AccesoService acceso;
    private final AppProperties properties;
    private final RelojNegocio reloj;

    public AccesoController(AccesoService acceso, AppProperties properties, RelojNegocio reloj) {
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
        UsuarioActual usuario = acceso.login(body, solicitud.getRemoteAddr());
        return ResponseEntity.ok()
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
    public Map<String, Object> sesionActual(@AuthenticationPrincipal UsuarioActual usuario) {
        return Map.of(
                "user", usuario.datosPublicos(), "csrf", usuario.csrf(), "today", reloj.hoy());
    }

    @PostMapping(value = "/logout", consumes = "application/json")
    public ResponseEntity<?> logout(@AuthenticationPrincipal UsuarioActual usuario) {
        acceso.logout(usuario);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
                .body(Map.of("ok", true));
    }

    @PostMapping(value = "/password", consumes = "application/json")
    public Map<String, Boolean> password(
            @AuthenticationPrincipal UsuarioActual usuario,
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
