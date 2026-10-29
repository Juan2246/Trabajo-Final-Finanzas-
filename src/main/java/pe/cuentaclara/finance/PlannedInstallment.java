package pe.cuentaclara.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PlannedInstallment(
        int number, LocalDate date, BigDecimal principal, BigDecimal interest, BigDecimal amount) {}
