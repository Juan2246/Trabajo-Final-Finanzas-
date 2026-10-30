package pe.cuentaclara.creditos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record VentaEntradaDTO(
        @JsonProperty("customer_id") @NotNull @Positive Long clienteId,
        @JsonProperty("purchased_at") @NotNull LocalDateTime fechaCompra,
        @JsonProperty("mode") @NotNull @Pattern(regexp = "end|installments") String modalidad,
        @JsonProperty("months") @Min(1) @Max(60) Integer meses,
        @JsonProperty("items") @NotEmpty @Size(max = 50) List<@Valid LineaVentaDTO> detalles,
        @JsonProperty("expected_plan") JsonNode planAceptado) {
    public record LineaVentaDTO(
            @JsonProperty("product_id") @NotNull @Positive Long productoId,
            @JsonProperty("quantity")
                    @NotNull
                    @DecimalMin("0.001")
                    @DecimalMax("10000")
                    @Digits(integer = 5, fraction = 3)
                    BigDecimal cantidad) {}
}
