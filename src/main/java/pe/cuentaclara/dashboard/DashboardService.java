package pe.cuentaclara.dashboard;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.audit.*;
import pe.cuentaclara.auth.CurrentUser;
import pe.cuentaclara.catalog.*;
import pe.cuentaclara.credit.*;
import pe.cuentaclara.customer.*;
import pe.cuentaclara.payment.*;
import pe.cuentaclara.shared.BusinessTime;
import pe.cuentaclara.store.*;

import java.util.List;

@Service
public class DashboardService {
    private final StoreAccessService access;
    private final StoreRepository stores;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final PaymentRepository payments;
    private final AuditEventRepository audit;
    private final CreditQueryService credits;
    private final CreditBalanceService balances;
    private final BusinessTime time;

    public DashboardService(
            StoreAccessService access,
            StoreRepository stores,
            CustomerRepository customers,
            ProductRepository products,
            PaymentRepository payments,
            AuditEventRepository audit,
            CreditQueryService credits,
            CreditBalanceService balances,
            BusinessTime time) {
        this.access = access;
        this.stores = stores;
        this.customers = customers;
        this.products = products;
        this.payments = payments;
        this.audit = audit;
        this.credits = credits;
        this.balances = balances;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public DashboardResponse get(CurrentUser actor, Long selectedStore) {
        Store store = access.resolve(actor, selectedStore, false);
        List<StoreResponse> availableStores =
                actor.isPlatform()
                        ? stores.findAllByOrderByNameAsc().stream()
                                .map(StoreResponse::from)
                                .toList()
                        : List.of();
        if (store == null)
            return new DashboardResponse(
                    actor.response(),
                    null,
                    availableStores,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    time.today());
        Long customerId = actor.isCustomer() ? actor.customerId() : null;
        var visibleCustomers =
                customers.findAllByStoreIdOrderByNameAsc(store.getId()).stream()
                        .filter(
                                customer ->
                                        customerId == null || customer.getId().equals(customerId))
                        .map(
                                customer ->
                                        CustomerResponse.from(
                                                customer, balances.outstanding(customer.getId())))
                        .toList();
        var visibleProducts =
                actor.isCustomer()
                        ? List.<ProductResponse>of()
                        : products.findAllByStoreIdOrderByNameAsc(store.getId()).stream()
                                .map(ProductResponse::from)
                                .toList();
        var visibleAudit =
                actor.isCustomer()
                        ? List.<AuditResponse>of()
                        : audit.findTop200ByStoreIdOrderByIdDesc(store.getId()).stream()
                                .map(AuditResponse::from)
                                .toList();
        var visiblePayments =
                payments.findAllByStoreIdOrderByIdDesc(store.getId()).stream()
                        .filter(
                                payment ->
                                        customerId == null
                                                || payment.getCustomer().getId().equals(customerId))
                        .map(PaymentResponse::from)
                        .toList();
        return new DashboardResponse(
                actor.response(),
                StoreResponse.from(store),
                availableStores,
                visibleCustomers,
                visibleProducts,
                credits.sales(store.getId(), customerId),
                credits.dues(store.getId(), customerId, time.today()),
                visiblePayments,
                visibleAudit,
                time.today());
    }
}
