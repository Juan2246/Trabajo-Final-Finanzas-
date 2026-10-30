package pe.cuentaclara.tiendas;

import org.springframework.stereotype.Service;

import pe.cuentaclara.acceso.UsuarioActual;
import pe.cuentaclara.clientes.Cliente;
import pe.cuentaclara.clientes.ClienteRepository;
import pe.cuentaclara.comun.ReglaNegocioException;

@Service
public class AccesoTiendaService {
    private final TiendaRepository tiendas;
    private final ClienteRepository clientes;

    public AccesoTiendaService(TiendaRepository tiendas, ClienteRepository clientes) {
        this.tiendas = tiendas;
        this.clientes = clientes;
    }

    public Tienda resolverTienda(UsuarioActual usuario, Long tiendaSeleccionada, boolean required) {
        Long id = usuario.esPlataforma() ? tiendaSeleccionada : usuario.tiendaId();
        if (id == null) {
            if (required) throw new ReglaNegocioException("Primero selecciona una tienda.");
            return null;
        }
        Tienda tienda =
                tiendas.findById(id)
                        .orElseThrow(
                                () ->
                                        new ReglaNegocioException(
                                                "La tienda no está disponible.", 403));
        if (!Boolean.TRUE.equals(tienda.getActivo()))
            throw new ReglaNegocioException("La tienda no está disponible.", 403);
        return tienda;
    }

    public Cliente buscarCliente(Long id, Long tiendaId) {
        Cliente cliente =
                clientes.findById(id)
                        .orElseThrow(
                                () -> new ReglaNegocioException("No se encontró el cliente.", 404));
        if (!cliente.getTienda().getId().equals(tiendaId))
            throw new ReglaNegocioException("No se encontró el registro en esta tienda.", 404);
        return cliente;
    }

    public void validarCliente(UsuarioActual usuario, Long clienteId) {
        if (usuario.esCliente() && !usuario.clienteId().equals(clienteId))
            throw new ReglaNegocioException("Solo puedes consultar tu cuenta.", 403);
    }
}
