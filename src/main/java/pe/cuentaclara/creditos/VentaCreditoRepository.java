package pe.cuentaclara.creditos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VentaCreditoRepository extends JpaRepository<VentaCreditoEntity, Long> {
    List<VentaCreditoEntity> findAllByTiendaIdOrderByFechaCompraDescIdDesc(Long tiendaId);

    List<VentaCreditoEntity> findAllByClienteIdOrderByCicloAsc(Long clienteId);
}
