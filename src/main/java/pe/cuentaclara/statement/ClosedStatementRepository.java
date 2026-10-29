package pe.cuentaclara.statement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClosedStatementRepository extends JpaRepository<ClosedStatement, Long> {
    boolean existsByCustomerIdAndCycle(Long customerId, LocalDate cycle);

    Optional<ClosedStatement> findByCustomerIdAndCycle(Long customerId, LocalDate cycle);

    List<ClosedStatement> findAllByCustomerIdOrderByCycleDesc(Long customerId);
}
