package pe.cuentaclara.clientes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClienteRepository extends JpaRepository<ClienteEntity, Long> {
    List<ClienteEntity> findAllByTiendaIdOrderByNombreAsc(Long tiendaId);

    List<ClienteEntity> findAllByActivoTrueAndTiendaActivoTrue();
}
