package pe.cuentaclara.acceso;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificacionAccesoRepository extends JpaRepository<VerificacionAcceso, String> {
    void deleteByVenceEnLessThan(Long venceEn);
}
