package pe.cuentaclara.customer;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CustomerResponse(
        Long id,
        Long storeId,
        String name,
        String document,
        String email,
        String phone,
        String currency,
        BigDecimal creditLimit,
        BigDecimal rate,
        String rateKind,
        Integer ratePeriod,
        Integer frequency,
        BigDecimal lateRate,
        String lateKind,
        Integer latePeriod,
        Integer lateFrequency,
        Integer maxMonths,
        Integer cutoffDay,
        Integer cutoffHour,
        Integer payDay,
        LocalDateTime consentAt,
        String consentVersion,
        Boolean active,
        BigDecimal outstanding,
        BigDecimal available) {
    public static CustomerResponse from(Customer row, BigDecimal outstanding) {
        return new CustomerResponse(
                row.getId(),
                row.getStore() == null ? null : row.getStore().getId(),
                row.getName(),
                row.getDocument(),
                row.getEmail(),
                row.getPhone(),
                row.getCurrency(),
                row.getCreditLimit(),
                row.getRate(),
                row.getRateKind(),
                row.getRatePeriod(),
                row.getFrequency(),
                row.getLateRate(),
                row.getLateKind(),
                row.getLatePeriod(),
                row.getLateFrequency(),
                row.getMaxMonths(),
                row.getCutoffDay(),
                row.getCutoffHour(),
                row.getPayDay(),
                row.getConsentAt(),
                row.getConsentVersion(),
                row.getActive(),
                outstanding,
                row.getCreditLimit().subtract(outstanding));
    }
}
