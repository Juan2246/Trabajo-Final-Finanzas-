package pe.cuentaclara.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.config.AppProperties;
import pe.cuentaclara.shared.BusinessTime;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    private final AppProperties properties;
    private final BusinessTime time;

    public AuthController(AuthService auth, AppProperties properties, BusinessTime time) {
        this.auth = auth;
        this.properties = properties;
        this.time = time;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "service", "Cuenta Clara");
    }

    @GetMapping("/challenge")
    public Map<String, String> challenge() {
        return auth.challenge();
    }

    @PostMapping(value = "/login", consumes = "application/json")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        CurrentUser user = auth.login(body, request.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(user.sessionToken(), Duration.ofHours(8)))
                .body(Map.of("user", user.response(), "csrf", user.csrf(), "today", time.today()));
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal CurrentUser user) {
        return Map.of("user", user.response(), "csrf", user.csrf(), "today", time.today());
    }

    @PostMapping(value = "/logout", consumes = "application/json")
    public ResponseEntity<?> logout(@AuthenticationPrincipal CurrentUser user) {
        auth.logout(user);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO))
                .body(Map.of("ok", true));
    }

    @PostMapping(value = "/password", consumes = "application/json")
    public Map<String, Boolean> password(
            @AuthenticationPrincipal CurrentUser user, @Valid @RequestBody PasswordRequest body) {
        auth.changePassword(user, body);
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
