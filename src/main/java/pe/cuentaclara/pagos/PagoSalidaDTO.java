package pe.cuentaclara.pagos;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PagoSalidaDTO(
        Long id,
        @JsonProperty("store_id") Long tiendaId,
        @JsonProperty("customer_id") Long clienteId,
        @JsonProperty("paid_at") LocalDateTime fechaPago,
        @JsonProperty("base_amount") BigDecimal importeBase,
        @JsonProperty("late_amount") BigDecimal importeMora,
        @JsonProperty("amount") BigDecimal importe,
        @JsonProperty("currency") String moneda,
        @JsonProperty("customer_name") String nombreCliente) {
    public static PagoSalidaDTO desde(PagoEntity row) {
        return new PagoSalidaDTO(
                row.getId(),
                row.getTienda() == null ? null : row.getTienda().getId(),
                row.getCliente() == null ? null : row.getCliente().getId(),
                row.getFechaPago(),
                row.getImporteBase(),
                row.getImporteMora(),
                row.getImporte(),
                row.getMoneda(),
                row.getCliente() == null ? null : row.getCliente().getNombre());
    }
}
