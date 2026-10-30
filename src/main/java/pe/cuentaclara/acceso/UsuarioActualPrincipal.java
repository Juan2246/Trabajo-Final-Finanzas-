package pe.cuentaclara.acceso;

import java.security.Principal;

/** Datos mínimos de la sesión; las entidades nunca salen del servicio de acceso. */
public record UsuarioActualPrincipal(
        Long id,
        String username,
        String nombre,
        String role,
        Long tiendaId,
        Long clienteId,
        String sessionToken,
        String csrf)
        implements Principal {
    @Override
    public String getName() {
        return username;
    }

    public UsuarioSalidaDTO datosPublicos() {
        return new UsuarioSalidaDTO(id, username, nombre, role, tiendaId, clienteId);
    }

    public boolean esPlataforma() {
        return "platform".equals(role);
    }

    public boolean esCliente() {
        return "customer".equals(role);
    }
}
