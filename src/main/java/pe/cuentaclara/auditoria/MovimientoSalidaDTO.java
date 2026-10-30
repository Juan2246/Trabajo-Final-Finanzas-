package pe.cuentaclara.auditoria;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record MovimientoSalidaDTO(
        Long id,
        @JsonProperty("user_id") Long usuarioId,
        @JsonProperty("store_id") Long tiendaId,
        @JsonProperty("action") String accion,
        @JsonProperty("entity_id") Long entidadId,
        @JsonProperty("detail") String detalle,
        @JsonProperty("created_at") LocalDateTime fechaCreacion,
        @JsonProperty("user_name") String nombreUsuario) {
    public static MovimientoSalidaDTO desde(Movimiento row) {
        return new MovimientoSalidaDTO(
                row.getId(),
                row.getUsuario() == null ? null : row.getUsuario().getId(),
                row.getTiendaId(),
                row.getAccion(),
                row.getEntidadId(),
                row.getDetalle(),
                row.getFechaCreacion(),
                row.getUsuario() == null ? null : row.getUsuario().getNombre());
    }
}
