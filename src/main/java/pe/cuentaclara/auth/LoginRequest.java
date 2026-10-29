package pe.cuentaclara.auth;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank @Size(max = 80) String username,
        @NotBlank @Size(max = 128) String password,
        @NotBlank String challengeId,
        @NotBlank String answer) {}
