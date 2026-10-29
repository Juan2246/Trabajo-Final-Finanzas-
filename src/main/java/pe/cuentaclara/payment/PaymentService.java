package pe.cuentaclara.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.audit.AuditService;
import pe.cuentaclara.auth.CurrentUser;
import pe.cuentaclara.credit.CreditQueryService;
import pe.cuentaclara.credit.DueResponse;
import pe.cuentaclara.credit.InstallmentRepository;
import pe.cuentaclara.finance.FinancialEngine;
import pe.cuentaclara.shared.BusinessException;
import pe.cuentaclara.shared.BusinessTime;
import pe.cuentaclara.store.StoreAccessService;

import java.math.BigDecimal;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final PaymentLineRepository lines;
    private final InstallmentRepository installments;
    private final CreditQueryService query;
    private final StoreAccessService access;
    private final BusinessTime time;
    private final AuditService audit;

    public PaymentService(
            PaymentRepository payments,
            PaymentLineRepository lines,
            InstallmentRepository installments,
            CreditQueryService query,
            StoreAccessService access,
            BusinessTime time,
            AuditService audit) {
        this.payments = payments;
        this.lines = lines;
        this.installments = installments;
        this.query = query;
        this.access = access;
        this.time = time;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PaymentQuote preview(CurrentUser actor, Long selectedStore, Long customerId) {
        var store = access.resolve(actor, selectedStore, true);
        return calculate(store.getId(), customerId);
    }

    public PaymentQuote calculate(Long storeId, Long customerId) {
        var customer = access.customer(customerId, storeId);
        var pending =
                query.dues(storeId, customerId, time.today()).stream()
                        .filter(due -> due.paymentId() == null)
                        .toList();
        var dues = pending.stream().filter(due -> !due.dueDate().isAfter(time.today())).toList();
        if (dues.isEmpty() && !pending.isEmpty()) {
            var next =
                    pending.stream()
                            .map(DueResponse::dueDate)
                            .min(java.time.LocalDate::compareTo)
                            .orElseThrow();
            dues = pending.stream().filter(due -> due.dueDate().equals(next)).toList();
        }
        var base =
                FinancialEngine.money(
                        dues.stream()
                                .map(DueResponse::amount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add));
        var late =
                FinancialEngine.money(
                        dues.stream()
                                .map(DueResponse::lateAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return new PaymentQuote(
                null,
                dues,
                base,
                late,
                FinancialEngine.money(base.add(late)),
                customer.getCurrency());
    }

    @Transactional
    public PaymentQuote create(CurrentUser actor, Long selectedStore, PaymentRequest request) {
        var store = access.resolve(actor, selectedStore, true);
        var customer = access.customer(request.customerId(), store.getId());
        var quote = calculate(store.getId(), customer.getId());
        if (quote.dues().isEmpty())
            throw new BusinessException("El cliente no tiene obligaciones pendientes.");
        if (request.amount() == null || request.amount().compareTo(quote.amount()) != 0)
            throw new BusinessException(
                    "El pago debe ser completo: "
                            + quote.amount()
                            + " "
                            + quote.currency()
                            + ". Actualiza la liquidación.");
        Payment payment = new Payment();
        payment.setStore(store);
        payment.setCustomer(customer);
        payment.setPaidAt(time.now());
        payment.setBaseAmount(quote.baseAmount());
        payment.setLateAmount(quote.lateAmount());
        payment.setAmount(quote.amount());
        payment.setCurrency(quote.currency());
        payments.saveAndFlush(payment);
        for (DueResponse due : quote.dues()) {
            var obligation = installments.findById(due.id()).orElseThrow();
            if (obligation.getPayment() != null)
                throw new BusinessException("La cuota ya fue cancelada.", 409);
            PaymentLine line = new PaymentLine();
            line.setPayment(payment);
            line.setDue(obligation);
            line.setLateDays(due.lateDays());
            line.setLateAmount(due.lateAmount());
            lines.save(line);
            obligation.setPayment(payment);
        }
        audit.record(
                actor.id(),
                store.getId(),
                "Pago registrado",
                payment.getId(),
                quote.amount() + " " + quote.currency());
        return new PaymentQuote(
                payment.getId(),
                quote.dues(),
                quote.baseAmount(),
                quote.lateAmount(),
                quote.amount(),
                quote.currency());
    }
}
