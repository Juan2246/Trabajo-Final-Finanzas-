package pe.cuentaclara.auth;

import jakarta.persistence.*;

/** Intentos fallidos por cuenta y dirección de origen. */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {
    @Id
    @Column(name = "key")
    private String key;

    @Column(name = "failures")
    private Integer failures;

    @Column(name = "blocked_until")
    private Long blockedUntil;

    @Column(name = "updated_at")
    private Long updatedAt;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Integer getFailures() {
        return failures;
    }

    public void setFailures(Integer failures) {
        this.failures = failures;
    }

    public Long getBlockedUntil() {
        return blockedUntil;
    }

    public void setBlockedUntil(Long blockedUntil) {
        this.blockedUntil = blockedUntil;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
