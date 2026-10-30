package pe.cuentaclara.acceso;

import jakarta.persistence.*;

/** Intentos fallidos por cuenta y dirección de origen. */
@Entity
@Table(name = "login_attempts")
public class IntentoAccesoEntity {
    @Id
    @Column(name = "key")
    private String key;

    @Column(name = "failures")
    private Integer fallos;

    @Column(name = "blocked_until")
    private Long bloqueadoHasta;

    @Column(name = "updated_at")
    private Long actualizadoEn;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Integer getFallos() {
        return fallos;
    }

    public void setFallos(Integer fallos) {
        this.fallos = fallos;
    }

    public Long getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public void setBloqueadoHasta(Long bloqueadoHasta) {
        this.bloqueadoHasta = bloqueadoHasta;
    }

    public Long getActualizadoEn() {
        return actualizadoEn;
    }

    public void setActualizadoEn(Long actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}
