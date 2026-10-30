package pe.cuentaclara.acceso;

import jakarta.validation.constraints.*;

public record ContrasenaEntradaDTO(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 12, max = 128) String password) {}
