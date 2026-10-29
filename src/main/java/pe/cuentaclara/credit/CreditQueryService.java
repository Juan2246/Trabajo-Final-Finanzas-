package pe.cuentaclara.credit;

import org.springframework.stereotype.Service;

import pe.cuentaclara.finance.CreditTerms;
import pe.cuentaclara.finance.FinancialEngine;
import pe.cuentaclara.shared.JsonSupport;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class CreditQueryService {
    private final CreditSaleRepository sales;
    private final SaleItemRepository items;
    private final InstallmentRepository installments;
    private final JsonSupport json;
    private final FinancialEngine finance;

    public CreditQueryService(
            CreditSaleRepository sales,
            SaleItemRepository items,
            InstallmentRepository installments,
            JsonSupport json,
            FinancialEngine finance) {
        this.sales = sales;
        this.items = items;
        this.installments = installments;
        this.json = json;
        this.finance = finance;
    }

    public CreditTerms terms(CreditSale sale) {
        return json.convert(json.parse(sale.getTermsJson()), CreditTerms.class);
    }

    public List<SaleResponse> sales(Long storeId, Long customerId) {
        return sales.findAllByStoreIdOrderByPurchasedAtDescIdDesc(storeId).stream()
                .filter(sale -> customerId == null || sale.getCustomer().getId().equals(customerId))
                .map(
                        sale ->
                                new SaleResponse(
                                        sale.getId(),
                                        storeId,
                                        sale.getCustomer().getId(),
                                        sale.getCustomer().getName(),
                                        sale.getPurchasedAt(),
                                        sale.getMode(),
                                        sale.getMonths(),
                                        sale.getAmount(),
                                        sale.getFinanced(),
                                        sale.getGraceDays(),
                                        sale.getGraceInterest(),
                                        sale.getCurrency(),
                                        sale.getCycle(),
                                        sale.getCreatedAt(),
                                        terms(sale),
                                        items.findAllBySaleIdOrderByIdAsc(sale.getId()).stream()
                                                .map(SaleItemResponse::from)
                                                .toList()))
                .toList();
    }

    public List<DueResponse> dues(Long storeId, Long customerId, LocalDate asOf) {
        return installments.findAllBySaleStoreIdOrderByDueDateAscIdAsc(storeId).stream()
                .filter(
                        due ->
                                customerId == null
                                        || due.getSale().getCustomer().getId().equals(customerId))
                .sorted(
                        Comparator.comparing(Installment::getDueDate)
                                .thenComparing(d -> d.getSale().getPurchasedAt())
                                .thenComparing(Installment::getNumber))
                .map(due -> view(due, asOf))
                .toList();
    }

    private DueResponse view(Installment due, LocalDate asOf) {
        CreditSale sale = due.getSale();
        var paidAt = due.getPayment() == null ? null : due.getPayment().getPaidAt();
        LocalDate effective =
                paidAt != null && paidAt.toLocalDate().isBefore(asOf) ? paidAt.toLocalDate() : asOf;
        CreditTerms terms = terms(sale);
        var late = finance.lateInterest(due.getAmount(), due.getDueDate(), effective, terms);
        boolean paid = paidAt != null && !paidAt.toLocalDate().isAfter(asOf);
        LocalDate purchased = sale.getPurchasedAt().toLocalDate();
        int interestDays =
                sale.getMode().equals("end")
                        ? finance.commercialDays(
                                purchased.isAfter(due.getDueDate()) ? due.getDueDate() : purchased,
                                due.getDueDate())
                        : 30;
        return new DueResponse(
                due.getId(),
                sale.getId(),
                due.getNumber(),
                due.getDueDate(),
                due.getPrincipal(),
                due.getInterest(),
                due.getAmount(),
                paid ? due.getPayment().getId() : null,
                sale.getCustomer().getId(),
                sale.getCustomer().getName(),
                sale.getCurrency(),
                sale.getPurchasedAt(),
                sale.getMode(),
                paid ? paidAt : null,
                late.amount(),
                late.days(),
                due.getAmount(),
                effective,
                interestDays,
                FinancialEngine.money(due.getAmount().add(late.amount())),
                terms);
    }
}
