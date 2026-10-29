package pe.cuentaclara.catalog;

import jakarta.persistence.*;

import pe.cuentaclara.store.Store;

import java.math.BigDecimal;

/** Producto o servicio con precios y modalidad permitida. */
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @Column(name = "name")
    private String name;

    @Column(name = "description")
    private String description = "";

    @Column(name = "brand")
    private String brand = "";

    @Column(name = "supplier")
    private String supplier = "";

    @Column(name = "unit")
    private String unit;

    @Column(name = "image")
    private String image = "";

    @Column(name = "cash_price")
    private BigDecimal cashPrice;

    @Column(name = "credit_price")
    private BigDecimal creditPrice;

    @Column(name = "currency")
    private String currency;

    @Column(name = "installment_allowed")
    private Boolean installmentAllowed = true;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public BigDecimal getCashPrice() {
        return cashPrice;
    }

    public void setCashPrice(BigDecimal cashPrice) {
        this.cashPrice = cashPrice;
    }

    public BigDecimal getCreditPrice() {
        return creditPrice;
    }

    public void setCreditPrice(BigDecimal creditPrice) {
        this.creditPrice = creditPrice;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Boolean getInstallmentAllowed() {
        return installmentAllowed;
    }

    public void setInstallmentAllowed(Boolean installmentAllowed) {
        this.installmentAllowed = installmentAllowed;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
