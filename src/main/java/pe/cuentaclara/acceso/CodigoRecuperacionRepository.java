package pe.cuentaclara.acceso;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CodigoRecuperacionRepository
        extends JpaRepository<CodigoRecuperacionEntity, String> {
    long countByUsuarioId(Long usuarioId);

    void deleteByUsuarioId(Long usuarioId);
}
