package pe.cuentaclara.tiendas;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

public record TiendaSalidaDTO(
        Long id,
        @JsonProperty("name") String nombre,
        @JsonProperty("business") String rubro,
        @JsonProperty("address") String direccion,
        @JsonProperty("active") Boolean activo) {
    public static TiendaSalidaDTO desde(Tienda row) {
        return new TiendaSalidaDTO(
                row.getId(), row.getNombre(), row.getRubro(), row.getDireccion(), row.getActivo());
    }
}
