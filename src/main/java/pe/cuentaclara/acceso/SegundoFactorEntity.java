package pe.cuentaclara.acceso;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "second_factors")
public class SegundoFactorEntity {
    @Id
    @Column(name = "user_id")
    private Long usuarioId;

    @Column(name = "secret")
    private String secretoCifrado;

    @Column(name = "pending_secret")
    private String pendienteCifrado;

    @Column(name = "pending_until")
    private Long pendienteHasta = 0L;

    @Column(name = "last_step")
    private Long ultimoPaso = -1L;

    @Column(name = "enabled_at")
    private LocalDateTime habilitadoDesde;

    @Column(name = "attempts")
    private Integer intentos = 0;

    @Column(name = "blocked_until")
    private Long bloqueadoHasta = 0L;

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getSecretoCifrado() {
        return secretoCifrado;
    }

    public void setSecretoCifrado(String secretoCifrado) {
        this.secretoCifrado = secretoCifrado;
    }

    public String getPendienteCifrado() {
        return pendienteCifrado;
    }

    public void setPendienteCifrado(String pendienteCifrado) {
        this.pendienteCifrado = pendienteCifrado;
    }

    public Long getPendienteHasta() {
        return pendienteHasta;
    }

    public void setPendienteHasta(Long pendienteHasta) {
        this.pendienteHasta = pendienteHasta;
    }

    public Long getUltimoPaso() {
        return ultimoPaso;
    }

    public void setUltimoPaso(Long ultimoPaso) {
        this.ultimoPaso = ultimoPaso;
    }

    public LocalDateTime getHabilitadoDesde() {
        return habilitadoDesde;
    }

    public void setHabilitadoDesde(LocalDateTime habilitadoDesde) {
        this.habilitadoDesde = habilitadoDesde;
    }

    public Integer getIntentos() {
        return intentos;
    }

    public void setIntentos(Integer intentos) {
        this.intentos = intentos;
    }

    public Long getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public void setBloqueadoHasta(Long bloqueadoHasta) {
        this.bloqueadoHasta = bloqueadoHasta;
    }
}
