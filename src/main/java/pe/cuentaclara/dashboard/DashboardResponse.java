package pe.cuentaclara.dashboard;

import pe.cuentaclara.audit.AuditResponse;
import pe.cuentaclara.auth.UserResponse;
import pe.cuentaclara.catalog.ProductResponse;
import pe.cuentaclara.credit.DueResponse;
import pe.cuentaclara.credit.SaleResponse;
import pe.cuentaclara.customer.CustomerResponse;
import pe.cuentaclara.payment.PaymentResponse;
import pe.cuentaclara.store.StoreResponse;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        UserResponse user,
        StoreResponse store,
        List<StoreResponse> stores,
        List<CustomerResponse> customers,
        List<ProductResponse> products,
        List<SaleResponse> sales,
        List<DueResponse> dues,
        List<PaymentResponse> payments,
        List<AuditResponse> audit,
        LocalDate today) {}
