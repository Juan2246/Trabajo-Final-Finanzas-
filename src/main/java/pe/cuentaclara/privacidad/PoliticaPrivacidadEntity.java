package pe.cuentaclara.privacidad;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "privacy_policies")
public class PoliticaPrivacidadEntity {
    @Id
    @Column(name = "store_id")
    private Long tiendaId;

    @Column(name = "retention_days")
    private Integer diasConservacion = 180;

    @Column(name = "contact")
    private String contacto = "";

    @Column(name = "updated_at")
    private LocalDateTime actualizadaEn;

    public Long getTiendaId() {
        return tiendaId;
    }

    public void setTiendaId(Long tiendaId) {
        this.tiendaId = tiendaId;
    }

    public Integer getDiasConservacion() {
        return diasConservacion;
    }

    public void setDiasConservacion(Integer diasConservacion) {
        this.diasConservacion = diasConservacion;
    }

    public String getContacto() {
        return contacto;
    }

    public void setContacto(String contacto) {
        this.contacto = contacto;
    }

    public LocalDateTime getActualizadaEn() {
        return actualizadaEn;
    }

    public void setActualizadaEn(LocalDateTime actualizadaEn) {
        this.actualizadaEn = actualizadaEn;
    }
}
