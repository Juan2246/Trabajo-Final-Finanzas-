package pe.cuentaclara.statement;

import jakarta.persistence.*;

import pe.cuentaclara.customer.Customer;
import pe.cuentaclara.store.Store;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Fotografía inmutable del estado al corte. */
@Entity
@Table(name = "statements")
public class ClosedStatement {
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

    @Column(name = "cycle")
    private LocalDate cycle;

    @Column(name = "snapshot")
    private String snapshot;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

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

    public LocalDate getCycle() {
        return cycle;
    }

    public void setCycle(LocalDate cycle) {
        this.cycle = cycle;
    }

    public String getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(String snapshot) {
        this.snapshot = snapshot;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
