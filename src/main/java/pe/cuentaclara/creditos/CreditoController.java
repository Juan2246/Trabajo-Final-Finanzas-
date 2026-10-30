package pe.cuentaclara.creditos;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.acceso.UsuarioActualPrincipal;

@RestController
@RequestMapping("/api/sales")
@PreAuthorize("hasAnyRole('PLATFORM', 'MERCHANT')")
public class CreditoController {
    private final CreditoService creditos;

    public CreditoController(CreditoService creditos) {
        this.creditos = creditos;
    }

    @PostMapping(value = "/preview", consumes = "application/json")
    public SimulacionVentaDTO simular(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId,
            @Valid @RequestBody VentaEntradaDTO solicitud) {
        return creditos.simular(usuario, tiendaId, solicitud);
    }

    @PostMapping(consumes = "application/json")
    public SimulacionVentaDTO crear(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId,
            @Valid @RequestBody VentaEntradaDTO solicitud) {
        return creditos.crear(usuario, tiendaId, solicitud);
    }
}
