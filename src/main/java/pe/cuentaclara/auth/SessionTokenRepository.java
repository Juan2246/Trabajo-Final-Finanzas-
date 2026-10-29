package pe.cuentaclara.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionTokenRepository extends JpaRepository<SessionToken, String> {
    void deleteByUserId(Long userId);

    void deleteByUserStoreId(Long storeId);

    void deleteByExpiresAtLessThan(Long expiresAt);

    List<SessionToken> findAllByUserId(Long userId);
}
