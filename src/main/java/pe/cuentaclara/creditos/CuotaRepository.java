package pe.cuentaclara.creditos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuotaRepository extends JpaRepository<CuotaEntity, Long> {
    List<CuotaEntity> findAllByVentaTiendaIdOrderByFechaVencimientoAscIdAsc(Long tiendaId);

    List<CuotaEntity> findAllByVentaClienteIdAndPagoIsNull(Long clienteId);
}
