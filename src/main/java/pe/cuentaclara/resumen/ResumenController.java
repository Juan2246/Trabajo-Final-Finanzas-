package pe.cuentaclara.resumen;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.acceso.UsuarioActualPrincipal;

@RestController
public class ResumenController {
    private final ResumenService dashboard;

    public ResumenController(ResumenService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/api/data")
    public ResumenSalidaDTO obtenerResumen(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId) {
        return dashboard.obtenerResumen(usuario, tiendaId);
    }
}
