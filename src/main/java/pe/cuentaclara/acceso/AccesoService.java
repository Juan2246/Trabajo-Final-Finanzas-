package pe.cuentaclara.acceso;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.auditoria.AuditoriaService;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.comun.RelojNegocioComponent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

@Service
public class AccesoService {
    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final VerificacionAccesoRepository verificaciones;
    private final IntentoAccesoRepository intentos;
    private final ContrasenaService contrasenas;
    private final RelojNegocioComponent reloj;
    private final AuditoriaService auditoria;

    public AccesoService(
            UsuarioRepository usuarios,
            SesionRepository sesiones,
            VerificacionAccesoRepository verificaciones,
            IntentoAccesoRepository intentos,
            ContrasenaService contrasenas,
            RelojNegocioComponent reloj,
            AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.verificaciones = verificaciones;
        this.intentos = intentos;
        this.contrasenas = contrasenas;
        this.reloj = reloj;
        this.auditoria = auditoria;
    }

    @Transactional
    public Map<String, String> verificacion() {
        verificaciones.deleteByVenceEnLessThan(reloj.segundosEpoch());
        int a = TokensAleatoriosUtil.digito(), b = TokensAleatoriosUtil.digito();
        VerificacionAccesoEntity verificacion = new VerificacionAccesoEntity();
        verificacion.setId(TokensAleatoriosUtil.token(24));
        verificacion.setRespuesta(Integer.toString(a + b));
        verificacion.setVenceEn(reloj.segundosEpoch() + 300);
        verificaciones.save(verificacion);
        return Map.of("id", verificacion.getId(), "question", "¿Cuánto es " + a + " + " + b + "?");
    }

    @Transactional(noRollbackFor = AccesoFallidoException.class)
    public UsuarioActualPrincipal login(AccesoEntradaDTO solicitud, String ip) {
        long now = reloj.segundosEpoch();
        String username = solicitud.username().strip();
        List<String> keys =
                List.of("user:" + username.toLowerCase(java.util.Locale.ROOT), "ip:" + ip);
        for (String key : keys) {
            if (intentos.findById(key).map(row -> row.getBloqueadoHasta() > now).orElse(false))
                throw new ReglaNegocioException("Demasiados intentos. Espera 15 minutos.", 429);
        }
        VerificacionAccesoEntity verificacion =
                verificaciones.findById(solicitud.verificacionId()).orElse(null);
        if (verificacion != null) verificaciones.delete(verificacion);
        UsuarioEntity usuario = usuarios.findByUsernameIgnoreCase(username).orElse(null);
        boolean contrasenaValida =
                contrasenas.verificar(
                        solicitud.password(), usuario == null ? "0:0" : usuario.getPassword());
        boolean verificacionValida =
                verificacion != null
                        && verificacion.getVenceEn() >= now
                        && MessageDigest.isEqual(
                                verificacion.getRespuesta().getBytes(StandardCharsets.UTF_8),
                                solicitud.respuesta().strip().getBytes(StandardCharsets.UTF_8));
        boolean cuentaValida =
                usuario != null
                        && Boolean.TRUE.equals(usuario.getActivo())
                        && (usuario.getTienda() == null
                                || Boolean.TRUE.equals(usuario.getTienda().getActivo()));
        if (!contrasenaValida || !verificacionValida || !cuentaValida) {
            for (String key : keys) {
                IntentoAccesoEntity intento =
                        intentos.findById(key).orElseGet(IntentoAccesoEntity::new);
                int fallos =
                        intento.getActualizadoEn() != null && intento.getActualizadoEn() > now - 900
                                ? intento.getFallos() + 1
                                : 1;
                intento.setKey(key);
                intento.setFallos(fallos);
                intento.setActualizadoEn(now);
                intento.setBloqueadoHasta(
                        fallos >= (key.startsWith("ip:") ? 20 : 5) ? now + 900 : 0L);
                intentos.save(intento);
            }
            throw new AccesoFallidoException();
        }
        intentos.findById(keys.getFirst()).ifPresent(intentos::delete);
        if (contrasenas.necesitaActualizarHash(usuario.getPassword()))
            usuario.setPassword(contrasenas.generarHash(solicitud.password()));
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

    @Transactional
    public void logout(UsuarioActualPrincipal usuario) {
        auditoria.registrar(usuario.id(), usuario.tiendaId(), "Cierre de sesión", null, "");
        sesiones.deleteById(usuario.sessionToken());
    }

    @Transactional
    public void cambiarContrasena(
            UsuarioActualPrincipal usuarioActual, ContrasenaEntradaDTO solicitud) {
        UsuarioEntity usuario = usuarios.findById(usuarioActual.id()).orElseThrow();
        if (!contrasenas.verificar(solicitud.currentPassword(), usuario.getPassword()))
            throw new ReglaNegocioException("La contraseña actual no coincide.");
        usuario.setPassword(contrasenas.generarHash(solicitud.password()));
        sesiones.findAllByUsuarioId(usuario.getId()).stream()
                .filter(sesion -> !sesion.getToken().equals(usuarioActual.sessionToken()))
                .forEach(sesiones::delete);
        auditoria.registrar(
                usuario.getId(), usuarioActual.tiendaId(), "Contraseña actualizada", null, "");
    }
}
