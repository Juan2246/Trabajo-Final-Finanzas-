package pe.cuentaclara.catalogo;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductoSalidaDTO(
        Long id,
        @JsonProperty("store_id") Long tiendaId,
        @JsonProperty("name") String nombre,
        @JsonProperty("description") String descripcion,
        @JsonProperty("brand") String marca,
        @JsonProperty("supplier") String proveedor,
        @JsonProperty("unit") String unidad,
        @JsonProperty("image") String imagen,
        @JsonProperty("cash_price") BigDecimal precioContado,
        @JsonProperty("credit_price") BigDecimal precioCredito,
        @JsonProperty("currency") String moneda,
        @JsonProperty("installment_allowed") Boolean permiteCuotas,
        @JsonProperty("active") Boolean activo) {
    public static ProductoSalidaDTO desde(ProductoEntity row) {
        return new ProductoSalidaDTO(
                row.getId(),
                row.getTienda() == null ? null : row.getTienda().getId(),
                row.getNombre(),
                row.getDescripcion(),
                row.getMarca(),
                row.getProveedor(),
                row.getUnidad(),
                row.getImagen(),
                row.getPrecioContado(),
                row.getPrecioCredito(),
                row.getMoneda(),
                row.getPermiteCuotas(),
                row.getActivo());
    }
}
