package pe.cuentaclara.acceso;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificacionAccesoRepository
        extends JpaRepository<VerificacionAccesoEntity, String> {
    void deleteByVenceEnLessThan(Long venceEn);
}
