package pe.cuentaclara.estados;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EstadoCerradoRepository extends JpaRepository<EstadoCerradoEntity, Long> {
    boolean existsByClienteIdAndCiclo(Long clienteId, LocalDate ciclo);

    Optional<EstadoCerradoEntity> findByClienteIdAndCiclo(Long clienteId, LocalDate ciclo);

    List<EstadoCerradoEntity> findAllByClienteIdOrderByCicloDesc(Long clienteId);
}
