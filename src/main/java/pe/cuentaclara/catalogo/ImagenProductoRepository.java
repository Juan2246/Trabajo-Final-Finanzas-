package pe.cuentaclara.catalogo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ImagenProductoRepository extends JpaRepository<ImagenProductoEntity, String> {
    List<ImagenProductoEntity> findByCreadaEnBefore(LocalDateTime fecha);
}
