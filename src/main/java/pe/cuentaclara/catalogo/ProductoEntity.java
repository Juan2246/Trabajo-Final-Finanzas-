package pe.cuentaclara.catalogo;

import jakarta.persistence.*;

import pe.cuentaclara.tiendas.TiendaEntity;

import java.math.BigDecimal;

/** Producto o servicio con precios y modalidad permitida. */
@Entity
@Table(name = "products")
public class ProductoEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private TiendaEntity tienda;

    @Column(name = "name")
    private String nombre;

    @Column(name = "description")
    private String descripcion = "";

    @Column(name = "brand")
    private String marca = "";

    @Column(name = "supplier")
    private String proveedor = "";

    @Column(name = "unit")
    private String unidad;

    @Column(name = "image")
    private String imagen = "";

    @Column(name = "cash_price")
    private BigDecimal precioContado;

    @Column(name = "credit_price")
    private BigDecimal precioCredito;

    @Column(name = "currency")
    private String moneda;

    @Column(name = "installment_allowed")
    private Boolean permiteCuotas = true;

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

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getProveedor() {
        return proveedor;
    }

    public void setProveedor(String proveedor) {
        this.proveedor = proveedor;
    }

    public String getUnidad() {
        return unidad;
    }

    public void setUnidad(String unidad) {
        this.unidad = unidad;
    }

    public String getImagen() {
        return imagen;
    }

    public void setImagen(String imagen) {
        this.imagen = imagen;
    }

    public BigDecimal getPrecioContado() {
        return precioContado;
    }

    public void setPrecioContado(BigDecimal precioContado) {
        this.precioContado = precioContado;
    }

    public BigDecimal getPrecioCredito() {
        return precioCredito;
    }

    public void setPrecioCredito(BigDecimal precioCredito) {
        this.precioCredito = precioCredito;
    }

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public Boolean getPermiteCuotas() {
        return permiteCuotas;
    }

    public void setPermiteCuotas(Boolean permiteCuotas) {
        this.permiteCuotas = permiteCuotas;
    }

    public Boolean getActivo() {
        return activo;
    }

    public void setActivo(Boolean activo) {
        this.activo = activo;
    }
}
