package pe.cuentaclara.acceso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SesionRepository extends JpaRepository<SesionEntity, String> {
    void deleteByUsuarioId(Long usuarioId);

    void deleteByUsuarioTiendaId(Long tiendaId);

    void deleteByVenceEnLessThan(Long venceEn);

    List<SesionEntity> findAllByUsuarioId(Long usuarioId);
}
