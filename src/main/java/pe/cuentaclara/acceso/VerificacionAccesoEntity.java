package pe.cuentaclara.acceso;

import jakarta.persistence.*;

/** Comprobación de acceso de un solo uso. */
@Entity
@Table(name = "challenges")
public class VerificacionAccesoEntity {
    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "answer")
    private String respuesta;

    @Column(name = "expires_at")
    private Long venceEn;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(String respuesta) {
        this.respuesta = respuesta;
    }

    public Long getVenceEn() {
        return venceEn;
    }

    public void setVenceEn(Long venceEn) {
        this.venceEn = venceEn;
    }
}
