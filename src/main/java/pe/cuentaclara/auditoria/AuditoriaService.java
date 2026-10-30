package pe.cuentaclara.auditoria;

import org.springframework.stereotype.Service;

import pe.cuentaclara.acceso.UsuarioRepository;
import pe.cuentaclara.comun.RelojNegocio;

@Service
public class AuditoriaService {
    private final MovimientoRepository events;
    private final UsuarioRepository usuarios;
    private final RelojNegocio reloj;

    public AuditoriaService(
            MovimientoRepository events, UsuarioRepository usuarios, RelojNegocio reloj) {
        this.events = events;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    public void registrar(
            Long usuarioId, Long tiendaId, String accion, Long entidadId, String detalle) {
        Movimiento movimiento = new Movimiento();
        movimiento.setUsuario(usuarioId == null ? null : usuarios.getReferenceById(usuarioId));
        movimiento.setTiendaId(tiendaId);
        movimiento.setAccion(accion);
        movimiento.setEntidadId(entidadId);
        movimiento.setDetalle(detalle == null ? "" : detalle);
        movimiento.setFechaCreacion(reloj.ahora());
        events.save(movimiento);
    }
}
