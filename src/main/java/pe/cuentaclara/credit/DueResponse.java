package pe.cuentaclara.credit;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import pe.cuentaclara.finance.CreditTerms;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DueResponse(
        Long id,
        Long saleId,
        int number,
        LocalDate dueDate,
        BigDecimal principal,
        BigDecimal interest,
        BigDecimal amount,
        Long paymentId,
        Long customerId,
        String customerName,
        String currency,
        LocalDateTime purchasedAt,
        String mode,
        LocalDateTime paidAt,
        BigDecimal lateAmount,
        int lateDays,
        BigDecimal lateBase,
        LocalDate lateAsOf,
        int interestDays,
        BigDecimal total,
        @JsonUnwrapped CreditTerms terms) {}
