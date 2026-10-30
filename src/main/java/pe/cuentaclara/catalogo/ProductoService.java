package pe.cuentaclara.catalogo;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.acceso.UsuarioActualPrincipal;
import pe.cuentaclara.auditoria.AuditoriaService;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.comun.SoporteJsonComponent;
import pe.cuentaclara.tiendas.AccesoTiendaService;

import java.net.URI;

@Service
public class ProductoService {
    private final ProductoRepository productos;
    private final AccesoTiendaService acceso;
    private final AuditoriaService auditoria;
    private final SoporteJsonComponent json;

    public ProductoService(
            ProductoRepository productos,
            AccesoTiendaService acceso,
            AuditoriaService auditoria,
            SoporteJsonComponent json) {
        this.productos = productos;
        this.acceso = acceso;
        this.auditoria = auditoria;
        this.json = json;
    }

    @Transactional
    public Long crear(
            UsuarioActualPrincipal usuarioActual,
            Long tiendaSeleccionada,
            ProductoEntradaDTO solicitud) {
        var tienda = acceso.resolverTienda(usuarioActual, tiendaSeleccionada, true);
        ProductoEntity producto = new ProductoEntity();
        producto.setTienda(tienda);
        aplicarDatos(producto, solicitud);
        productos.saveAndFlush(producto);
        auditoria.registrar(
                usuarioActual.id(), tienda.getId(), "Producto registrado", producto.getId(), "");
        return producto.getId();
    }

    @Transactional
    public void actualizar(
            UsuarioActualPrincipal usuarioActual,
            Long tiendaSeleccionada,
            Long id,
            JsonNode patch) {
        var tienda = acceso.resolverTienda(usuarioActual, tiendaSeleccionada, true);
        ProductoEntity producto = buscar(id, tienda.getId());
        aplicarDatos(
                producto,
                json.combinar(ProductoSalidaDTO.desde(producto), patch, ProductoEntradaDTO.class));
        auditoria.registrar(usuarioActual.id(), tienda.getId(), "Producto actualizado", id, "");
    }

    public ProductoEntity buscar(Long id, Long tiendaId) {
        ProductoEntity producto =
                productos
                        .findById(id)
                        .orElseThrow(
                                () ->
                                        new ReglaNegocioException(
                                                "No se encontró el producto.", 404));
        if (!producto.getTienda().getId().equals(tiendaId))
            throw new ReglaNegocioException("No se encontró el registro en esta tienda.", 404);
        return producto;
    }

    private void aplicarDatos(ProductoEntity producto, ProductoEntradaDTO solicitud) {
        String imagen = limpiar(solicitud.imagen());
        if (!imagen.isEmpty()) {
            URI uri = URI.create(imagen);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null)
                throw new ReglaNegocioException("La imagen debe ser un enlace HTTPS.");
        }
        producto.setNombre(solicitud.nombre().strip());
        producto.setDescripcion(limpiar(solicitud.descripcion()));
        producto.setMarca(limpiar(solicitud.marca()));
        producto.setProveedor(limpiar(solicitud.proveedor()));
        producto.setUnidad(solicitud.unidad().strip());
        producto.setImagen(imagen);
        producto.setPrecioContado(solicitud.precioContado());
        producto.setPrecioCredito(solicitud.precioCredito());
        producto.setMoneda(solicitud.moneda());
        producto.setPermiteCuotas(solicitud.permiteCuotas() == null || solicitud.permiteCuotas());
        producto.setActivo(solicitud.activo() == null || solicitud.activo());
    }

    private String limpiar(String value) {
        return value == null ? "" : value.strip();
    }
}
