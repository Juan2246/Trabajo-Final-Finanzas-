package pe.cuentaclara.catalogo;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import pe.cuentaclara.acceso.UsuarioActual;

import java.util.Map;

@RestController
@RequestMapping("/api/products")
@PreAuthorize("hasAnyRole('PLATFORM', 'MERCHANT')")
public class ProductoController {
    private final ProductoService productos;

    public ProductoController(ProductoService productos) {
        this.productos = productos;
    }

    @PostMapping(consumes = "application/json")
    public Map<String, Long> crear(
            @AuthenticationPrincipal UsuarioActual usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId,
            @Valid @RequestBody ProductoEntradaDTO solicitud) {
        return Map.of("id", productos.crear(usuario, tiendaId, solicitud));
    }

    @PatchMapping(value = "/{id}", consumes = "application/json")
    public Map<String, Boolean> actualizar(
            @AuthenticationPrincipal UsuarioActual usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId,
            @PathVariable Long id,
            @RequestBody JsonNode patch) {
        productos.actualizar(usuario, tiendaId, id, patch);
        return Map.of("ok", true);
    }
}
