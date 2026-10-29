package pe.cuentaclara.credit;

import org.springframework.stereotype.Service;

import pe.cuentaclara.finance.FinancialEngine;

import java.math.BigDecimal;

@Service
public class CreditBalanceService {
    private final InstallmentRepository installments;

    public CreditBalanceService(InstallmentRepository installments) {
        this.installments = installments;
    }

    public BigDecimal outstanding(Long customerId) {
        return FinancialEngine.money(
                installments.findAllBySaleCustomerIdAndPaymentIsNull(customerId).stream()
                        .map(Installment::getPrincipal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
