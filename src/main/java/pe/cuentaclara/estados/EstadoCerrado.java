package pe.cuentaclara.estados;

import jakarta.persistence.*;

import pe.cuentaclara.clientes.Cliente;
import pe.cuentaclara.tiendas.Tienda;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Fotografía inmutable del estado al corte. */
@Entity
@Table(name = "statements")
public class EstadoCerrado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Tienda tienda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Cliente cliente;

    @Column(name = "cycle")
    private LocalDate ciclo;

    @Column(name = "snapshot")
    private String contenidoJson;

    @Column(name = "generated_at")
    private LocalDateTime fechaGeneracion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Tienda getTienda() {
        return tienda;
    }

    public void setTienda(Tienda tienda) {
        this.tienda = tienda;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public LocalDate getCiclo() {
        return ciclo;
    }

    public void setCiclo(LocalDate ciclo) {
        this.ciclo = ciclo;
    }

    public String getContenidoJson() {
        return contenidoJson;
    }

    public void setContenidoJson(String contenidoJson) {
        this.contenidoJson = contenidoJson;
    }

    public LocalDateTime getFechaGeneracion() {
        return fechaGeneracion;
    }

    public void setFechaGeneracion(LocalDateTime fechaGeneracion) {
        this.fechaGeneracion = fechaGeneracion;
    }
}
