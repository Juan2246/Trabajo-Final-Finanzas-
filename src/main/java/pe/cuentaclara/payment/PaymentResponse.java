package pe.cuentaclara.payment;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long storeId,
        Long customerId,
        LocalDateTime paidAt,
        BigDecimal baseAmount,
        BigDecimal lateAmount,
        BigDecimal amount,
        String currency,
        String customerName) {
    public static PaymentResponse from(Payment row) {
        return new PaymentResponse(
                row.getId(),
                row.getStore() == null ? null : row.getStore().getId(),
                row.getCustomer() == null ? null : row.getCustomer().getId(),
                row.getPaidAt(),
                row.getBaseAmount(),
                row.getLateAmount(),
                row.getAmount(),
                row.getCurrency(),
                row.getCustomer() == null ? null : row.getCustomer().getName());
    }
}
