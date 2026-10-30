package pe.cuentaclara.pagos;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.acceso.UsuarioActualPrincipal;
import pe.cuentaclara.auditoria.AuditoriaService;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.comun.RelojNegocioComponent;
import pe.cuentaclara.creditos.ConsultaCreditoService;
import pe.cuentaclara.creditos.CuotaRepository;
import pe.cuentaclara.creditos.CuotaSalidaDTO;
import pe.cuentaclara.finanzas.MotorFinancieroService;
import pe.cuentaclara.tiendas.AccesoTiendaService;

import java.math.BigDecimal;

@Service
public class PagoService {
    private final PagoRepository pagos;
    private final DetallePagoRepository lineas;
    private final CuotaRepository cuotas;
    private final ConsultaCreditoService consultas;
    private final AccesoTiendaService acceso;
    private final RelojNegocioComponent reloj;
    private final AuditoriaService auditoria;

    public PagoService(
            PagoRepository pagos,
            DetallePagoRepository lineas,
            CuotaRepository cuotas,
            ConsultaCreditoService consultas,
            AccesoTiendaService acceso,
            RelojNegocioComponent reloj,
            AuditoriaService auditoria) {
        this.pagos = pagos;
        this.lineas = lineas;
        this.cuotas = cuotas;
        this.consultas = consultas;
        this.acceso = acceso;
        this.reloj = reloj;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public LiquidacionPagoDTO simular(
            UsuarioActualPrincipal usuarioActual, Long tiendaSeleccionada, Long clienteId) {
        var tienda = acceso.resolverTienda(usuarioActual, tiendaSeleccionada, true);
        return calcular(tienda.getId(), clienteId);
    }

    public LiquidacionPagoDTO calcular(Long tiendaId, Long clienteId) {
        var cliente = acceso.buscarCliente(clienteId, tiendaId);
        var pendientes =
                consultas.cuotas(tiendaId, clienteId, reloj.hoy()).stream()
                        .filter(cuota -> cuota.pagoId() == null)
                        .toList();
        var cuotas =
                pendientes.stream()
                        .filter(cuota -> !cuota.fechaVencimiento().isAfter(reloj.hoy()))
                        .toList();
        if (cuotas.isEmpty() && !pendientes.isEmpty()) {
            var next =
                    pendientes.stream()
                            .map(CuotaSalidaDTO::fechaVencimiento)
                            .min(java.time.LocalDate::compareTo)
                            .orElseThrow();
            cuotas =
                    pendientes.stream()
                            .filter(cuota -> cuota.fechaVencimiento().equals(next))
                            .toList();
        }
        var base =
                MotorFinancieroService.redondearImporte(
                        cuotas.stream()
                                .map(CuotaSalidaDTO::importe)
                                .reduce(BigDecimal.ZERO, BigDecimal::add));
        var late =
                MotorFinancieroService.redondearImporte(
                        cuotas.stream()
                                .map(CuotaSalidaDTO::importeMora)
                                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return new LiquidacionPagoDTO(
                null,
                cuotas,
                base,
                late,
                MotorFinancieroService.redondearImporte(base.add(late)),
                cliente.getMoneda());
    }

    @Transactional
    public LiquidacionPagoDTO crear(
            UsuarioActualPrincipal usuarioActual,
            Long tiendaSeleccionada,
            PagoEntradaDTO solicitud) {
        var tienda = acceso.resolverTienda(usuarioActual, tiendaSeleccionada, true);
        var cliente = acceso.buscarCliente(solicitud.clienteId(), tienda.getId());
        var quote = calcular(tienda.getId(), cliente.getId());
        if (quote.cuotas().isEmpty())
            throw new ReglaNegocioException("El cliente no tiene obligaciones pendientes.");
        if (solicitud.importe() == null || solicitud.importe().compareTo(quote.importe()) != 0)
            throw new ReglaNegocioException(
                    "El pago debe ser completo: "
                            + quote.importe()
                            + " "
                            + quote.moneda()
                            + ". Actualiza la liquidación.");
        PagoEntity pago = new PagoEntity();
        pago.setTienda(tienda);
        pago.setCliente(cliente);
        pago.setFechaPago(reloj.ahora());
        pago.setImporteBase(quote.importeBase());
        pago.setImporteMora(quote.importeMora());
        pago.setImporte(quote.importe());
        pago.setMoneda(quote.moneda());
        pagos.saveAndFlush(pago);
        for (CuotaSalidaDTO cuota : quote.cuotas()) {
            var obligation = cuotas.findById(cuota.id()).orElseThrow();
            if (obligation.getPago() != null)
                throw new ReglaNegocioException("La cuota ya fue cancelada.", 409);
            DetallePagoEntity linea = new DetallePagoEntity();
            linea.setPago(pago);
            linea.setCuota(obligation);
            linea.setDiasMora(cuota.diasMora());
            linea.setImporteMora(cuota.importeMora());
            lineas.save(linea);
            obligation.setPago(pago);
        }
        auditoria.registrar(
                usuarioActual.id(),
                tienda.getId(),
                "Pago registrado",
                pago.getId(),
                quote.importe() + " " + quote.moneda());
        return new LiquidacionPagoDTO(
                pago.getId(),
                quote.cuotas(),
                quote.importeBase(),
                quote.importeMora(),
                quote.importe(),
                quote.moneda());
    }
}
