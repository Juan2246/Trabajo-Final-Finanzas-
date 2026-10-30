package pe.cuentaclara.acceso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmacionSeguridadDTO(
        @NotBlank @Size(max = 128) String password, @Size(max = 40) String codigo) {}
