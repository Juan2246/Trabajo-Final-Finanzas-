package pe.cuentaclara.creditos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VentaCreditoRepository extends JpaRepository<VentaCredito, Long> {
    List<VentaCredito> findAllByTiendaIdOrderByFechaCompraDescIdDesc(Long tiendaId);

    List<VentaCredito> findAllByClienteIdOrderByCicloAsc(Long clienteId);
}
