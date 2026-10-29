package pe.cuentaclara.credit;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SaleRequest(
        @NotNull @Positive Long customerId,
        @NotNull LocalDateTime purchasedAt,
        @NotNull @Pattern(regexp = "end|installments") String mode,
        @Min(1) @Max(60) Integer months,
        @NotEmpty @Size(max = 50) List<@Valid Line> items,
        JsonNode expectedPlan) {
    public record Line(
            @NotNull @Positive Long productId,
            @NotNull @DecimalMin("0.001") @DecimalMax("10000") @Digits(integer = 5, fraction = 3)
                    BigDecimal quantity) {}
}
