package pe.cuentaclara.auth;

import jakarta.validation.constraints.*;

public record PasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 12, max = 128) String password) {}
