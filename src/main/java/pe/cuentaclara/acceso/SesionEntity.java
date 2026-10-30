package pe.cuentaclara.acceso;

import jakarta.persistence.*;

/** Sesión con vencimiento y token CSRF. */
@Entity
@Table(name = "sessions")
public class SesionEntity {
    @Id
    @Column(name = "token")
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private UsuarioEntity usuario;

    @Column(name = "csrf")
    private String csrf;

    @Column(name = "expires_at")
    private Long venceEn;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UsuarioEntity getUsuario() {
        return usuario;
    }

    public void setUsuario(UsuarioEntity usuario) {
        this.usuario = usuario;
    }

    public String getCsrf() {
        return csrf;
    }

    public void setCsrf(String csrf) {
        this.csrf = csrf;
    }

    public Long getVenceEn() {
        return venceEn;
    }

    public void setVenceEn(Long venceEn) {
        this.venceEn = venceEn;
    }
}
