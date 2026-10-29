package pe.cuentaclara.auth;

import jakarta.validation.constraints.*;

public record UserResponse(
        Long id, String username, String name, String role, Long storeId, Long customerId) {
    public static UserResponse from(UserAccount row) {
        return new UserResponse(
                row.getId(),
                row.getUsername(),
                row.getName(),
                row.getRole(),
                row.getStore() == null ? null : row.getStore().getId(),
                row.getCustomer() == null ? null : row.getCustomer().getId());
    }
}
