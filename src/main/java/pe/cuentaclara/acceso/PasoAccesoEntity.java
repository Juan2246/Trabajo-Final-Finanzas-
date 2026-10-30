package pe.cuentaclara.acceso;

import jakarta.persistence.*;

@Entity
@Table(name = "login_steps")
public class PasoAccesoEntity {
    @Id
    @Column(name = "token_hash")
    private String id;

    @Column(name = "user_id")
    private Long usuarioId;

    @Column(name = "credential_version")
    private String versionCredencial;

    @Column(name = "expires_at")
    private Long venceEn;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getVersionCredencial() {
        return versionCredencial;
    }

    public void setVersionCredencial(String versionCredencial) {
        this.versionCredencial = versionCredencial;
    }

    public Long getVenceEn() {
        return venceEn;
    }

    public void setVenceEn(Long venceEn) {
        this.venceEn = venceEn;
    }
}
