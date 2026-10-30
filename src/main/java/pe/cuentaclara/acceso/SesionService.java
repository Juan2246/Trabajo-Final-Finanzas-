package pe.cuentaclara.acceso;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.comun.RelojNegocioComponent;

import java.util.Optional;

@Service
public class SesionService {
    private final pe.cuentaclara.auditoria.AuditoriaService auditoria;
    private final SesionRepository sesiones;
    private final RelojNegocioComponent reloj;

    public SesionService(
            SesionRepository sesiones,
            RelojNegocioComponent reloj,
            pe.cuentaclara.auditoria.AuditoriaService auditoria) {
        this.auditoria = auditoria;
        this.sesiones = sesiones;
        this.reloj = reloj;
    }

    @Transactional
    public UsuarioActualPrincipal abrir(UsuarioEntity usuario) {
        long now = reloj.segundosEpoch();
        sesiones.deleteByVenceEnLessThan(now);
        SesionEntity sesion = new SesionEntity();
        sesion.setToken(TokensAleatoriosUtil.token(48));
        sesion.setCsrf(TokensAleatoriosUtil.token(32));
        sesion.setUsuario(usuario);
        sesion.setVenceEn(now + 28800);
        sesiones.save(sesion);
        Long tiendaId = usuario.getTienda() == null ? null : usuario.getTienda().getId();
        auditoria.registrar(usuario.getId(), tiendaId, "Inicio de sesión", null, "");
        return new UsuarioActualPrincipal(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombre(),
                usuario.getRole(),
                tiendaId,
                usuario.getCliente() == null ? null : usuario.getCliente().getId(),
                sesion.getToken(),
                sesion.getCsrf());
    }

    @Transactional(readOnly = true)
    public Optional<UsuarioActualPrincipal> buscarSesion(String token) {
        if (token == null || token.length() > 128) return Optional.empty();
        return sesiones.findById(token)
                .filter(sesion -> sesion.getVenceEn() > reloj.segundosEpoch())
                .filter(sesion -> Boolean.TRUE.equals(sesion.getUsuario().getActivo()))
                .filter(
                        sesion ->
                                sesion.getUsuario().getTienda() == null
                                        || Boolean.TRUE.equals(
                                                sesion.getUsuario().getTienda().getActivo()))
                .map(
                        sesion -> {
                            UsuarioEntity usuario = sesion.getUsuario();
                            return new UsuarioActualPrincipal(
                                    usuario.getId(),
                                    usuario.getUsername(),
                                    usuario.getNombre(),
                                    usuario.getRole(),
                                    usuario.getTienda() == null
                                            ? null
                                            : usuario.getTienda().getId(),
                                    usuario.getCliente() == null
                                            ? null
                                            : usuario.getCliente().getId(),
                                    sesion.getToken(),
                                    sesion.getCsrf());
                        });
    }
}
