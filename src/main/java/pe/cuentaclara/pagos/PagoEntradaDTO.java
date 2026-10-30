package pe.cuentaclara.pagos;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PagoEntradaDTO(
        @JsonProperty("customer_id") @NotNull @Positive Long clienteId,
        @JsonProperty("amount") @DecimalMin("0.01") @Digits(integer = 26, fraction = 2)
                BigDecimal importe) {}
