package pe.cuentaclara.acceso;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

public record AccesoEntradaDTO(
        @NotBlank @Size(max = 80) String username,
        @NotBlank @Size(max = 128) String password,
        @JsonProperty("challenge_id") @NotBlank String verificacionId,
        @JsonProperty("answer") @NotBlank String respuesta) {}
