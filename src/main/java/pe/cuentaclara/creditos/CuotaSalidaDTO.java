package pe.cuentaclara.creditos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import pe.cuentaclara.finanzas.CondicionesCreditoDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CuotaSalidaDTO(
        Long id,
        @JsonProperty("sale_id") Long ventaId,
        @JsonProperty("number") int numero,
        @JsonProperty("due_date") LocalDate fechaVencimiento,
        @JsonProperty("principal") BigDecimal capital,
        @JsonProperty("interest") BigDecimal interes,
        @JsonProperty("amount") BigDecimal importe,
        @JsonProperty("payment_id") Long pagoId,
        @JsonProperty("customer_id") Long clienteId,
        @JsonProperty("customer_name") String nombreCliente,
        @JsonProperty("currency") String moneda,
        @JsonProperty("purchased_at") LocalDateTime fechaCompra,
        @JsonProperty("mode") String modalidad,
        @JsonProperty("paid_at") LocalDateTime fechaPago,
        @JsonProperty("late_amount") BigDecimal importeMora,
        @JsonProperty("late_days") int diasMora,
        @JsonProperty("late_base") BigDecimal baseMora,
        @JsonProperty("late_as_of") LocalDate fechaCalculoMora,
        @JsonProperty("interest_days") int diasInteres,
        BigDecimal total,
        @JsonUnwrapped CondicionesCreditoDTO condiciones) {}
