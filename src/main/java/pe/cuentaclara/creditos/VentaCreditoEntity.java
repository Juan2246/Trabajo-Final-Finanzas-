package pe.cuentaclara.creditos;

import jakarta.persistence.*;

import pe.cuentaclara.clientes.ClienteEntity;
import pe.cuentaclara.tiendas.TiendaEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Compra y copia del acuerdo financiero original. */
@Entity
@Table(name = "sales")
public class VentaCreditoEntity {
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

    @Column(name = "purchased_at")
    private LocalDateTime fechaCompra;

    @Column(name = "mode")
    private String modalidad;

    @Column(name = "months")
    private Integer meses;

    @Column(name = "amount")
    private BigDecimal importe;

    @Column(name = "financed")
    private BigDecimal capitalFinanciado;

    @Column(name = "grace_days")
    private Integer diasGracia;

    @Column(name = "grace_interest")
    private BigDecimal interesGracia;

    @Column(name = "currency")
    private String moneda;

    @Column(name = "terms_json")
    private String condicionesJson;

    @Column(name = "cycle")
    private LocalDate ciclo;

    @Column(name = "created_at")
    private LocalDateTime fechaCreacion;

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

    public LocalDateTime getFechaCompra() {
        return fechaCompra;
    }

    public void setFechaCompra(LocalDateTime fechaCompra) {
        this.fechaCompra = fechaCompra;
    }

    public String getModalidad() {
        return modalidad;
    }

    public void setModalidad(String modalidad) {
        this.modalidad = modalidad;
    }

    public Integer getMeses() {
        return meses;
    }

    public void setMeses(Integer meses) {
        this.meses = meses;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public void setImporte(BigDecimal importe) {
        this.importe = importe;
    }

    public BigDecimal getCapitalFinanciado() {
        return capitalFinanciado;
    }

    public void setCapitalFinanciado(BigDecimal capitalFinanciado) {
        this.capitalFinanciado = capitalFinanciado;
    }

    public Integer getDiasGracia() {
        return diasGracia;
    }

    public void setDiasGracia(Integer diasGracia) {
        this.diasGracia = diasGracia;
    }

    public BigDecimal getInteresGracia() {
        return interesGracia;
    }

    public void setInteresGracia(BigDecimal interesGracia) {
        this.interesGracia = interesGracia;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public String getCondicionesJson() {
        return condicionesJson;
    }

    public void setCondicionesJson(String condicionesJson) {
        this.condicionesJson = condicionesJson;
    }

    public LocalDate getCiclo() {
        return ciclo;
    }

    public void setCiclo(LocalDate ciclo) {
        this.ciclo = ciclo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
