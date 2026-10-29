package pe.cuentaclara.credit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {
    List<Installment> findAllBySaleStoreIdOrderByDueDateAscIdAsc(Long storeId);

    List<Installment> findAllBySaleCustomerIdAndPaymentIsNull(Long customerId);
}
