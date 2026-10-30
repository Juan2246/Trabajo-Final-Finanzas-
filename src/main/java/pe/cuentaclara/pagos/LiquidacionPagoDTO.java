package pe.cuentaclara.pagos;

import com.fasterxml.jackson.annotation.JsonProperty;

import pe.cuentaclara.creditos.CuotaSalidaDTO;

import java.math.BigDecimal;
import java.util.List;

public record LiquidacionPagoDTO(
        Long id,
        @JsonProperty("dues") List<CuotaSalidaDTO> cuotas,
        @JsonProperty("base_amount") BigDecimal importeBase,
        @JsonProperty("late_amount") BigDecimal importeMora,
        @JsonProperty("amount") BigDecimal importe,
        @JsonProperty("currency") String moneda) {}
