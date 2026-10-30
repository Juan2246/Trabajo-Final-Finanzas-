package pe.cuentaclara.catalogo;

import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import pe.cuentaclara.acceso.UsuarioActualPrincipal;

@RestController
@RequestMapping("/api/product-images")
public class ImagenProductoController {
    private final ImagenProductoService servicio;

    public ImagenProductoController(ImagenProductoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object subir(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario,
            @RequestParam(name = "store_id", required = false) Long tiendaId,
            @RequestParam MultipartFile file) {
        return servicio.subir(usuario, tiendaId, file);
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> obtener(
            @AuthenticationPrincipal UsuarioActualPrincipal usuario, @PathVariable String id) {
        var imagen = servicio.obtener(usuario, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagen.getMime()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(imagen.getContenido());
    }
}
