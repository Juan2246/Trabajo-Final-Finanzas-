package pe.cuentaclara.customer;

import jakarta.persistence.*;

import pe.cuentaclara.store.Store;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Cliente y condiciones para sus próximas compras. */
@Entity
@Table(name = "customers")
public class Customer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "name")
    private String name;

    @Column(name = "document")
    private String document;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone = "";

    @Column(name = "currency")
    private String currency;

    @Column(name = "credit_limit")
    private BigDecimal creditLimit;

    @Column(name = "rate")
    private BigDecimal rate;

    @Column(name = "rate_kind")
    private String rateKind;

    @Column(name = "rate_period")
    private Integer ratePeriod;

    @Column(name = "frequency")
    private Integer frequency;

    @Column(name = "late_rate")
    private BigDecimal lateRate;

    @Column(name = "late_kind")
    private String lateKind;

    @Column(name = "late_period")
    private Integer latePeriod;

    @Column(name = "late_frequency")
    private Integer lateFrequency;

    @Column(name = "max_months")
    private Integer maxMonths;

    @Column(name = "cutoff_day")
    private Integer cutoffDay;

    @Column(name = "cutoff_hour")
    private Integer cutoffHour;

    @Column(name = "pay_day")
    private Integer payDay;

    @Column(name = "consent_at")
    private LocalDateTime consentAt;

    @Column(name = "consent_version")
    private String consentVersion;

    @Column(name = "active")
    private Boolean active = true;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public String getRateKind() {
        return rateKind;
    }

    public void setRateKind(String rateKind) {
        this.rateKind = rateKind;
    }

    public Integer getRatePeriod() {
        return ratePeriod;
    }

    public void setRatePeriod(Integer ratePeriod) {
        this.ratePeriod = ratePeriod;
    }

    public Integer getFrequency() {
        return frequency;
    }

    public void setFrequency(Integer frequency) {
        this.frequency = frequency;
    }

    public BigDecimal getLateRate() {
        return lateRate;
    }

    public void setLateRate(BigDecimal lateRate) {
        this.lateRate = lateRate;
    }

    public String getLateKind() {
        return lateKind;
    }

    public void setLateKind(String lateKind) {
        this.lateKind = lateKind;
    }

    public Integer getLatePeriod() {
        return latePeriod;
    }

    public void setLatePeriod(Integer latePeriod) {
        this.latePeriod = latePeriod;
    }

    public Integer getLateFrequency() {
        return lateFrequency;
    }

    public void setLateFrequency(Integer lateFrequency) {
        this.lateFrequency = lateFrequency;
    }

    public Integer getMaxMonths() {
        return maxMonths;
    }

    public void setMaxMonths(Integer maxMonths) {
        this.maxMonths = maxMonths;
    }

    public Integer getCutoffDay() {
        return cutoffDay;
    }

    public void setCutoffDay(Integer cutoffDay) {
        this.cutoffDay = cutoffDay;
    }

    public Integer getCutoffHour() {
        return cutoffHour;
    }

    public void setCutoffHour(Integer cutoffHour) {
        this.cutoffHour = cutoffHour;
    }

    public Integer getPayDay() {
        return payDay;
    }

    public void setPayDay(Integer payDay) {
        this.payDay = payDay;
    }

    public LocalDateTime getConsentAt() {
        return consentAt;
    }

    public void setConsentAt(LocalDateTime consentAt) {
        this.consentAt = consentAt;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public void setConsentVersion(String consentVersion) {
        this.consentVersion = consentVersion;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
