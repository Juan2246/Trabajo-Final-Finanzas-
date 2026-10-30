package pe.cuentaclara.pagos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagoRepository extends JpaRepository<PagoEntity, Long> {
    List<PagoEntity> findAllByTiendaIdOrderByIdDesc(Long tiendaId);
}
