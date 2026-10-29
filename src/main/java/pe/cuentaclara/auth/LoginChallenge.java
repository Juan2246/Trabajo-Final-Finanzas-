package pe.cuentaclara.auth;

import jakarta.persistence.*;

/** Comprobación de acceso de un solo uso. */
@Entity
@Table(name = "challenges")
public class LoginChallenge {
    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "answer")
    private String answer;

    @Column(name = "expires_at")
    private Long expiresAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
