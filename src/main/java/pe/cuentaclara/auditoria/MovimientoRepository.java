package pe.cuentaclara.auditoria;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoRepository extends JpaRepository<Movimiento, Long> {
    List<Movimiento> findTop200ByTiendaIdOrderByIdDesc(Long tiendaId);
}
