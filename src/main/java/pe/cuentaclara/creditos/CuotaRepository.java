package pe.cuentaclara.creditos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CuotaRepository extends JpaRepository<Cuota, Long> {
    List<Cuota> findAllByVentaTiendaIdOrderByFechaVencimientoAscIdAsc(Long tiendaId);

    List<Cuota> findAllByVentaClienteIdAndPagoIsNull(Long clienteId);
}
