package pe.cuentaclara.estados;

import jakarta.persistence.*;

import pe.cuentaclara.clientes.ClienteEntity;
import pe.cuentaclara.tiendas.TiendaEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Fotografía inmutable del estado al corte. */
@Entity
@Table(name = "statements")
public class EstadoCerradoEntity {
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
