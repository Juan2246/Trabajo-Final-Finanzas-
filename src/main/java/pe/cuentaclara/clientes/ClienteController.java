package pe.cuentaclara.clientes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.acceso.UsuarioActualPrincipal;

import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@PreAuthorize("hasAnyRole('PLATFORM', 'MERCHANT')")
public class ClienteController {
    private final ClienteService clientes;

    public ClienteController(ClienteService clientes) {
        this.clientes = clientes;
    }

    @PostMapping(consumes = "application/json")
    public Map<String, Long> crear(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId,
            @Valid @RequestBody ClienteEntradaDTO solicitud) {
        return Map.of("id", clientes.crear(usuario, tiendaId, solicitud));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json")
    public Map<String, Boolean> actualizar(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId,
            @PathVariable Long id,
            @RequestBody JsonNode patch) {
        clientes.actualizar(usuario, tiendaId, id, patch);
        return Map.of("ok", true);
    }
}
