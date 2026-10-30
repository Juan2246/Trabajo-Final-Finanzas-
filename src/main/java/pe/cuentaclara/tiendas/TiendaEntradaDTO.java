package pe.cuentaclara.tiendas;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

public record TiendaEntradaDTO(
        @JsonProperty("name") @NotBlank @Size(max = 120) String nombre,
        @JsonProperty("business") @NotBlank @Size(max = 100) String rubro,
        @JsonProperty("address") @Size(max = 240) String direccion,
        @Size(max = 120) String adminName,
        String username,
        String password,
        @JsonProperty("active") Boolean activo) {}
