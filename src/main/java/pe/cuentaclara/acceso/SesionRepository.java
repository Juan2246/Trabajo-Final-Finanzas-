package pe.cuentaclara.acceso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SesionRepository extends JpaRepository<Sesion, String> {
    void deleteByUsuarioId(Long usuarioId);

    void deleteByUsuarioTiendaId(Long tiendaId);

    void deleteByVenceEnLessThan(Long venceEn);

    List<Sesion> findAllByUsuarioId(Long usuarioId);
}
