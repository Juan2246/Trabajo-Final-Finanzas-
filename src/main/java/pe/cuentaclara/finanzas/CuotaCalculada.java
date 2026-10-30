package pe.cuentaclara.finanzas;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CuotaCalculada(
        @JsonProperty("number") int numero,
        @JsonProperty("date") LocalDate fecha,
        @JsonProperty("principal") BigDecimal capital,
        @JsonProperty("interest") BigDecimal interes,
        @JsonProperty("amount") BigDecimal importe) {}
