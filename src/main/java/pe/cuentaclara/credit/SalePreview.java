package pe.cuentaclara.credit;

import pe.cuentaclara.finance.PaymentPlan;
import pe.cuentaclara.finance.PlannedInstallment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SalePreview(
        Long id,
        LocalDate cycle,
        int graceDays,
        BigDecimal graceInterest,
        BigDecimal financed,
        List<PlannedInstallment> dues,
        BigDecimal amount,
        String currency,
        BigDecimal available,
        List<SaleItemResponse> items,
        LocalDateTime purchasedAt,
        Long customerId,
        String mode,
        int months) {
    public static SalePreview from(
            Long id,
            PaymentPlan plan,
            BigDecimal amount,
            String currency,
            BigDecimal available,
            List<SaleItemResponse> items,
            SaleRequest request,
            int months) {
        return new SalePreview(
                id,
                plan.cycle(),
                plan.graceDays(),
                plan.graceInterest(),
                plan.financed(),
                plan.dues(),
                amount,
                currency,
                available,
                items,
                request.purchasedAt(),
                request.customerId(),
                request.mode(),
                months);
    }
}
