package pe.cuentaclara.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByStoreIdOrderByNameAsc(Long storeId);
}
