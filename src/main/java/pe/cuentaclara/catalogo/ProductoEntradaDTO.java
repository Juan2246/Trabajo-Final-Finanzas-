package pe.cuentaclara.catalogo;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductoEntradaDTO(
        @JsonProperty("name") @NotBlank @Size(max = 120) String nombre,
        @JsonProperty("description") @Size(max = 500) String descripcion,
        @JsonProperty("brand") @Size(max = 100) String marca,
        @JsonProperty("supplier") @Size(max = 120) String proveedor,
        @JsonProperty("unit") @NotBlank @Size(max = 30) String unidad,
        @JsonProperty("image") @Size(max = 1000) String imagen,
        @JsonProperty("cash_price")
                @NotNull
                @DecimalMin("0.01")
                @DecimalMax("10000000")
                @Digits(integer = 8, fraction = 2)
                BigDecimal precioContado,
        @JsonProperty("credit_price")
                @NotNull
                @DecimalMin("0.01")
                @DecimalMax("10000000")
                @Digits(integer = 8, fraction = 2)
                BigDecimal precioCredito,
        @JsonProperty("currency") @NotNull @Pattern(regexp = "PEN|USD") String moneda,
        @JsonProperty("installment_allowed") Boolean permiteCuotas,
        @JsonProperty("active") Boolean activo) {}
