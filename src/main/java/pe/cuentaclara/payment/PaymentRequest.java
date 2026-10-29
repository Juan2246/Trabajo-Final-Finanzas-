package pe.cuentaclara.payment;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotNull @Positive Long customerId,
        @DecimalMin("0.01") @Digits(integer = 26, fraction = 2) BigDecimal amount) {}
