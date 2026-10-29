package pe.cuentaclara.credit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    List<SaleItem> findAllBySaleIdOrderByIdAsc(Long saleId);
}
