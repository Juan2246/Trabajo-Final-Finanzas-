package pe.cuentaclara.credit;

import pe.cuentaclara.finance.CreditTerms;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SaleResponse(
        Long id,
        Long storeId,
        Long customerId,
        String customerName,
        LocalDateTime purchasedAt,
        String mode,
        int months,
        BigDecimal amount,
        BigDecimal financed,
        int graceDays,
        BigDecimal graceInterest,
        String currency,
        LocalDate cycle,
        LocalDateTime createdAt,
        CreditTerms terms,
        List<SaleItemResponse> items) {}
