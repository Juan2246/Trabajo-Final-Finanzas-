package pe.cuentaclara.resumen;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.acceso.UsuarioActual;
import pe.cuentaclara.auditoria.*;
import pe.cuentaclara.catalogo.*;
import pe.cuentaclara.clientes.*;
import pe.cuentaclara.comun.RelojNegocio;
import pe.cuentaclara.creditos.*;
import pe.cuentaclara.pagos.*;
import pe.cuentaclara.tiendas.*;

import java.util.List;

@Service
public class ResumenService {
    private final AccesoTiendaService acceso;
    private final TiendaRepository tiendas;
    private final ClienteRepository clientes;
    private final ProductoRepository productos;
    private final PagoRepository pagos;
    private final MovimientoRepository auditoria;
    private final ConsultaCreditoService creditos;
    private final SaldoCreditoService saldos;
    private final RelojNegocio reloj;

    public ResumenService(
            AccesoTiendaService acceso,
            TiendaRepository tiendas,
            ClienteRepository clientes,
            ProductoRepository productos,
            PagoRepository pagos,
            MovimientoRepository auditoria,
            ConsultaCreditoService creditos,
            SaldoCreditoService saldos,
            RelojNegocio reloj) {
        this.acceso = acceso;
        this.tiendas = tiendas;
        this.clientes = clientes;
        this.productos = productos;
        this.pagos = pagos;
        this.auditoria = auditoria;
        this.creditos = creditos;
        this.saldos = saldos;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public ResumenSalidaDTO obtenerResumen(UsuarioActual usuarioActual, Long tiendaSeleccionada) {
        Tienda tienda = acceso.resolverTienda(usuarioActual, tiendaSeleccionada, false);
        List<TiendaSalidaDTO> tiendasDisponibles =
                usuarioActual.esPlataforma()
                        ? tiendas.findAllByOrderByNombreAsc().stream()
                                .map(TiendaSalidaDTO::desde)
                                .toList()
                        : List.of();
        if (tienda == null)
            return new ResumenSalidaDTO(
                    usuarioActual.datosPublicos(),
                    null,
                    tiendasDisponibles,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    reloj.hoy());
        Long clienteId = usuarioActual.esCliente() ? usuarioActual.clienteId() : null;
        var visibleCustomers =
                clientes.findAllByTiendaIdOrderByNombreAsc(tienda.getId()).stream()
                        .filter(cliente -> clienteId == null || cliente.getId().equals(clienteId))
                        .map(
                                cliente ->
                                        ClienteSalidaDTO.desde(
                                                cliente, saldos.capitalPendiente(cliente.getId())))
                        .toList();
        var visibleProducts =
                usuarioActual.esCliente()
                        ? List.<ProductoSalidaDTO>of()
                        : productos.findAllByTiendaIdOrderByNombreAsc(tienda.getId()).stream()
                                .map(ProductoSalidaDTO::desde)
                                .toList();
        var visibleAudit =
                usuarioActual.esCliente()
                        ? List.<MovimientoSalidaDTO>of()
                        : auditoria.findTop200ByTiendaIdOrderByIdDesc(tienda.getId()).stream()
                                .map(MovimientoSalidaDTO::desde)
                                .toList();
        var visiblePayments =
                pagos.findAllByTiendaIdOrderByIdDesc(tienda.getId()).stream()
                        .filter(
                                pago ->
                                        clienteId == null
                                                || pago.getCliente().getId().equals(clienteId))
                        .map(PagoSalidaDTO::desde)
                        .toList();
        return new ResumenSalidaDTO(
                usuarioActual.datosPublicos(),
                TiendaSalidaDTO.desde(tienda),
                tiendasDisponibles,
                visibleCustomers,
                visibleProducts,
                creditos.ventas(tienda.getId(), clienteId),
                creditos.cuotas(tienda.getId(), clienteId, reloj.hoy()),
                visiblePayments,
                visibleAudit,
                reloj.hoy());
    }
}
