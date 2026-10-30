package pe.cuentaclara.creditos;

import jakarta.persistence.*;

import pe.cuentaclara.pagos.Pago;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Obligación de pago y recibo que la cancela. */
@Entity
@Table(name = "dues")
public class Cuota {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private VentaCredito venta;

    @Column(name = "number")
    private Integer numero;

    @Column(name = "due_date")
    private LocalDate fechaVencimiento;

    @Column(name = "principal")
    private BigDecimal capital;

    @Column(name = "interest")
    private BigDecimal interes;

    @Column(name = "amount")
    private BigDecimal importe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Pago pago;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public VentaCredito getVenta() {
        return venta;
    }

    public void setVenta(VentaCredito venta) {
        this.venta = venta;
    }

    public Integer getNumero() {
        return numero;
    }

    public void setNumero(Integer numero) {
        this.numero = numero;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public BigDecimal getCapital() {
        return capital;
    }

    public void setCapital(BigDecimal capital) {
        this.capital = capital;
    }

    public BigDecimal getInteres() {
        return interes;
    }

    public void setInteres(BigDecimal interes) {
        this.interes = interes;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    public Pago getPago() {
        return pago;
    }

    public void setPago(Pago pago) {
        this.pago = pago;
    }
}
