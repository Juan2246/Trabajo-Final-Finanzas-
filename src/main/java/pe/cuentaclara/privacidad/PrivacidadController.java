package pe.cuentaclara.privacidad;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.acceso.UsuarioActualPrincipal;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.resumen.ResumenService;

import java.util.Map;

@RestController
@RequestMapping("/api/privacy")
public class PrivacidadController {
    private final PrivacidadService servicio;
    private final ResumenService resumen;

    public PrivacidadController(PrivacidadService servicio, ResumenService resumen) {
        this.servicio = servicio;
        this.resumen = resumen;
    }

    public record PoliticaDTO(
            @Min(30) @Max(3650) int retentionDays,
            @NotNull @Size(max = 120) String contact,
            @NotBlank @Size(max = 128) String password) {}

    public record ConfirmacionDTO(@NotBlank @Size(max = 128) String password) {}

    @GetMapping
    public Object consultar(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId) {
        return servicio.consultar(usuario, tiendaId);
    }

    @PostMapping("/policy")
    public Object configurar(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId,
            @Valid @RequestBody PoliticaDTO entrada) {
        servicio.configurar(
                usuario, tiendaId, entrada.retentionDays(), entrada.contact(), entrada.password());
        return Map.of("ok", true);
    }

    @PostMapping("/request")
    public Object solicitar(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @Valid @RequestBody ConfirmacionDTO entrada) {
        servicio.solicitar(usuario, false, entrada.password());
        return Map.of("ok", true);
    }

    @PostMapping("/cancel")
    public Object cancelar(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @Valid @RequestBody ConfirmacionDTO entrada) {
        servicio.solicitar(usuario, true, entrada.password());
        return Map.of("ok", true);
    }

    @PostMapping("/process")
    public Object procesar(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId,
            @Valid @RequestBody ConfirmacionDTO entrada) {
        return Map.of("processed", servicio.procesarTienda(usuario, tiendaId, entrada.password()));
    }

    @GetMapping("/export")
    public ResponseEntity<?> exportar(@AuthenticationPrincipal UsuarioActualPrincipal usuario) {
        if (!usuario.esCliente())
            throw new ReglaNegocioException(
                    "Descarga disponible desde la cuenta del cliente.", 403);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=mis-datos-cuenta-clara.json")
                .body(resumen.obtenerResumen(usuario, null));
    }
}
