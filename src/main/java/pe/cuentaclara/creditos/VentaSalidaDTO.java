package pe.cuentaclara.creditos;

import com.fasterxml.jackson.annotation.JsonProperty;

import pe.cuentaclara.finanzas.CondicionesCredito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record VentaSalidaDTO(
        Long id,
        @JsonProperty("store_id") Long tiendaId,
        @JsonProperty("customer_id") Long clienteId,
        @JsonProperty("customer_name") String nombreCliente,
        @JsonProperty("purchased_at") LocalDateTime fechaCompra,
        @JsonProperty("mode") String modalidad,
        @JsonProperty("months") int meses,
        @JsonProperty("amount") BigDecimal importe,
        @JsonProperty("financed") BigDecimal capitalFinanciado,
        @JsonProperty("grace_days") int diasGracia,
        @JsonProperty("grace_interest") BigDecimal interesGracia,
        @JsonProperty("currency") String moneda,
        @JsonProperty("cycle") LocalDate ciclo,
        @JsonProperty("created_at") LocalDateTime fechaCreacion,
        @JsonProperty("terms") CondicionesCredito condiciones,
        @JsonProperty("items") List<DetalleVentaSalidaDTO> detalles) {}
