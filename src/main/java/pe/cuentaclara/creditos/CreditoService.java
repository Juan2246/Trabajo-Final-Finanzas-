package pe.cuentaclara.creditos;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.acceso.UsuarioActualPrincipal;
import pe.cuentaclara.auditoria.AuditoriaService;
import pe.cuentaclara.catalogo.ProductoEntity;
import pe.cuentaclara.catalogo.ProductoService;
import pe.cuentaclara.clientes.ClienteEntity;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.comun.RelojNegocioComponent;
import pe.cuentaclara.comun.SoporteJsonComponent;
import pe.cuentaclara.estados.EstadoCerradoRepository;
import pe.cuentaclara.finanzas.CondicionesCreditoDTO;
import pe.cuentaclara.finanzas.MotorFinancieroService;
import pe.cuentaclara.finanzas.PlanPagosDTO;
import pe.cuentaclara.tiendas.AccesoTiendaService;
import pe.cuentaclara.tiendas.TiendaEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
public class CreditoService {
    private final VentaCreditoRepository ventas;
    private final DetalleVentaRepository detalles;
    private final CuotaRepository cuotas;
    private final AccesoTiendaService acceso;
    private final ProductoService productos;
    private final SaldoCreditoService saldos;
    private final MotorFinancieroService finanzas;
    private final EstadoCerradoRepository estados;
    private final AuditoriaService auditoria;
    private final RelojNegocioComponent reloj;
    private final SoporteJsonComponent json;

    public CreditoService(
            VentaCreditoRepository ventas,
            DetalleVentaRepository detalles,
            CuotaRepository cuotas,
            AccesoTiendaService acceso,
            ProductoService productos,
            SaldoCreditoService saldos,
            MotorFinancieroService finanzas,
            EstadoCerradoRepository estados,
            AuditoriaService auditoria,
            RelojNegocioComponent reloj,
            SoporteJsonComponent json) {
        this.ventas = ventas;
        this.detalles = detalles;
        this.cuotas = cuotas;
        this.acceso = acceso;
        this.productos = productos;
        this.saldos = saldos;
        this.finanzas = finanzas;
        this.estados = estados;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.json = json;
    }

    private record CalculoVenta(
            ClienteEntity cliente,
            PlanPagosDTO planPagos,
            List<DetalleVentaSalidaDTO> lineas,
            BigDecimal importe,
            BigDecimal disponible,
            int meses) {
        SimulacionVentaDTO simular(Long id, VentaEntradaDTO solicitud) {
            return SimulacionVentaDTO.desde(
                    id,
                    planPagos,
                    importe,
                    cliente.getMoneda(),
                    disponible,
                    lineas,
                    solicitud,
                    meses);
        }
    }

    @Transactional(readOnly = true)
    public SimulacionVentaDTO simular(
            UsuarioActualPrincipal usuarioActual,
            Long tiendaSeleccionada,
            VentaEntradaDTO solicitud) {
        TiendaEntity tienda = acceso.resolverTienda(usuarioActual, tiendaSeleccionada, true);
        return calcular(tienda, solicitud).simular(null, solicitud);
    }

    @Transactional
    public SimulacionVentaDTO crear(
            UsuarioActualPrincipal usuarioActual,
            Long tiendaSeleccionada,
            VentaEntradaDTO solicitud) {
        TiendaEntity tienda = acceso.resolverTienda(usuarioActual, tiendaSeleccionada, true);
        CalculoVenta calculo = calcular(tienda, solicitud);
        SimulacionVentaDTO simulacion = calculo.simular(null, solicitud);
        if (solicitud.planAceptado() != null) {
            var acuerdo =
                    json.objeto(
                            Map.of(
                                    "amount",
                                    simulacion.importe(),
                                    "currency",
                                    simulacion.moneda(),
                                    "financed",
                                    simulacion.capitalFinanciado(),
                                    "grace_days",
                                    simulacion.diasGracia(),
                                    "grace_interest",
                                    simulacion.interesGracia(),
                                    "dues",
                                    simulacion.cuotas()));
            if (!acuerdo.equals(solicitud.planAceptado()))
                throw new ReglaNegocioException(
                        "El acuerdo cambió desde la simulación. Vuelve a calcular las cuotas.",
                        409);
        }
        VentaCreditoEntity venta = new VentaCreditoEntity();
        venta.setTienda(tienda);
        venta.setCliente(calculo.cliente());
        venta.setFechaCompra(solicitud.fechaCompra());
        venta.setModalidad(solicitud.modalidad());
        venta.setMeses(calculo.meses());
        venta.setImporte(calculo.importe());
        venta.setCapitalFinanciado(calculo.planPagos().capitalFinanciado());
        venta.setDiasGracia(calculo.planPagos().diasGracia());
        venta.setInteresGracia(calculo.planPagos().interesGracia());
        venta.setMoneda(calculo.cliente().getMoneda());
        venta.setCondicionesJson(json.escribir(CondicionesCreditoDTO.desde(calculo.cliente())));
        venta.setCiclo(calculo.planPagos().ciclo());
        venta.setFechaCreacion(reloj.ahora());
        ventas.saveAndFlush(venta);
        for (DetalleVentaSalidaDTO linea : calculo.lineas()) {
            DetalleVentaEntity detalle = new DetalleVentaEntity();
            detalle.setVenta(venta);
            detalle.setProducto(productos.buscar(linea.productoId(), tienda.getId()));
            detalle.setNombre(linea.nombre());
            detalle.setCantidad(linea.cantidad());
            detalle.setPrecioUnitario(linea.precioUnitario());
            detalle.setSubtotal(linea.subtotal());
            detalles.save(detalle);
        }
        for (var calculada : calculo.planPagos().cuotas()) {
            CuotaEntity cuota = new CuotaEntity();
            cuota.setVenta(venta);
            cuota.setNumero(calculada.numero());
            cuota.setFechaVencimiento(calculada.fecha());
            cuota.setCapital(calculada.capital());
            cuota.setInteres(calculada.interes());
            cuota.setImporte(calculada.importe());
            cuotas.save(cuota);
        }
        auditoria.registrar(
                usuarioActual.id(),
                tienda.getId(),
                "Compra registrada",
                venta.getId(),
                venta.getImporte() + " " + venta.getMoneda());
        return calculo.simular(venta.getId(), solicitud);
    }

    private CalculoVenta calcular(TiendaEntity tienda, VentaEntradaDTO solicitud) {
        ClienteEntity cliente = acceso.buscarCliente(solicitud.clienteId(), tienda.getId());
        if (cliente.getRetiroSolicitadoEn() != null)
            throw new ReglaNegocioException(
                    "El cliente ha solicitado retirar sus datos. No se permiten nuevas compras.");
        if (!cliente.getActivo()) throw new ReglaNegocioException("El cliente está desactivado.");
        if (solicitud.fechaCompra().isAfter(reloj.ahora())
                || solicitud.fechaCompra().isBefore(reloj.ahora().minusDays(3650)))
            throw new ReglaNegocioException(
                    "La compra debe estar entre hoy y los últimos 10 años, en hora de Lima.");
        int meses =
                solicitud.modalidad().equals("installments")
                        ? (solicitud.meses() == null ? 1 : solicitud.meses())
                        : 1;
        List<DetalleVentaSalidaDTO> lineas = new ArrayList<>();
        var seen = new HashSet<Long>();
        BigDecimal total = BigDecimal.ZERO;
        for (VentaEntradaDTO.LineaVentaDTO input : solicitud.detalles()) {
            ProductoEntity producto = productos.buscar(input.productoId(), tienda.getId());
            if (!seen.add(producto.getId()))
                throw new ReglaNegocioException("Agrupa las cantidades de un mismo producto.");
            if (!producto.getActivo() || !producto.getMoneda().equals(cliente.getMoneda()))
                throw new ReglaNegocioException(
                        "El producto debe estar activo y tener la moneda del cliente.");
            if (solicitud.modalidad().equals("installments") && !producto.getPermiteCuotas())
                throw new ReglaNegocioException("Este producto solo se puede pagar al cierre.");
            BigDecimal subtotal =
                    MotorFinancieroService.redondearImporte(
                            input.cantidad().multiply(producto.getPrecioCredito()));
            if (subtotal.signum() <= 0)
                throw new ReglaNegocioException("El subtotal debe ser mayor que cero.");
            total = total.add(subtotal);
            lineas.add(
                    new DetalleVentaSalidaDTO(
                            null,
                            null,
                            producto.getId(),
                            producto.getNombre(),
                            input.cantidad(),
                            producto.getPrecioCredito(),
                            subtotal));
        }
        if (total.compareTo(new BigDecimal("10000000")) > 0)
            throw new ReglaNegocioException("El importe supera el máximo permitido.");
        PlanPagosDTO planPagos =
                finanzas.calcularPlanPagos(
                        total,
                        solicitud.fechaCompra(),
                        CondicionesCreditoDTO.desde(cliente),
                        solicitud.modalidad(),
                        meses);
        BigDecimal disponible =
                MotorFinancieroService.redondearImporte(
                        cliente.getLimiteCredito()
                                .subtract(saldos.capitalPendiente(cliente.getId())));
        if (planPagos.capitalFinanciado().compareTo(disponible) > 0)
            throw new ReglaNegocioException(
                    "La compra supera el crédito disponible ("
                            + disponible
                            + " "
                            + cliente.getMoneda()
                            + ").");
        if (estados.existsByClienteIdAndCiclo(cliente.getId(), planPagos.ciclo()))
            throw new ReglaNegocioException(
                    "Ese período ya está cerrado. Registra la compra en un período abierto.");
        return new CalculoVenta(
                cliente,
                planPagos,
                List.copyOf(lineas),
                MotorFinancieroService.redondearImporte(total),
                disponible,
                meses);
    }
}
