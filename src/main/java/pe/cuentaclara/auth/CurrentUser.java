package pe.cuentaclara.auth;

import java.security.Principal;

/** Datos mínimos de la sesión; las entidades nunca salen del servicio de acceso. */
public record CurrentUser(
        Long id,
        String username,
        String name,
        String role,
        Long storeId,
        Long customerId,
        String sessionToken,
        String csrf)
        implements Principal {
    @Override
    public String getName() {
        return username;
    }

    public UserResponse response() {
        return new UserResponse(id, username, name, role, storeId, customerId);
    }

    public boolean isPlatform() {
        return "platform".equals(role);
    }

    public boolean isCustomer() {
        return "customer".equals(role);
    }
}
