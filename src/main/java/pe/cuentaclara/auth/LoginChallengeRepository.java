package pe.cuentaclara.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, String> {
    void deleteByExpiresAtLessThan(Long expiresAt);
}
