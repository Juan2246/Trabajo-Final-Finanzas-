package pe.cuentaclara.audit;

import jakarta.validation.constraints.*;

import java.time.LocalDateTime;

public record AuditResponse(
        Long id,
        Long userId,
        Long storeId,
        String action,
        Long entityId,
        String detail,
        LocalDateTime createdAt,
        String userName) {
    public static AuditResponse from(AuditEvent row) {
        return new AuditResponse(
                row.getId(),
                row.getUser() == null ? null : row.getUser().getId(),
                row.getStoreId(),
                row.getAction(),
                row.getEntityId(),
                row.getDetail(),
                row.getCreatedAt(),
                row.getUser() == null ? null : row.getUser().getName());
    }
}
