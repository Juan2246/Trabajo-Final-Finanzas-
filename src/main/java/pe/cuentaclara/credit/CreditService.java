package pe.cuentaclara.credit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.audit.AuditService;
import pe.cuentaclara.auth.CurrentUser;
import pe.cuentaclara.catalog.Product;
import pe.cuentaclara.catalog.ProductService;
import pe.cuentaclara.customer.Customer;
import pe.cuentaclara.finance.CreditTerms;
import pe.cuentaclara.finance.FinancialEngine;
import pe.cuentaclara.finance.PaymentPlan;
import pe.cuentaclara.shared.BusinessException;
import pe.cuentaclara.shared.BusinessTime;
import pe.cuentaclara.shared.JsonSupport;
import pe.cuentaclara.statement.ClosedStatementRepository;
import pe.cuentaclara.store.Store;
import pe.cuentaclara.store.StoreAccessService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class CreditService {
    private final CreditSaleRepository sales;
    private final SaleItemRepository items;
    private final InstallmentRepository installments;
    private final StoreAccessService access;
    private final ProductService products;
    private final CreditBalanceService balances;
    private final FinancialEngine finance;
    private final ClosedStatementRepository statements;
    private final AuditService audit;
    private final BusinessTime time;
    private final JsonSupport json;

    public CreditService(
            CreditSaleRepository sales,
            SaleItemRepository items,
            InstallmentRepository installments,
            StoreAccessService access,
            ProductService products,
            CreditBalanceService balances,
            FinancialEngine finance,
            ClosedStatementRepository statements,
            AuditService audit,
            BusinessTime time,
            JsonSupport json) {
        this.sales = sales;
        this.items = items;
        this.installments = installments;
        this.access = access;
        this.products = products;
        this.balances = balances;
        this.finance = finance;
        this.statements = statements;
        this.audit = audit;
        this.time = time;
        this.json = json;
    }

    private record Calculation(
            Customer customer,
            PaymentPlan plan,
            List<SaleItemResponse> lines,
            BigDecimal amount,
            BigDecimal available,
            int months) {
        SalePreview preview(Long id, SaleRequest request) {
            return SalePreview.from(
                    id, plan, amount, customer.getCurrency(), available, lines, request, months);
        }
    }

    @Transactional(readOnly = true)
    public SalePreview preview(CurrentUser actor, Long selectedStore, SaleRequest request) {
        Store store = access.resolve(actor, selectedStore, true);
        return calculate(store, request).preview(null, request);
    }

    @Transactional
    public SalePreview create(CurrentUser actor, Long selectedStore, SaleRequest request) {
        Store store = access.resolve(actor, selectedStore, true);
        Calculation calculation = calculate(store, request);
        SalePreview preview = calculation.preview(null, request);
        if (request.expectedPlan() != null) {
            var agreement =
                    json.object(
                            Map.of(
                                    "amount",
                                    preview.amount(),
                                    "currency",
                                    preview.currency(),
                                    "financed",
                                    preview.financed(),
                                    "grace_days",
                                    preview.graceDays(),
                                    "grace_interest",
                                    preview.graceInterest(),
                                    "dues",
                                    preview.dues()));
            if (!agreement.equals(request.expectedPlan()))
                throw new BusinessException(
                        "El acuerdo cambió desde la simulación. Vuelve a calcular las cuotas.",
                        409);
        }
        CreditSale sale = new CreditSale();
        sale.setStore(store);
        sale.setCustomer(calculation.customer());
        sale.setPurchasedAt(request.purchasedAt());
        sale.setMode(request.mode());
        sale.setMonths(calculation.months());
        sale.setAmount(calculation.amount());
        sale.setFinanced(calculation.plan().financed());
        sale.setGraceDays(calculation.plan().graceDays());
        sale.setGraceInterest(calculation.plan().graceInterest());
        sale.setCurrency(calculation.customer().getCurrency());
        sale.setTermsJson(json.encode(CreditTerms.from(calculation.customer())));
        sale.setCycle(calculation.plan().cycle());
        sale.setCreatedAt(time.now());
        sales.saveAndFlush(sale);
        for (SaleItemResponse line : calculation.lines()) {
            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProduct(products.find(line.productId(), store.getId()));
            item.setName(line.name());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice());
            item.setSubtotal(line.subtotal());
            items.save(item);
        }
        for (var planned : calculation.plan().dues()) {
            Installment due = new Installment();
            due.setSale(sale);
            due.setNumber(planned.number());
            due.setDueDate(planned.date());
            due.setPrincipal(planned.principal());
            due.setInterest(planned.interest());
            due.setAmount(planned.amount());
            installments.save(due);
        }
        audit.record(
                actor.id(),
                store.getId(),
                "Compra registrada",
                sale.getId(),
                sale.getAmount() + " " + sale.getCurrency());
        return calculation.preview(sale.getId(), request);
    }

    private Calculation calculate(Store store, SaleRequest request) {
        Customer customer = access.customer(request.customerId(), store.getId());
        if (!customer.getActive()) throw new BusinessException("El cliente está desactivado.");
        if (request.purchasedAt().isAfter(time.now())
                || request.purchasedAt().isBefore(time.now().minusDays(3650)))
            throw new BusinessException(
                    "La compra debe estar entre hoy y los últimos 10 años, en hora de Lima.");
        int months =
                request.mode().equals("installments")
                        ? (request.months() == null ? 1 : request.months())
                        : 1;
        List<SaleItemResponse> lines = new ArrayList<>();
        var seen = new HashSet<Long>();
        BigDecimal total = BigDecimal.ZERO;
        for (SaleRequest.Line input : request.items()) {
            Product product = products.find(input.productId(), store.getId());
            if (!seen.add(product.getId()))
                throw new BusinessException("Agrupa las cantidades de un mismo producto.");
            if (!product.getActive() || !product.getCurrency().equals(customer.getCurrency()))
                throw new BusinessException(
                        "El producto debe estar activo y tener la moneda del cliente.");
            if (request.mode().equals("installments") && !product.getInstallmentAllowed())
                throw new BusinessException("Este producto solo se puede pagar al cierre.");
            BigDecimal subtotal =
                    FinancialEngine.money(input.quantity().multiply(product.getCreditPrice()));
            if (subtotal.signum() <= 0)
                throw new BusinessException("El subtotal debe ser mayor que cero.");
            total = total.add(subtotal);
            lines.add(
                    new SaleItemResponse(
                            null,
                            null,
                            product.getId(),
                            product.getName(),
                            input.quantity(),
                            product.getCreditPrice(),
                            subtotal));
        }
        if (total.compareTo(new BigDecimal("10000000")) > 0)
            throw new BusinessException("El importe supera el máximo permitido.");
        PaymentPlan plan =
                finance.plan(
                        total,
                        request.purchasedAt(),
                        CreditTerms.from(customer),
                        request.mode(),
                        months);
        BigDecimal available =
                FinancialEngine.money(
                        customer.getCreditLimit().subtract(balances.outstanding(customer.getId())));
        if (plan.financed().compareTo(available) > 0)
            throw new BusinessException(
                    "La compra supera el crédito disponible ("
                            + available
                            + " "
                            + customer.getCurrency()
                            + ").");
        if (statements.existsByCustomerIdAndCycle(customer.getId(), plan.cycle()))
            throw new BusinessException(
                    "Ese período ya está cerrado. Registra la compra en un período abierto.");
        return new Calculation(
                customer,
                plan,
                List.copyOf(lines),
                FinancialEngine.money(total),
                available,
                months);
    }
}
