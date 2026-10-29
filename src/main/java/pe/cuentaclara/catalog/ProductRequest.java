package pe.cuentaclara.catalog;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @Size(max = 100) String brand,
        @Size(max = 120) String supplier,
        @NotBlank @Size(max = 30) String unit,
        @Size(max = 1000) String image,
        @NotNull @DecimalMin("0.01") @DecimalMax("10000000") @Digits(integer = 8, fraction = 2)
                BigDecimal cashPrice,
        @NotNull @DecimalMin("0.01") @DecimalMax("10000000") @Digits(integer = 8, fraction = 2)
                BigDecimal creditPrice,
        @NotNull @Pattern(regexp = "PEN|USD") String currency,
        Boolean installmentAllowed,
        Boolean active) {}
