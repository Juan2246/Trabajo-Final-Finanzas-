package pe.cuentaclara.customer;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CustomerRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 30) String document,
        @NotBlank @Email @Size(max = 160) String email,
        @Size(max = 30) String phone,
        @NotNull @Pattern(regexp = "PEN|USD") String currency,
        @NotNull @DecimalMin("0.01") @DecimalMax("10000000") @Digits(integer = 8, fraction = 2)
                BigDecimal creditLimit,
        @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 6)
                BigDecimal rate,
        @NotNull @Pattern(regexp = "effective|nominal") String rateKind,
        @NotNull Integer ratePeriod,
        Integer frequency,
        @NotNull @DecimalMin("0") @DecimalMax("1000") @Digits(integer = 4, fraction = 6)
                BigDecimal lateRate,
        @NotNull @Pattern(regexp = "effective|nominal") String lateKind,
        @NotNull Integer latePeriod,
        Integer lateFrequency,
        @NotNull @Min(1) @Max(60) Integer maxMonths,
        @NotNull @Min(1) @Max(31) Integer cutoffDay,
        @Min(0) @Max(23) Integer cutoffHour,
        @NotNull @Min(1) @Max(31) Integer payDay,
        Boolean consent,
        String username,
        String password,
        Boolean active) {}
