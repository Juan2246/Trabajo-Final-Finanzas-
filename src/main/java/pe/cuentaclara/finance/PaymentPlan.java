package pe.cuentaclara.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PaymentPlan(
        LocalDate cycle,
        int graceDays,
        BigDecimal graceInterest,
        BigDecimal financed,
        List<PlannedInstallment> dues) {}
