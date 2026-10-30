package pe.cuentaclara.acceso;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

public record UsuarioSalidaDTO(
        Long id,
        String username,
        @JsonProperty("name") String nombre,
        String role,
        @JsonProperty("store_id") Long tiendaId,
        @JsonProperty("customer_id") Long clienteId) {
    public static UsuarioSalidaDTO desde(UsuarioEntity row) {
        return new UsuarioSalidaDTO(
                row.getId(),
                row.getUsername(),
                row.getNombre(),
                row.getRole(),
                row.getTienda() == null ? null : row.getTienda().getId(),
                row.getCliente() == null ? null : row.getCliente().getId());
    }
}
