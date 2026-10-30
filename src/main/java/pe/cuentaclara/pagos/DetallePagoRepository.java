package pe.cuentaclara.pagos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DetallePagoRepository extends JpaRepository<DetallePago, Long> {
    List<DetallePago> findAllByPagoId(Long pagoId);
}
