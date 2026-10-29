package pe.cuentaclara.statement;

import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.auth.CurrentUser;
import pe.cuentaclara.credit.*;
import pe.cuentaclara.customer.Customer;
import pe.cuentaclara.customer.CustomerRepository;
import pe.cuentaclara.customer.CustomerResponse;
import pe.cuentaclara.finance.FinancialEngine;
import pe.cuentaclara.shared.BusinessException;
import pe.cuentaclara.shared.BusinessTime;
import pe.cuentaclara.shared.JsonSupport;
import pe.cuentaclara.store.StoreAccessService;
import pe.cuentaclara.store.StoreResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatementService {
    public record ClosingSummary(Long id, LocalDate cycle, LocalDateTime generatedAt) {}

    public record Snapshot(
            CustomerResponse customer,
            StoreResponse store,
            LocalDate date,
            List<DueResponse> dues,
            List<SaleResponse> sales,
            List<DueResponse> payable,
            LocalDate paymentDate,
            BigDecimal amountDue,
            BigDecimal futureBalance,
            Map<String, BigDecimal> totals,
            LocalDateTime generatedAt) {}

    private final ClosedStatementRepository statements;
    private final CustomerRepository customers;
    private final CreditSaleRepository sales;
    private final CreditQueryService query;
    private final CreditBalanceService balances;
    private final StoreAccessService access;
    private final FinancialEngine finance;
    private final BusinessTime time;
    private final JsonSupport json;

    public StatementService(
            ClosedStatementRepository statements,
            CustomerRepository customers,
            CreditSaleRepository sales,
            CreditQueryService query,
            CreditBalanceService balances,
            StoreAccessService access,
            FinancialEngine finance,
            BusinessTime time,
            JsonSupport json) {
        this.statements = statements;
        this.customers = customers;
        this.sales = sales;
        this.query = query;
        this.balances = balances;
        this.access = access;
        this.finance = finance;
        this.time = time;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ObjectNode get(
            CurrentUser actor,
            Long selectedStore,
            Long selectedCustomer,
            LocalDate date,
            LocalDate cycle) {
        var store = access.resolve(actor, selectedStore, true);
        Long customerId =
                selectedCustomer == null && actor.isCustomer()
                        ? actor.customerId()
                        : selectedCustomer;
        if (customerId == null) throw new BusinessException("Selecciona un cliente.");
        access.checkCustomer(actor, customerId);
        Customer customer = access.customer(customerId, store.getId());
        LocalDate when = date == null ? time.today() : date;
        if (when.isAfter(time.today()) || when.isBefore(time.today().minusDays(3650)))
            throw new BusinessException("La fecha de consulta no es válida.");
        ObjectNode result;
        if (cycle != null) {
            var closed =
                    statements
                            .findByCustomerIdAndCycle(customerId, cycle)
                            .orElseThrow(() -> new BusinessException("No existe ese cierre.", 404));
            // Un cierre de la versión anterior se lee tal como quedó guardado.
            result = (ObjectNode) json.parse(closed.getSnapshot());
            result.put("is_closed", true);
            result.put("cycle", cycle.toString());
        } else {
            result = json.object(snapshot(customer, when));
            result.put("is_closed", false);
        }
        var available =
                statements.findAllByCustomerIdOrderByCycleDesc(customerId).stream()
                        .map(
                                row ->
                                        new ClosingSummary(
                                                row.getId(), row.getCycle(), row.getGeneratedAt()))
                        .toList();
        result.set("closed_statements", json.object(Map.of("items", available)).get("items"));
        return result;
    }

    public Snapshot snapshot(Customer customer, LocalDate when) {
        Long storeId = customer.getStore().getId();
        var limit = when.equals(time.today()) ? time.now() : when.atTime(LocalTime.MAX);
        var purchases =
                query.sales(storeId, customer.getId()).stream()
                        .filter(
                                sale ->
                                        !sale.purchasedAt().toLocalDate().isAfter(when)
                                                && !sale.cycle()
                                                        .atTime(sale.terms().cutoffHour(), 0)
                                                        .isAfter(limit))
                        .sorted(
                                Comparator.comparing(SaleResponse::purchasedAt)
                                        .thenComparing(SaleResponse::id))
                        .toList();
        var ids = purchases.stream().map(SaleResponse::id).collect(Collectors.toSet());
        var dues =
                query.dues(storeId, customer.getId(), when).stream()
                        .filter(due -> ids.contains(due.saleId()))
                        .toList();
        var pending = dues.stream().filter(due -> due.paymentId() == null).toList();
        var total =
                FinancialEngine.money(
                        pending.stream()
                                .map(DueResponse::total)
                                .reduce(BigDecimal.ZERO, BigDecimal::add));
        var anchor =
                pending.stream()
                        .map(DueResponse::dueDate)
                        .filter(due -> !due.isBefore(when))
                        .min(LocalDate::compareTo)
                        .orElse(when);
        var payable = pending.stream().filter(due -> !due.dueDate().isAfter(anchor)).toList();
        var amount =
                FinancialEngine.money(
                        payable.stream()
                                .map(DueResponse::total)
                                .reduce(BigDecimal.ZERO, BigDecimal::add));
        var zero = FinancialEngine.money(BigDecimal.ZERO);
        return new Snapshot(
                CustomerResponse.from(customer, balances.outstanding(customer.getId())),
                StoreResponse.from(customer.getStore()),
                when,
                dues,
                purchases,
                payable,
                anchor,
                amount,
                total.subtract(amount),
                Map.of(
                        "PEN",
                        customer.getCurrency().equals("PEN") ? total : zero,
                        "USD",
                        customer.getCurrency().equals("USD") ? total : zero),
                time.now());
    }

    @Transactional
    public int closeAll() {
        int created = 0;
        for (Customer customer : customers.findAllByActiveTrueAndStoreActiveTrue()) {
            var history = sales.findAllByCustomerIdOrderByCycleAsc(customer.getId());
            if (history.isEmpty()) continue;
            LocalDate cycle = history.getFirst().getCycle();
            for (int count = 0; count < 121 && !cycle.isAfter(time.today()); count++) {
                if (!cycle.atTime(customer.getCutoffHour(), 0).isAfter(time.now())
                        && !statements.existsByCustomerIdAndCycle(customer.getId(), cycle)) {
                    ClosedStatement statement = new ClosedStatement();
                    statement.setStore(customer.getStore());
                    statement.setCustomer(customer);
                    statement.setCycle(cycle);
                    statement.setGeneratedAt(time.now());
                    statement.setSnapshot(json.encode(snapshot(customer, cycle)));
                    statements.save(statement);
                    created++;
                }
                cycle = finance.monthDate(cycle, 1, customer.getCutoffDay());
            }
        }
        return created;
    }
}
