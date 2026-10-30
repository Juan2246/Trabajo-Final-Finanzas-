package pe.cuentaclara.creditos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetalleVentaRepository extends JpaRepository<DetalleVentaEntity, Long> {
    List<DetalleVentaEntity> findAllByVentaIdOrderByIdAsc(Long ventaId);
}
