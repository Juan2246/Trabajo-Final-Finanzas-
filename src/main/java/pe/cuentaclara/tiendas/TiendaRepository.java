package pe.cuentaclara.tiendas;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TiendaRepository extends JpaRepository<Tienda, Long> {
    List<Tienda> findAllByOrderByNombreAsc();
}
