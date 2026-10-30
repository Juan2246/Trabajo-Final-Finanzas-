package pe.cuentaclara.tiendas;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.acceso.UsuarioActualPrincipal;

import java.util.Map;

@RestController
@RequestMapping("/api/stores")
@PreAuthorize("hasRole('PLATFORM')")
public class TiendaController {
    private final TiendaService tiendas;

    public TiendaController(TiendaService tiendas) {
        this.tiendas = tiendas;
    }

    @PostMapping(consumes = "application/json")
    public Map<String, Long> crear(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @Valid @RequestBody TiendaEntradaDTO solicitud) {
        return Map.of("id", tiendas.crear(usuario, solicitud));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json")
    public Map<String, Boolean> actualizar(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @PathVariable Long id,
            @RequestBody JsonNode patch) {
        tiendas.actualizar(usuario, id, patch);
        return Map.of("ok", true);
    }
}
