package pe.cuentaclara.payment;

import jakarta.persistence.*;

import pe.cuentaclara.credit.Installment;

import java.math.BigDecimal;

/** Mora y días de atraso conservados al cobrar. */
@Entity
@Table(name = "payment_lines")
public class PaymentLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "due_id")
    private Installment due;

    @Column(name = "late_days")
    private Integer lateDays;

    @Column(name = "late_amount")
    private BigDecimal lateAmount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public Installment getDue() {
        return due;
    }

    public void setDue(Installment due) {
        this.due = due;
    }

    public Integer getLateDays() {
        return lateDays;
    }

    public void setLateDays(Integer lateDays) {
        this.lateDays = lateDays;
    }

    public BigDecimal getLateAmount() {
        return lateAmount;
    }

    public void setLateAmount(BigDecimal lateAmount) {
        this.lateAmount = lateAmount;
    }
}
