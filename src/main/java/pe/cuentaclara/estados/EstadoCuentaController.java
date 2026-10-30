package pe.cuentaclara.estados;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.acceso.UsuarioActualPrincipal;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/statement")
public class EstadoCuentaController {
    private final EstadoCuentaService estados;

    public EstadoCuentaController(EstadoCuentaService estados) {
        this.estados = estados;
    }

    @GetMapping
    public ObjectNode consultar(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId,
            @RequestParam(name = "customer_id", required = false) Long clienteId,
            @RequestParam(name = "date", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fecha,
            @RequestParam(name = "cycle", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate ciclo) {
        return estados.consultar(usuario, tiendaId, clienteId, fecha, ciclo);
    }
}
