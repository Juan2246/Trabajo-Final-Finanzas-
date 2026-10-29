package pe.cuentaclara.finance;

import pe.cuentaclara.customer.Customer;

import java.math.BigDecimal;

/** Tasas y calendario que quedan guardados con cada compra. */
public record CreditTerms(
        BigDecimal rate,
        String rateKind,
        int ratePeriod,
        int frequency,
        BigDecimal lateRate,
        String lateKind,
        int latePeriod,
        int lateFrequency,
        int maxMonths,
        int cutoffDay,
        int cutoffHour,
        int payDay) {
    public static CreditTerms from(Customer customer) {
        return new CreditTerms(
                customer.getRate(),
                customer.getRateKind(),
                customer.getRatePeriod(),
                customer.getFrequency(),
                customer.getLateRate(),
                customer.getLateKind(),
                customer.getLatePeriod(),
                customer.getLateFrequency(),
                customer.getMaxMonths(),
                customer.getCutoffDay(),
                customer.getCutoffHour(),
                customer.getPayDay());
    }
}
