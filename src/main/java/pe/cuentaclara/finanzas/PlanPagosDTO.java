package pe.cuentaclara.finanzas;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PlanPagosDTO(
        @JsonProperty("cycle") LocalDate ciclo,
        @JsonProperty("grace_days") int diasGracia,
        @JsonProperty("grace_interest") BigDecimal interesGracia,
        @JsonProperty("financed") BigDecimal capitalFinanciado,
        @JsonProperty("dues") List<CuotaCalculadaDTO> cuotas) {}
