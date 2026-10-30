package pe.cuentaclara.pagos;

import jakarta.persistence.*;

import pe.cuentaclara.creditos.Cuota;

import java.math.BigDecimal;

/** Mora y días de atraso conservados al cobrar. */
@Entity
@Table(name = "payment_lines")
public class DetallePago {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Pago pago;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "due_id")
    private Cuota cuota;

    @Column(name = "late_days")
    private Integer diasMora;

    @Column(name = "late_amount")
    private BigDecimal importeMora;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Pago getPago() {
        return pago;
    }

    public void setPago(Pago pago) {
        this.pago = pago;
    }

    public Cuota getCuota() {
        return cuota;
    }

    public void setCuota(Cuota cuota) {
        this.cuota = cuota;
    }

    public Integer getDiasMora() {
        return diasMora;
    }

    public void setDiasMora(Integer diasMora) {
        this.diasMora = diasMora;
    }

    public BigDecimal getImporteMora() {
        return importeMora;
    }

    public void setImporteMora(BigDecimal importeMora) {
        this.importeMora = importeMora;
    }
}
