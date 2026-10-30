package pe.cuentaclara.acceso;

import jakarta.persistence.*;

@Entity
@Table(name = "recovery_codes")
public class CodigoRecuperacionEntity {
    @Id
    @Column(name = "code_hash")
    private String id;

    @Column(name = "user_id")
    private Long usuarioId;

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
}
