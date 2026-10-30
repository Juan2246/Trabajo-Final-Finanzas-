package pe.cuentaclara.tiendas;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.acceso.SesionRepository;
import pe.cuentaclara.acceso.UsuarioActual;
import pe.cuentaclara.acceso.UsuarioService;
import pe.cuentaclara.auditoria.AuditoriaService;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.comun.SoporteJson;

@Service
public class TiendaService {
    private final TiendaRepository tiendas;
    private final UsuarioService usuarios;
    private final SesionRepository sesiones;
    private final AuditoriaService auditoria;
    private final SoporteJson json;

    public TiendaService(
            TiendaRepository tiendas,
            UsuarioService usuarios,
            SesionRepository sesiones,
            AuditoriaService auditoria,
            SoporteJson json) {
        this.tiendas = tiendas;
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.auditoria = auditoria;
        this.json = json;
    }

    @Transactional
    public Long crear(UsuarioActual usuarioActual, TiendaEntradaDTO solicitud) {
        Tienda tienda = new Tienda();
        aplicarDatos(tienda, solicitud);
        tiendas.saveAndFlush(tienda);
        usuarios.crear(
                solicitud.username(),
                solicitud.adminName(),
                solicitud.password(),
                "merchant",
                tienda,
                null);
        auditoria.registrar(
                usuarioActual.id(),
                tienda.getId(),
                "Tienda creada",
                tienda.getId(),
                tienda.getNombre());
        return tienda.getId();
    }

    @Transactional
    public void actualizar(UsuarioActual usuarioActual, Long id, JsonNode patch) {
        Tienda tienda =
                tiendas.findById(id)
                        .orElseThrow(() -> new ReglaNegocioException("Tienda no encontrada.", 404));
        TiendaEntradaDTO solicitud =
                json.combinar(TiendaSalidaDTO.desde(tienda), patch, TiendaEntradaDTO.class);
        aplicarDatos(tienda, solicitud);
        if (!tienda.getActivo()) sesiones.deleteByUsuarioTiendaId(id);
        auditoria.registrar(usuarioActual.id(), id, "Tienda actualizada", id, "");
    }

    private void aplicarDatos(Tienda tienda, TiendaEntradaDTO solicitud) {
        tienda.setNombre(solicitud.nombre().strip());
        tienda.setRubro(solicitud.rubro().strip());
        tienda.setDireccion(solicitud.direccion() == null ? "" : solicitud.direccion().strip());
        tienda.setActivo(solicitud.activo() == null || solicitud.activo());
    }
}
