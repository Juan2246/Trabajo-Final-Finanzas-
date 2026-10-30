package pe.cuentaclara.finanzas;

import ch.obermuhlner.math.big.BigDecimalMath;

import org.springframework.stereotype.Component;

import pe.cuentaclara.comun.ReglaNegocioException;

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
public class MotorFinanciero {
    private static final MathContext PRECISION = new MathContext(36, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final Set<Integer> FRECUENCIAS = Set.of(1, 2, 4, 6, 12, 24, 360);

    public record FechasCiclo(LocalDate ciclo, LocalDate pago) {}

    public record MoraCalculada(BigDecimal importe, int dias) {}

    public static BigDecimal redondearImporte(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    public int diasComerciales(LocalDate fechaInicio, LocalDate fechaFin) {
        validar(
                !fechaFin.isBefore(fechaInicio),
                "La fecha final no puede ser anterior a la inicial.");
        return 360 * (fechaFin.getYear() - fechaInicio.getYear())
                + 30 * (fechaFin.getMonthValue() - fechaInicio.getMonthValue())
                + Math.min(fechaFin.getDayOfMonth(), 30)
                - Math.min(fechaInicio.getDayOfMonth(), 30);
    }

    public LocalDate fechaDelMes(LocalDate referencia, int desplazamiento, int dia) {
        validar(dia >= 1 && dia <= 31, "El día debe estar entre 1 y 31.");
        YearMonth mes = YearMonth.from(referencia).plusMonths(desplazamiento);
        return mes.atDay(Math.min(dia, mes.lengthOfMonth()));
    }

    public BigDecimal factorAcumulacion(
            BigDecimal tasa, String tipoTasa, int base, int dias, int frecuencia) {
        validar(
                tasa != null && tasa.signum() >= 0 && tasa.compareTo(new BigDecimal("1000")) <= 0,
                "La tasa debe estar entre 0 y 1000 por ciento.");
        validar(
                Set.of("effective", "nominal").contains(tipoTasa),
                "Selecciona una tasa nominal o efectiva.");
        validar(base == 30 || base == 360, "El período debe ser mensual o anual.");
        validar(dias >= 0 && dias <= 3600000, "El número de días no es válido.");
        validar(FRECUENCIAS.contains(frecuencia), "La frecuencia de capitalización no es válida.");
        BigDecimal fraccion = tasa.divide(new BigDecimal("100"), PRECISION);
        BigDecimal crecimiento;
        BigDecimal exponente;
        if (tipoTasa.equals("effective") || base == 30) {
            crecimiento = ONE.add(fraccion, PRECISION);
            exponente = BigDecimal.valueOf(dias).divide(BigDecimal.valueOf(base), PRECISION);
        } else {
            crecimiento =
                    ONE.add(fraccion.divide(BigDecimal.valueOf(frecuencia), PRECISION), PRECISION);
            exponente =
                    BigDecimal.valueOf((long) dias * frecuencia)
                            .divide(BigDecimal.valueOf(360), PRECISION);
        }
        return BigDecimalMath.pow(crecimiento, exponente, PRECISION);
    }

    public FechasCiclo fechasCiclo(
            LocalDateTime fechaCompra, int diaCorte, int horaCorte, int diaPago) {
        validar(horaCorte >= 0 && horaCorte <= 23, "La hora de corte debe estar entre 0 y 23.");
        LocalDate corte = fechaDelMes(fechaCompra.toLocalDate(), 0, diaCorte);
        int desplazamiento = fechaCompra.isAfter(corte.atTime(horaCorte, 0)) ? 1 : 0;
        LocalDate ciclo = fechaDelMes(fechaCompra.toLocalDate(), desplazamiento, diaCorte);
        return new FechasCiclo(ciclo, fechaDelMes(ciclo, diaPago >= diaCorte ? 0 : 1, diaPago));
    }

    public PlanPagos calcularPlanPagos(
            BigDecimal importeCompra,
            LocalDateTime fechaCompra,
            CondicionesCredito condiciones,
            String modalidad,
            int meses) {
        BigDecimal capital = redondearImporte(importeCompra);
        validar(capital.signum() > 0, "El importe debe ser mayor que cero.");
        validar(
                Set.of("end", "installments").contains(modalidad),
                "Selecciona una modalidad válida.");
        validar(meses >= 1 && meses <= 60, "El plazo debe estar entre 1 y 60 meses.");
        validar(
                condiciones.maximoMeses() >= 1 && condiciones.maximoMeses() <= 60,
                "El plazo máximo no es válido.");
        validar(
                !modalidad.equals("end") || meses == 1,
                "El pago al cierre tiene una sola obligación.");
        validar(
                !modalidad.equals("installments") || meses <= condiciones.maximoMeses(),
                "El plazo supera el permitido para este cliente.");
        FechasCiclo fechasCiclo =
                fechasCiclo(
                        fechaCompra,
                        condiciones.diaCorte(),
                        condiciones.horaCorte(),
                        condiciones.diaPago());
        int dias = diasComerciales(fechaCompra.toLocalDate(), fechasCiclo.pago());
        BigDecimal capitalFinanciado =
                redondearImporte(
                        capital.multiply(
                                factorAcumulacion(
                                        condiciones.tasa(),
                                        condiciones.tipoTasa(),
                                        condiciones.periodoTasa(),
                                        dias,
                                        condiciones.frecuencia()),
                                PRECISION));
        if (modalidad.equals("end")) {
            return new PlanPagos(
                    fechasCiclo.ciclo(),
                    0,
                    redondearImporte(BigDecimal.ZERO),
                    capital,
                    List.of(
                            new CuotaCalculada(
                                    1,
                                    fechasCiclo.pago(),
                                    capital,
                                    capitalFinanciado.subtract(capital),
                                    capitalFinanciado)));
        }
        BigDecimal tasaMensual =
                factorAcumulacion(
                                condiciones.tasa(),
                                condiciones.tipoTasa(),
                                condiciones.periodoTasa(),
                                30,
                                condiciones.frecuencia())
                        .subtract(ONE);
        BigDecimal pago =
                tasaMensual.signum() == 0
                        ? redondearImporte(
                                capitalFinanciado.divide(BigDecimal.valueOf(meses), PRECISION))
                        : redondearImporte(
                                capitalFinanciado
                                        .multiply(tasaMensual, PRECISION)
                                        .divide(
                                                ONE.subtract(
                                                        BigDecimalMath.pow(
                                                                ONE.add(tasaMensual),
                                                                BigDecimal.valueOf(-meses),
                                                                PRECISION)),
                                                PRECISION));
        validar(pago.signum() > 0, "El importe es demasiado pequeño para ese plazo.");
        BigDecimal saldo = capitalFinanciado;
        List<CuotaCalculada> cuotas = new ArrayList<>();
        for (int numero = 1; numero <= meses; numero++) {
            BigDecimal interes = redondearImporte(saldo.multiply(tasaMensual, PRECISION));
            BigDecimal amortizacion =
                    numero == meses ? saldo : redondearImporte(pago.subtract(interes));
            validar(
                    amortizacion.signum() >= 0 && amortizacion.compareTo(saldo) <= 0,
                    "El monto y el plazo producen cuotas no válidas.");
            validar(
                    numero == meses || amortizacion.compareTo(saldo) != 0,
                    "El importe es demasiado pequeño para ese plazo.");
            cuotas.add(
                    new CuotaCalculada(
                            numero,
                            fechaDelMes(fechasCiclo.pago(), numero, condiciones.diaPago()),
                            amortizacion,
                            interes,
                            redondearImporte(amortizacion.add(interes))));
            saldo = redondearImporte(saldo.subtract(amortizacion));
        }
        return new PlanPagos(
                fechasCiclo.ciclo(),
                dias,
                capitalFinanciado.subtract(capital),
                capitalFinanciado,
                List.copyOf(cuotas));
    }

    public MoraCalculada calcularMora(
            BigDecimal importe,
            LocalDate fechaVencimiento,
            LocalDate fechaPago,
            CondicionesCredito condiciones) {
        validar(importe.signum() >= 0, "La deuda no puede ser negativa.");
        if (!fechaPago.isAfter(fechaVencimiento))
            return new MoraCalculada(redondearImporte(BigDecimal.ZERO), 0);
        int dias = diasComerciales(fechaVencimiento, fechaPago);
        BigDecimal factorMora =
                factorAcumulacion(
                        condiciones.tasaMora(),
                        condiciones.tipoTasaMora(),
                        condiciones.periodoTasaMora(),
                        dias,
                        condiciones.frecuenciaMora());
        return new MoraCalculada(
                redondearImporte(
                        redondearImporte(importe).multiply(factorMora.subtract(ONE), PRECISION)),
                dias);
    }

    private static void validar(boolean valido, String mensaje) {
        if (!valido) throw new ReglaNegocioException(mensaje);
    }
}
