package pe.cuentaclara.pagos;

import jakarta.persistence.*;

import pe.cuentaclara.clientes.ClienteEntity;
import pe.cuentaclara.tiendas.TiendaEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Recibo por la liquidación completa de una cuenta. */
@Entity
@Table(name = "payments")
public class PagoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private TiendaEntity tienda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private ClienteEntity cliente;

    @Column(name = "paid_at")
    private LocalDateTime fechaPago;

    @Column(name = "base_amount")
    private BigDecimal importeBase;

    @Column(name = "late_amount")
    private BigDecimal importeMora;

    @Column(name = "amount")
    private BigDecimal importe;

    @Column(name = "currency")
    private String moneda;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TiendaEntity getTienda() {
        return tienda;
    }

    public void setTienda(TiendaEntity tienda) {
        this.tienda = tienda;
    }

    public ClienteEntity getCliente() {
        return cliente;
    }

    public void setCliente(ClienteEntity cliente) {
        this.cliente = cliente;
    }

    public LocalDateTime getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDateTime fechaPago) {
        this.fechaPago = fechaPago;
    }

    public BigDecimal getImporteBase() {
        return importeBase;
    }

    public void setImporteBase(BigDecimal importeBase) {
        this.importeBase = importeBase;
    }

    public BigDecimal getImporteMora() {
        return importeMora;
    }

    public void setImporteMora(BigDecimal importeMora) {
        this.importeMora = importeMora;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }
}
