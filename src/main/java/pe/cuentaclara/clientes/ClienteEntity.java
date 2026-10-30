package pe.cuentaclara.clientes;

import jakarta.persistence.*;

import pe.cuentaclara.tiendas.TiendaEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Cliente y condiciones para sus próximas compras. */
@Entity
@Table(name = "customers")
public class ClienteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private TiendaEntity tienda;

    @Column(name = "name")
    private String nombre;

    @Column(name = "document")
    private String documento;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String telefono = "";

    @Column(name = "currency")
    private String moneda;

    @Column(name = "credit_limit")
    private BigDecimal limiteCredito;

    @Column(name = "rate")
    private BigDecimal tasa;

    @Column(name = "rate_kind")
    private String tipoTasa;

    @Column(name = "rate_period")
    private Integer periodoTasa;

    @Column(name = "frequency")
    private Integer frecuencia;

    @Column(name = "late_rate")
    private BigDecimal tasaMora;

    @Column(name = "late_kind")
    private String tipoTasaMora;

    @Column(name = "late_period")
    private Integer periodoTasaMora;

    @Column(name = "late_frequency")
    private Integer frecuenciaMora;

    @Column(name = "max_months")
    private Integer maximoMeses;

    @Column(name = "cutoff_day")
    private Integer diaCorte;

    @Column(name = "cutoff_hour")
    private Integer horaCorte;

    @Column(name = "pay_day")
    private Integer diaPago;

    @Column(name = "consent_at")
    private LocalDateTime fechaConsentimiento;

    @Column(name = "consent_version")
    private String versionConsentimiento;

    @Column(name = "active")
    private Boolean activo = true;

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

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public BigDecimal getLimiteCredito() {
        return limiteCredito;
    }

    public void setLimiteCredito(BigDecimal limiteCredito) {
        this.limiteCredito = limiteCredito;
    }

    public BigDecimal getTasa() {
        return tasa;
    }

    public void setTasa(BigDecimal tasa) {
        this.tasa = tasa;
    }

    public String getTipoTasa() {
        return tipoTasa;
    }

    public void setTipoTasa(String tipoTasa) {
        this.tipoTasa = tipoTasa;
    }

    public Integer getPeriodoTasa() {
        return periodoTasa;
    }

    public void setPeriodoTasa(Integer periodoTasa) {
        this.periodoTasa = periodoTasa;
    }

    public Integer getFrecuencia() {
        return frecuencia;
    }

    public void setFrecuencia(Integer frecuencia) {
        this.frecuencia = frecuencia;
    }

    public BigDecimal getTasaMora() {
        return tasaMora;
    }

    public void setTasaMora(BigDecimal tasaMora) {
        this.tasaMora = tasaMora;
    }

    public String getTipoTasaMora() {
        return tipoTasaMora;
    }

    public void setTipoTasaMora(String tipoTasaMora) {
        this.tipoTasaMora = tipoTasaMora;
    }

    public Integer getPeriodoTasaMora() {
        return periodoTasaMora;
    }

    public void setPeriodoTasaMora(Integer periodoTasaMora) {
        this.periodoTasaMora = periodoTasaMora;
    }

    public Integer getFrecuenciaMora() {
        return frecuenciaMora;
    }

    public void setFrecuenciaMora(Integer frecuenciaMora) {
        this.frecuenciaMora = frecuenciaMora;
    }

    public Integer getMaximoMeses() {
        return maximoMeses;
    }

    public void setMaximoMeses(Integer maximoMeses) {
        this.maximoMeses = maximoMeses;
    }

    public Integer getDiaCorte() {
        return diaCorte;
    }

    public void setDiaCorte(Integer diaCorte) {
        this.diaCorte = diaCorte;
    }

    public Integer getHoraCorte() {
        return horaCorte;
    }

    public void setHoraCorte(Integer horaCorte) {
        this.horaCorte = horaCorte;
    }

    public Integer getDiaPago() {
        return diaPago;
    }

    public void setDiaPago(Integer diaPago) {
        this.diaPago = diaPago;
    }

    public LocalDateTime getFechaConsentimiento() {
        return fechaConsentimiento;
    }

    public void setFechaConsentimiento(LocalDateTime fechaConsentimiento) {
        this.fechaConsentimiento = fechaConsentimiento;
    }

    public String getVersionConsentimiento() {
        return versionConsentimiento;
    }

    public void setVersionConsentimiento(String versionConsentimiento) {
        this.versionConsentimiento = versionConsentimiento;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }

    @Column(name = "inactive_since")
    private LocalDateTime inactivoDesde;

    public LocalDateTime getInactivoDesde() {
        return inactivoDesde;
    }

    public void setInactivoDesde(LocalDateTime inactivoDesde) {
        this.inactivoDesde = inactivoDesde;
    }

    @Column(name = "erase_requested_at")
    private LocalDateTime retiroSolicitadoEn;

    public LocalDateTime getRetiroSolicitadoEn() {
        return retiroSolicitadoEn;
    }

    public void setRetiroSolicitadoEn(LocalDateTime retiroSolicitadoEn) {
        this.retiroSolicitadoEn = retiroSolicitadoEn;
    }

    @Column(name = "anonymized_at")
    private LocalDateTime anonimizadoEn;

    public LocalDateTime getAnonimizadoEn() {
        return anonimizadoEn;
    }

    public void setAnonimizadoEn(LocalDateTime anonimizadoEn) {
        this.anonimizadoEn = anonimizadoEn;
    }

    @Column(name = "retention_days")
    private Integer diasConservacion = 180;

    public Integer getDiasConservacion() {
        return diasConservacion;
    }

    public void setDiasConservacion(Integer diasConservacion) {
        this.diasConservacion = diasConservacion;
    }
}
