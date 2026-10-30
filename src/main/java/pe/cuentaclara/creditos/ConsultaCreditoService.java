package pe.cuentaclara.creditos;

import org.springframework.stereotype.Service;

import pe.cuentaclara.comun.SoporteJsonComponent;
import pe.cuentaclara.finanzas.CondicionesCreditoDTO;
import pe.cuentaclara.finanzas.MotorFinancieroService;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class ConsultaCreditoService {
    private final VentaCreditoRepository ventas;
    private final DetalleVentaRepository detalles;
    private final CuotaRepository cuotas;
    private final SoporteJsonComponent json;
    private final MotorFinancieroService finanzas;

    public ConsultaCreditoService(
            VentaCreditoRepository ventas,
            DetalleVentaRepository detalles,
            CuotaRepository cuotas,
            SoporteJsonComponent json,
            MotorFinancieroService finanzas) {
        this.ventas = ventas;
        this.detalles = detalles;
        this.cuotas = cuotas;
        this.json = json;
        this.finanzas = finanzas;
    }

    public CondicionesCreditoDTO condiciones(VentaCreditoEntity venta) {
        return json.convertir(json.leer(venta.getCondicionesJson()), CondicionesCreditoDTO.class);
    }

    public List<VentaSalidaDTO> ventas(Long tiendaId, Long clienteId) {
        return ventas.findAllByTiendaIdOrderByFechaCompraDescIdDesc(tiendaId).stream()
                .filter(venta -> clienteId == null || venta.getCliente().getId().equals(clienteId))
                .map(
                        venta ->
                                new VentaSalidaDTO(
                                        venta.getId(),
                                        tiendaId,
                                        venta.getCliente().getId(),
                                        venta.getCliente().getNombre(),
                                        venta.getFechaCompra(),
                                        venta.getModalidad(),
                                        venta.getMeses(),
                                        venta.getImporte(),
                                        venta.getCapitalFinanciado(),
                                        venta.getDiasGracia(),
                                        venta.getInteresGracia(),
                                        venta.getMoneda(),
                                        venta.getCiclo(),
                                        venta.getFechaCreacion(),
                                        condiciones(venta),
                                        detalles
                                                .findAllByVentaIdOrderByIdAsc(venta.getId())
                                                .stream()
                                                .map(DetalleVentaSalidaDTO::desde)
                                                .toList()))
                .toList();
    }

    public List<CuotaSalidaDTO> cuotas(Long tiendaId, Long clienteId, LocalDate asOf) {
        return cuotas.findAllByVentaTiendaIdOrderByFechaVencimientoAscIdAsc(tiendaId).stream()
                .filter(
                        cuota ->
                                clienteId == null
                                        || cuota.getVenta().getCliente().getId().equals(clienteId))
                .sorted(
                        Comparator.comparing(CuotaEntity::getFechaVencimiento)
                                .thenComparing(d -> d.getVenta().getFechaCompra())
                                .thenComparing(CuotaEntity::getNumero))
                .map(cuota -> detalleCuota(cuota, asOf))
                .toList();
    }

    private CuotaSalidaDTO detalleCuota(CuotaEntity cuota, LocalDate asOf) {
        VentaCreditoEntity venta = cuota.getVenta();
        var fechaPago = cuota.getPago() == null ? null : cuota.getPago().getFechaPago();
        LocalDate fechaEfectiva =
                fechaPago != null && fechaPago.toLocalDate().isBefore(asOf)
                        ? fechaPago.toLocalDate()
                        : asOf;
        CondicionesCreditoDTO condiciones = condiciones(venta);
        var late =
                finanzas.calcularMora(
                        cuota.getImporte(),
                        cuota.getFechaVencimiento(),
                        fechaEfectiva,
                        condiciones);
        boolean pagada = fechaPago != null && !fechaPago.toLocalDate().isAfter(asOf);
        LocalDate fechaCompra = venta.getFechaCompra().toLocalDate();
        int diasInteres =
                venta.getModalidad().equals("end")
                        ? finanzas.diasComerciales(
                                fechaCompra.isAfter(cuota.getFechaVencimiento())
                                        ? cuota.getFechaVencimiento()
                                        : fechaCompra,
                                cuota.getFechaVencimiento())
                        : 30;
        return new CuotaSalidaDTO(
                cuota.getId(),
                venta.getId(),
                cuota.getNumero(),
                cuota.getFechaVencimiento(),
                cuota.getCapital(),
                cuota.getInteres(),
                cuota.getImporte(),
                pagada ? cuota.getPago().getId() : null,
                venta.getCliente().getId(),
                venta.getCliente().getNombre(),
                venta.getMoneda(),
                venta.getFechaCompra(),
                venta.getModalidad(),
                pagada ? fechaPago : null,
                late.importe(),
                late.dias(),
                cuota.getImporte(),
                fechaEfectiva,
                diasInteres,
                MotorFinancieroService.redondearImporte(cuota.getImporte().add(late.importe())),
                condiciones);
    }
}
