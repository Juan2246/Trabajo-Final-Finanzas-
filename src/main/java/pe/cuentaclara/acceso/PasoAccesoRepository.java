package pe.cuentaclara.acceso;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasoAccesoRepository extends JpaRepository<PasoAccesoEntity, String> {
    void deleteByUsuarioId(Long usuarioId);

    void deleteByVenceEnLessThan(Long venceEn);
}
