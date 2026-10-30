package pe.cuentaclara.clientes;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ClienteSalidaDTO(
        Long id,
        @JsonProperty("store_id") Long tiendaId,
        @JsonProperty("name") String nombre,
        @JsonProperty("document") String documento,
        String email,
        @JsonProperty("phone") String telefono,
        @JsonProperty("currency") String moneda,
        @JsonProperty("credit_limit") BigDecimal limiteCredito,
        @JsonProperty("rate") BigDecimal tasa,
        @JsonProperty("rate_kind") String tipoTasa,
        @JsonProperty("rate_period") Integer periodoTasa,
        @JsonProperty("frequency") Integer frecuencia,
        @JsonProperty("late_rate") BigDecimal tasaMora,
        @JsonProperty("late_kind") String tipoTasaMora,
        @JsonProperty("late_period") Integer periodoTasaMora,
        @JsonProperty("late_frequency") Integer frecuenciaMora,
        @JsonProperty("max_months") Integer maximoMeses,
        @JsonProperty("cutoff_day") Integer diaCorte,
        @JsonProperty("cutoff_hour") Integer horaCorte,
        @JsonProperty("pay_day") Integer diaPago,
        @JsonProperty("consent_at") LocalDateTime fechaConsentimiento,
        @JsonProperty("consent_version") String versionConsentimiento,
        @JsonProperty("active") Boolean activo,
        @JsonProperty("outstanding") BigDecimal capitalPendiente,
        @JsonProperty("available") BigDecimal disponible,
        @JsonProperty("retention_days") Integer diasConservacion,
        @JsonProperty("erase_requested_at") LocalDateTime retiroSolicitadoEn,
        @JsonProperty("anonymized_at") LocalDateTime anonimizadoEn) {
    public static ClienteSalidaDTO desde(ClienteEntity row, BigDecimal capitalPendiente) {
        return new ClienteSalidaDTO(
                row.getId(),
                row.getTienda() == null ? null : row.getTienda().getId(),
                row.getNombre(),
                row.getDocumento(),
                row.getEmail(),
                row.getTelefono(),
                row.getMoneda(),
                row.getLimiteCredito(),
                row.getTasa(),
                row.getTipoTasa(),
                row.getPeriodoTasa(),
                row.getFrecuencia(),
                row.getTasaMora(),
                row.getTipoTasaMora(),
                row.getPeriodoTasaMora(),
                row.getFrecuenciaMora(),
                row.getMaximoMeses(),
                row.getDiaCorte(),
                row.getHoraCorte(),
                row.getDiaPago(),
                row.getFechaConsentimiento(),
                row.getVersionConsentimiento(),
                row.getActivo(),
                capitalPendiente,
                row.getLimiteCredito().subtract(capitalPendiente),
                row.getDiasConservacion(),
                row.getRetiroSolicitadoEn(),
                row.getAnonimizadoEn());
    }
}
