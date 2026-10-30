package pe.cuentaclara.pagos;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagoRepository extends JpaRepository<Pago, Long> {
    List<Pago> findAllByTiendaIdOrderByIdDesc(Long tiendaId);
}
