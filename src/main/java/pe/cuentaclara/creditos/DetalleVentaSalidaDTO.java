package pe.cuentaclara.creditos;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record DetalleVentaSalidaDTO(
        Long id,
        @JsonProperty("sale_id") Long ventaId,
        @JsonProperty("product_id") Long productoId,
        @JsonProperty("name") String nombre,
        @JsonProperty("quantity") BigDecimal cantidad,
        @JsonProperty("unit_price") BigDecimal precioUnitario,
        BigDecimal subtotal) {
    public static DetalleVentaSalidaDTO desde(DetalleVenta row) {
        return new DetalleVentaSalidaDTO(
                row.getId(),
                row.getVenta() == null ? null : row.getVenta().getId(),
                row.getProducto() == null ? null : row.getProducto().getId(),
                row.getNombre(),
                row.getCantidad(),
                row.getPrecioUnitario(),
                row.getSubtotal());
    }
}
