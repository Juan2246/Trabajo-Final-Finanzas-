package pe.cuentaclara.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoRepository extends JpaRepository<ProductoEntity, Long> {
    boolean existsByImagen(String imagen);

    List<ProductoEntity> findAllByTiendaIdOrderByNombreAsc(Long tiendaId);
}
