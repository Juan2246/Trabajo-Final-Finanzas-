package pe.cuentaclara.acceso;

import org.springframework.stereotype.Service;

import pe.cuentaclara.clientes.Cliente;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.tiendas.Tienda;

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

    public Usuario crear(
            String username,
            String nombre,
            String password,
            String role,
            Tienda tienda,
            Cliente cliente) {
        if (username == null || !username.matches("[A-Za-z0-9_.@-]{3,80}"))
            throw new ReglaNegocioException("Usuario: 3–80 letras, números, puntos, @ o guiones.");
        if (nombre == null || nombre.isBlank() || nombre.length() > 120)
            throw new ReglaNegocioException("Revisa el nombre del usuario.");
        if (usuarios.findByUsernameIgnoreCase(username).isPresent())
            throw new ReglaNegocioException("El usuario ya existe.", 409);
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setNombre(nombre.strip());
        usuario.setPassword(contrasenas.generarHash(password));
        usuario.setRole(role);
        usuario.setTienda(tienda);
        usuario.setCliente(cliente);
        usuario.setActivo(true);
        return usuarios.saveAndFlush(usuario);
    }

    public void actualizarCliente(Cliente cliente, String nuevaContrasena) {
        for (Usuario usuario : usuarios.findByClienteId(cliente.getId())) {
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
