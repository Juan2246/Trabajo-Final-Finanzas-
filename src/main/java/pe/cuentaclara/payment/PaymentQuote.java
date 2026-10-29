package pe.cuentaclara.payment;

import pe.cuentaclara.credit.DueResponse;

import java.math.BigDecimal;
import java.util.List;

public record PaymentQuote(
        Long id,
        List<DueResponse> dues,
        BigDecimal baseAmount,
        BigDecimal lateAmount,
        BigDecimal amount,
        String currency) {}
