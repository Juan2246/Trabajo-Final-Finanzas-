package pe.cuentaclara.pagos;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.acceso.UsuarioActualPrincipal;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasAnyRole('PLATFORM', 'MERCHANT')")
public class PagoController {
    private final PagoService pagos;

    public PagoController(PagoService pagos) {
        this.pagos = pagos;
    }

    @PostMapping(value = "/preview", consumes = "application/json")
    public LiquidacionPagoDTO simular(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId,
            @Valid @RequestBody PagoEntradaDTO solicitud) {
        return pagos.simular(usuario, tiendaId, solicitud.clienteId());
    }

    @PostMapping(consumes = "application/json")
    public LiquidacionPagoDTO crear(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId,
            @Valid @RequestBody PagoEntradaDTO solicitud) {
        return pagos.crear(usuario, tiendaId, solicitud);
    }
}
