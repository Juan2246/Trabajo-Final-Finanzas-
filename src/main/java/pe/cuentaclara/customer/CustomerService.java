package pe.cuentaclara.customer;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.audit.AuditService;
import pe.cuentaclara.auth.AccountService;
import pe.cuentaclara.auth.CurrentUser;
import pe.cuentaclara.credit.CreditBalanceService;
import pe.cuentaclara.finance.FinancialEngine;
import pe.cuentaclara.shared.BusinessException;
import pe.cuentaclara.shared.BusinessTime;
import pe.cuentaclara.shared.JsonSupport;
import pe.cuentaclara.store.Store;
import pe.cuentaclara.store.StoreAccessService;

@Service
public class CustomerService {
    private final CustomerRepository customers;
    private final StoreAccessService access;
    private final AccountService accounts;
    private final CreditBalanceService balances;
    private final FinancialEngine finance;
    private final BusinessTime time;
    private final AuditService audit;
    private final JsonSupport json;

    public CustomerService(
            CustomerRepository customers,
            StoreAccessService access,
            AccountService accounts,
            CreditBalanceService balances,
            FinancialEngine finance,
            BusinessTime time,
            AuditService audit,
            JsonSupport json) {
        this.customers = customers;
        this.access = access;
        this.accounts = accounts;
        this.balances = balances;
        this.finance = finance;
        this.time = time;
        this.audit = audit;
        this.json = json;
    }

    @Transactional
    public Long create(CurrentUser actor, Long selectedStore, CustomerRequest request) {
        Store store = access.resolve(actor, selectedStore, true);
        if (!Boolean.TRUE.equals(request.consent()))
            throw new BusinessException(
                    "Se necesita el consentimiento del cliente para registrar sus datos.");
        Customer customer = new Customer();
        customer.setStore(store);
        apply(customer, request);
        customer.setConsentAt(time.now());
        customer.setConsentVersion("1");
        customers.saveAndFlush(customer);
        accounts.create(
                request.username(),
                customer.getName(),
                request.password(),
                "customer",
                store,
                customer);
        audit.record(
                actor.id(),
                store.getId(),
                "Cliente registrado",
                customer.getId(),
                customer.getName());
        return customer.getId();
    }

    @Transactional
    public void update(CurrentUser actor, Long selectedStore, Long id, JsonNode patch) {
        Store store = access.resolve(actor, selectedStore, true);
        Customer customer = access.customer(id, store.getId());
        var pending = balances.outstanding(id);
        CustomerRequest request =
                json.merge(CustomerResponse.from(customer, pending), patch, CustomerRequest.class);
        if (pending.signum() > 0 && !request.currency().equals(customer.getCurrency()))
            throw new BusinessException("No puedes cambiar la moneda mientras exista deuda.");
        if (request.creditLimit().compareTo(pending) < 0)
            throw new BusinessException("El límite no puede ser menor que el capital pendiente.");
        apply(customer, request);
        accounts.updateCustomer(customer, request.password());
        audit.record(actor.id(), store.getId(), "Cliente actualizado", id, "");
    }

    public void apply(Customer customer, CustomerRequest request) {
        int frequency = request.frequency() == null ? 12 : request.frequency();
        int lateFrequency = request.lateFrequency() == null ? 12 : request.lateFrequency();
        finance.factor(request.rate(), request.rateKind(), request.ratePeriod(), 30, frequency);
        finance.factor(
                request.lateRate(), request.lateKind(), request.latePeriod(), 30, lateFrequency);
        customer.setName(request.name().strip());
        customer.setDocument(request.document().strip());
        customer.setEmail(request.email().strip());
        customer.setPhone(request.phone() == null ? "" : request.phone().strip());
        customer.setCurrency(request.currency());
        customer.setCreditLimit(request.creditLimit());
        customer.setRate(request.rate());
        customer.setRateKind(request.rateKind());
        customer.setRatePeriod(request.ratePeriod());
        customer.setFrequency(frequency);
        customer.setLateRate(request.lateRate());
        customer.setLateKind(request.lateKind());
        customer.setLatePeriod(request.latePeriod());
        customer.setLateFrequency(lateFrequency);
        customer.setMaxMonths(request.maxMonths());
        customer.setCutoffDay(request.cutoffDay());
        customer.setCutoffHour(request.cutoffHour() == null ? 23 : request.cutoffHour());
        customer.setPayDay(request.payDay());
        customer.setActive(request.active() == null || request.active());
    }
}
