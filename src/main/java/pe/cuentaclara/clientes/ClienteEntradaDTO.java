package pe.cuentaclara.clientes;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ClienteEntradaDTO(
        @JsonProperty("name") @NotBlank @Size(max = 120) String nombre,
        @JsonProperty("document") @NotBlank @Size(max = 30) String documento,
        @NotBlank @Email @Size(max = 160) String email,
        @JsonProperty("phone") @Size(max = 30) String telefono,
        @JsonProperty("currency") @NotNull @Pattern(regexp = "PEN|USD") String moneda,
        @JsonProperty("credit_limit")
                @NotNull
                @DecimalMin("0.01")
                @DecimalMax("10000000")
                @Digits(integer = 8, fraction = 2)
                BigDecimal limiteCredito,
        @JsonProperty("rate")
                @NotNull
                @DecimalMin("0")
                @DecimalMax("1000")
                @Digits(integer = 4, fraction = 6)
                BigDecimal tasa,
        @JsonProperty("rate_kind") @NotNull @Pattern(regexp = "effective|nominal") String tipoTasa,
        @JsonProperty("rate_period") @NotNull Integer periodoTasa,
        @JsonProperty("frequency") Integer frecuencia,
        @JsonProperty("late_rate")
                @NotNull
                @DecimalMin("0")
                @DecimalMax("1000")
                @Digits(integer = 4, fraction = 6)
                BigDecimal tasaMora,
        @JsonProperty("late_kind") @NotNull @Pattern(regexp = "effective|nominal")
                String tipoTasaMora,
        @JsonProperty("late_period") @NotNull Integer periodoTasaMora,
        @JsonProperty("late_frequency") Integer frecuenciaMora,
        @JsonProperty("max_months") @NotNull @Min(1) @Max(60) Integer maximoMeses,
        @JsonProperty("cutoff_day") @NotNull @Min(1) @Max(31) Integer diaCorte,
        @JsonProperty("cutoff_hour") @Min(0) @Max(23) Integer horaCorte,
        @JsonProperty("pay_day") @NotNull @Min(1) @Max(31) Integer diaPago,
        @JsonProperty("consent") Boolean consentimiento,
        String username,
        String password,
        @JsonProperty("active") Boolean activo) {}
