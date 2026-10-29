package pe.cuentaclara.credit;

import jakarta.persistence.*;

import pe.cuentaclara.payment.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Obligación de pago y recibo que la cancela. */
@Entity
@Table(name = "dues")
public class Installment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private CreditSale sale;

    @Column(name = "number")
    private Integer number;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "principal")
    private BigDecimal principal;

    @Column(name = "interest")
    private BigDecimal interest;

    @Column(name = "amount")
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CreditSale getSale() {
        return sale;
    }

    public void setSale(CreditSale sale) {
        this.sale = sale;
    }

    public Integer getNumber() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public BigDecimal getPrincipal() {
        return principal;
    }

    public void setPrincipal(BigDecimal principal) {
        this.principal = principal;
    }

    public BigDecimal getInterest() {
        return interest;
    }

    public void setInterest(BigDecimal interest) {
        this.interest = interest;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }
}
