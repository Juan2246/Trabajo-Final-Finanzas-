package pe.cuentaclara.acceso;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/security/mfa")
public class SegundoFactorController {
    private final SegundoFactorService servicio;

    public SegundoFactorController(SegundoFactorService servicio) {
        this.servicio = servicio;
    }

    public record CodigoDTO(@NotBlank @Pattern(regexp = "[0-9]{6}") String codigo) {}

    @GetMapping
    public ResponseEntity<?> estado(@AuthenticationPrincipal UsuarioActualPrincipal usuario) {
        return respuesta(servicio.estado(usuario));
    }

    @PostMapping("/setup")
    public ResponseEntity<?> preparar(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @Valid @RequestBody ConfirmacionSeguridadDTO entrada) {
        return respuesta(servicio.preparar(usuario, entrada));
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> activar(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @Valid @RequestBody CodigoDTO entrada) {
        return respuesta(Map.of("recovery_codes", servicio.activar(usuario, entrada.codigo())));
    }

    @PostMapping("/recovery")
    public ResponseEntity<?> renovar(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @Valid @RequestBody ConfirmacionSeguridadDTO entrada) {
        return respuesta(Map.of("recovery_codes", servicio.renovar(usuario, entrada)));
    }

    @PostMapping("/disable")
    public ResponseEntity<?> desactivar(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @Valid @RequestBody ConfirmacionSeguridadDTO entrada) {
        servicio.desactivar(usuario, entrada);
        return respuesta(Map.of("ok", true));
    }

    private ResponseEntity<?> respuesta(Object datos) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(datos);
    }
}
