package pe.cuentaclara.store;

import jakarta.validation.constraints.*;

public record StoreRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 100) String business,
        @Size(max = 240) String address,
        @Size(max = 120) String adminName,
        String username,
        String password,
        Boolean active) {}
