package pe.cuentaclara.finance;

import ch.obermuhlner.math.big.BigDecimalMath;

import org.springframework.stereotype.Component;

import pe.cuentaclara.shared.BusinessException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Método francés vencido y acumulación en base comercial 30/360. */
@Component
public class FinancialEngine {
    private static final MathContext PRECISION = new MathContext(36, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final Set<Integer> FREQUENCIES = Set.of(1, 2, 4, 6, 12, 24, 360);

    public record Anchor(LocalDate cycle, LocalDate payment) {}

    public record LateCharge(BigDecimal amount, int days) {}

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public int commercialDays(LocalDate start, LocalDate end) {
        require(!end.isBefore(start), "La fecha final no puede ser anterior a la inicial.");
        return 360 * (end.getYear() - start.getYear())
                + 30 * (end.getMonthValue() - start.getMonthValue())
                + Math.min(end.getDayOfMonth(), 30)
                - Math.min(start.getDayOfMonth(), 30);
    }

    public LocalDate monthDate(LocalDate reference, int offset, int day) {
        require(day >= 1 && day <= 31, "El día debe estar entre 1 y 31.");
        YearMonth month = YearMonth.from(reference).plusMonths(offset);
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    public BigDecimal factor(BigDecimal rate, String kind, int base, int days, int frequency) {
        require(
                rate != null && rate.signum() >= 0 && rate.compareTo(new BigDecimal("1000")) <= 0,
                "La tasa debe estar entre 0 y 1000 por ciento.");
        require(
                Set.of("effective", "nominal").contains(kind),
                "Selecciona una tasa nominal o efectiva.");
        require(base == 30 || base == 360, "El período debe ser mensual o anual.");
        require(days >= 0 && days <= 3600000, "El número de días no es válido.");
        require(FREQUENCIES.contains(frequency), "La frecuencia de capitalización no es válida.");
        BigDecimal fraction = rate.divide(new BigDecimal("100"), PRECISION);
        BigDecimal growth;
        BigDecimal exponent;
        if (kind.equals("effective") || base == 30) {
            growth = ONE.add(fraction, PRECISION);
            exponent = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(base), PRECISION);
        } else {
            growth = ONE.add(fraction.divide(BigDecimal.valueOf(frequency), PRECISION), PRECISION);
            exponent =
                    BigDecimal.valueOf((long) days * frequency)
                            .divide(BigDecimal.valueOf(360), PRECISION);
        }
        return BigDecimalMath.pow(growth, exponent, PRECISION);
    }

    public Anchor anchor(LocalDateTime purchased, int cutoffDay, int cutoffHour, int payDay) {
        require(cutoffHour >= 0 && cutoffHour <= 23, "La hora de corte debe estar entre 0 y 23.");
        LocalDate cutoff = monthDate(purchased.toLocalDate(), 0, cutoffDay);
        int offset = purchased.isAfter(cutoff.atTime(cutoffHour, 0)) ? 1 : 0;
        LocalDate cycle = monthDate(purchased.toLocalDate(), offset, cutoffDay);
        return new Anchor(cycle, monthDate(cycle, payDay >= cutoffDay ? 0 : 1, payDay));
    }

    public PaymentPlan plan(
            BigDecimal input, LocalDateTime purchased, CreditTerms terms, String mode, int months) {
        BigDecimal principal = money(input);
        require(principal.signum() > 0, "El importe debe ser mayor que cero.");
        require(Set.of("end", "installments").contains(mode), "Selecciona una modalidad válida.");
        require(months >= 1 && months <= 60, "El plazo debe estar entre 1 y 60 meses.");
        require(terms.maxMonths() >= 1 && terms.maxMonths() <= 60, "El plazo máximo no es válido.");
        require(!mode.equals("end") || months == 1, "El pago al cierre tiene una sola obligación.");
        require(
                !mode.equals("installments") || months <= terms.maxMonths(),
                "El plazo supera el permitido para este cliente.");
        Anchor anchor = anchor(purchased, terms.cutoffDay(), terms.cutoffHour(), terms.payDay());
        int days = commercialDays(purchased.toLocalDate(), anchor.payment());
        BigDecimal financed =
                money(
                        principal.multiply(
                                factor(
                                        terms.rate(),
                                        terms.rateKind(),
                                        terms.ratePeriod(),
                                        days,
                                        terms.frequency()),
                                PRECISION));
        if (mode.equals("end")) {
            return new PaymentPlan(
                    anchor.cycle(),
                    0,
                    money(BigDecimal.ZERO),
                    principal,
                    List.of(
                            new PlannedInstallment(
                                    1,
                                    anchor.payment(),
                                    principal,
                                    financed.subtract(principal),
                                    financed)));
        }
        BigDecimal monthly =
                factor(terms.rate(), terms.rateKind(), terms.ratePeriod(), 30, terms.frequency())
                        .subtract(ONE);
        BigDecimal payment =
                monthly.signum() == 0
                        ? money(financed.divide(BigDecimal.valueOf(months), PRECISION))
                        : money(
                                financed.multiply(monthly, PRECISION)
                                        .divide(
                                                ONE.subtract(
                                                        BigDecimalMath.pow(
                                                                ONE.add(monthly),
                                                                BigDecimal.valueOf(-months),
                                                                PRECISION)),
                                                PRECISION));
        require(payment.signum() > 0, "El importe es demasiado pequeño para ese plazo.");
        BigDecimal balance = financed;
        List<PlannedInstallment> dues = new ArrayList<>();
        for (int number = 1; number <= months; number++) {
            BigDecimal interest = money(balance.multiply(monthly, PRECISION));
            BigDecimal amortization =
                    number == months ? balance : money(payment.subtract(interest));
            require(
                    amortization.signum() >= 0 && amortization.compareTo(balance) <= 0,
                    "El monto y el plazo producen cuotas no válidas.");
            require(
                    number == months || amortization.compareTo(balance) != 0,
                    "El importe es demasiado pequeño para ese plazo.");
            dues.add(
                    new PlannedInstallment(
                            number,
                            monthDate(anchor.payment(), number, terms.payDay()),
                            amortization,
                            interest,
                            money(amortization.add(interest))));
            balance = money(balance.subtract(amortization));
        }
        return new PaymentPlan(
                anchor.cycle(), days, financed.subtract(principal), financed, List.copyOf(dues));
    }

    public LateCharge lateInterest(
            BigDecimal amount, LocalDate due, LocalDate paid, CreditTerms terms) {
        require(amount.signum() >= 0, "La deuda no puede ser negativa.");
        if (!paid.isAfter(due)) return new LateCharge(money(BigDecimal.ZERO), 0);
        int days = commercialDays(due, paid);
        BigDecimal accrued =
                factor(
                        terms.lateRate(),
                        terms.lateKind(),
                        terms.latePeriod(),
                        days,
                        terms.lateFrequency());
        return new LateCharge(
                money(money(amount).multiply(accrued.subtract(ONE), PRECISION)), days);
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new BusinessException(message);
    }
}
