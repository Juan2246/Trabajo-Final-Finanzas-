package pe.cuentaclara.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentLineRepository extends JpaRepository<PaymentLine, Long> {
    List<PaymentLine> findAllByPaymentId(Long paymentId);
}
