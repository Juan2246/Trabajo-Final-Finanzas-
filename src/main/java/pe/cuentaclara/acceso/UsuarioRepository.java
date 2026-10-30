package pe.cuentaclara.acceso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {
    Optional<UsuarioEntity> findByUsernameIgnoreCase(String username);

    List<UsuarioEntity> findByClienteId(Long clienteId);
}
