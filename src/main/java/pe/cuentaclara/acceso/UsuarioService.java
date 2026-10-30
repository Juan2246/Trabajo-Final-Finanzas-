package pe.cuentaclara.acceso;

import org.springframework.stereotype.Service;

import pe.cuentaclara.clientes.ClienteEntity;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.tiendas.TiendaEntity;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final ContrasenaService contrasenas;

    public UsuarioService(
            UsuarioRepository usuarios, SesionRepository sesiones, ContrasenaService contrasenas) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.contrasenas = contrasenas;
    }

    public UsuarioEntity crear(
            String username,
            String nombre,
            String password,
            String role,
            TiendaEntity tienda,
            ClienteEntity cliente) {
        if (username == null || !username.matches("[A-Za-z0-9_.@-]{3,80}"))
            throw new ReglaNegocioException("Usuario: 3–80 letras, números, puntos, @ o guiones.");
        if (nombre == null || nombre.isBlank() || nombre.length() > 120)
            throw new ReglaNegocioException("Revisa el nombre del usuario.");
        if (usuarios.findByUsernameIgnoreCase(username).isPresent())
            throw new ReglaNegocioException("El usuario ya existe.", 409);
        UsuarioEntity usuario = new UsuarioEntity();
        usuario.setUsername(username);
        usuario.setNombre(nombre.strip());
        usuario.setPassword(contrasenas.generarHash(password));
        usuario.setRole(role);
        usuario.setTienda(tienda);
        usuario.setCliente(cliente);
        usuario.setActivo(true);
        return usuarios.saveAndFlush(usuario);
    }

    public void actualizarCliente(ClienteEntity cliente, String nuevaContrasena) {
        for (UsuarioEntity usuario : usuarios.findByClienteId(cliente.getId())) {
            usuario.setNombre(cliente.getNombre());
            usuario.setActivo(cliente.getActivo());
            if (!Boolean.TRUE.equals(cliente.getActivo())
                    || (nuevaContrasena != null && !nuevaContrasena.isBlank()))
                sesiones.deleteByUsuarioId(usuario.getId());
            if (nuevaContrasena != null && !nuevaContrasena.isBlank())
                usuario.setPassword(contrasenas.generarHash(nuevaContrasena));
        }
    }
}
