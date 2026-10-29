package pe.cuentaclara.credit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditSaleRepository extends JpaRepository<CreditSale, Long> {
    List<CreditSale> findAllByStoreIdOrderByPurchasedAtDescIdDesc(Long storeId);

    List<CreditSale> findAllByCustomerIdOrderByCycleAsc(Long customerId);
}
