package pe.cuentaclara.creditos;

import com.fasterxml.jackson.annotation.JsonProperty;

import pe.cuentaclara.finanzas.CuotaCalculadaDTO;
import pe.cuentaclara.finanzas.PlanPagosDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SimulacionVentaDTO(
        Long id,
        @JsonProperty("cycle") LocalDate ciclo,
        @JsonProperty("grace_days") int diasGracia,
        @JsonProperty("grace_interest") BigDecimal interesGracia,
        @JsonProperty("financed") BigDecimal capitalFinanciado,
        @JsonProperty("dues") List<CuotaCalculadaDTO> cuotas,
        @JsonProperty("amount") BigDecimal importe,
        @JsonProperty("currency") String moneda,
        @JsonProperty("available") BigDecimal disponible,
        @JsonProperty("items") List<DetalleVentaSalidaDTO> detalles,
        @JsonProperty("purchased_at") LocalDateTime fechaCompra,
        @JsonProperty("customer_id") Long clienteId,
        @JsonProperty("mode") String modalidad,
        @JsonProperty("months") int meses) {
    public static SimulacionVentaDTO desde(
            Long id,
            PlanPagosDTO planPagos,
            BigDecimal importe,
            String moneda,
            BigDecimal disponible,
            List<DetalleVentaSalidaDTO> detalles,
            VentaEntradaDTO solicitud,
            int meses) {
        return new SimulacionVentaDTO(
                id,
                planPagos.ciclo(),
                planPagos.diasGracia(),
                planPagos.interesGracia(),
                planPagos.capitalFinanciado(),
                planPagos.cuotas(),
                importe,
                moneda,
                disponible,
                detalles,
                solicitud.fechaCompra(),
                solicitud.clienteId(),
                solicitud.modalidad(),
                meses);
    }
}
