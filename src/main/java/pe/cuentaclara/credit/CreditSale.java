package pe.cuentaclara.credit;

import jakarta.persistence.*;

import pe.cuentaclara.customer.Customer;
import pe.cuentaclara.store.Store;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Compra y copia del acuerdo financiero original. */
@Entity
@Table(name = "sales")
public class CreditSale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "purchased_at")
    private LocalDateTime purchasedAt;

    @Column(name = "mode")
    private String mode;

    @Column(name = "months")
    private Integer months;

    @Column(name = "amount")
    private BigDecimal amount;

    @Column(name = "financed")
    private BigDecimal financed;

    @Column(name = "grace_days")
    private Integer graceDays;

    @Column(name = "grace_interest")
    private BigDecimal graceInterest;

    @Column(name = "currency")
    private String currency;

    @Column(name = "terms_json")
    private String termsJson;

    @Column(name = "cycle")
    private LocalDate cycle;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public LocalDateTime getPurchasedAt() {
        return purchasedAt;
    }

    public void setPurchasedAt(LocalDateTime purchasedAt) {
        this.purchasedAt = purchasedAt;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Integer getMonths() {
        return months;
    }

    public void setMonths(Integer months) {
        this.months = months;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getFinanced() {
        return financed;
    }

    public void setFinanced(BigDecimal financed) {
        this.financed = financed;
    }

    public Integer getGraceDays() {
        return graceDays;
    }

    public void setGraceDays(Integer graceDays) {
        this.graceDays = graceDays;
    }

    public BigDecimal getGraceInterest() {
        return graceInterest;
    }

    public void setGraceInterest(BigDecimal graceInterest) {
        this.graceInterest = graceInterest;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getTermsJson() {
        return termsJson;
    }

    public void setTermsJson(String termsJson) {
        this.termsJson = termsJson;
    }

    public LocalDate getCycle() {
        return cycle;
    }

    public void setCycle(LocalDate cycle) {
        this.cycle = cycle;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
