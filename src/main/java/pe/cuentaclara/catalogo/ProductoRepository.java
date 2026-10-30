package pe.cuentaclara.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    List<Producto> findAllByTiendaIdOrderByNombreAsc(Long tiendaId);
}
