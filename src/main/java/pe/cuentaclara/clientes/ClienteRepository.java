package pe.cuentaclara.clientes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    List<Cliente> findAllByTiendaIdOrderByNombreAsc(Long tiendaId);

    List<Cliente> findAllByActivoTrueAndTiendaActivoTrue();
}
