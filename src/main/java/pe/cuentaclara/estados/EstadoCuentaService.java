package pe.cuentaclara.estados;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.acceso.UsuarioActual;
import pe.cuentaclara.clientes.Cliente;
import pe.cuentaclara.clientes.ClienteRepository;
import pe.cuentaclara.clientes.ClienteSalidaDTO;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.comun.RelojNegocio;
import pe.cuentaclara.comun.SoporteJson;
import pe.cuentaclara.creditos.*;
import pe.cuentaclara.finanzas.MotorFinanciero;
import pe.cuentaclara.tiendas.AccesoTiendaService;
import pe.cuentaclara.tiendas.TiendaSalidaDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class EstadoCuentaService {
    public record ResumenCierreDTO(
            Long id,
            @JsonProperty("cycle") LocalDate ciclo,
            @JsonProperty("generated_at") LocalDateTime fechaGeneracion) {}

    public record EstadoCuentaDTO(
            @JsonProperty("customer") ClienteSalidaDTO cliente,
            @JsonProperty("store") TiendaSalidaDTO tienda,
            @JsonProperty("date") LocalDate fecha,
            @JsonProperty("dues") List<CuotaSalidaDTO> cuotas,
            @JsonProperty("sales") List<VentaSalidaDTO> ventas,
            @JsonProperty("payable") List<CuotaSalidaDTO> exigibles,
            @JsonProperty("payment_date") LocalDate fechaPago,
            @JsonProperty("amount_due") BigDecimal importeExigible,
            @JsonProperty("future_balance") BigDecimal saldoFuturo,
            @JsonProperty("totals") Map<String, BigDecimal> totales,
            @JsonProperty("generated_at") LocalDateTime fechaGeneracion) {}

    private final EstadoCerradoRepository estados;
    private final ClienteRepository clientes;
    private final VentaCreditoRepository ventas;
    private final ConsultaCreditoService consultas;
    private final SaldoCreditoService saldos;
    private final AccesoTiendaService acceso;
    private final MotorFinanciero finanzas;
    private final RelojNegocio reloj;
    private final SoporteJson json;

    public EstadoCuentaService(
            EstadoCerradoRepository estados,
            ClienteRepository clientes,
            VentaCreditoRepository ventas,
            ConsultaCreditoService consultas,
            SaldoCreditoService saldos,
            AccesoTiendaService acceso,
            MotorFinanciero finanzas,
            RelojNegocio reloj,
            SoporteJson json) {
        this.estados = estados;
        this.clientes = clientes;
        this.ventas = ventas;
        this.consultas = consultas;
        this.saldos = saldos;
        this.acceso = acceso;
        this.finanzas = finanzas;
        this.reloj = reloj;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public ObjectNode consultar(
            UsuarioActual usuarioActual,
            Long tiendaSeleccionada,
            Long clienteSeleccionado,
            LocalDate fecha,
            LocalDate ciclo) {
        var tienda = acceso.resolverTienda(usuarioActual, tiendaSeleccionada, true);
        Long clienteId =
                clienteSeleccionado == null && usuarioActual.esCliente()
                        ? usuarioActual.clienteId()
                        : clienteSeleccionado;
        if (clienteId == null) throw new ReglaNegocioException("Selecciona un cliente.");
        acceso.validarCliente(usuarioActual, clienteId);
        Cliente cliente = acceso.buscarCliente(clienteId, tienda.getId());
        LocalDate when = fecha == null ? reloj.hoy() : fecha;
        if (when.isAfter(reloj.hoy()) || when.isBefore(reloj.hoy().minusDays(3650)))
            throw new ReglaNegocioException("La fecha de consulta no es válida.");
        ObjectNode result;
        if (ciclo != null) {
            var closed =
                    estados.findByClienteIdAndCiclo(clienteId, ciclo)
                            .orElseThrow(
                                    () -> new ReglaNegocioException("No existe ese cierre.", 404));
            // Un cierre de la versión anterior se lee tal como quedó guardado.
            result = (ObjectNode) json.leer(closed.getContenidoJson());
            result.put("is_closed", true);
            result.put("cycle", ciclo.toString());
        } else {
            result = json.objeto(generarEstado(cliente, when));
            result.put("is_closed", false);
        }
        var disponible =
                estados.findAllByClienteIdOrderByCicloDesc(clienteId).stream()
                        .map(
                                row ->
                                        new ResumenCierreDTO(
                                                row.getId(),
                                                row.getCiclo(),
                                                row.getFechaGeneracion()))
                        .toList();
        result.set("closed_statements", json.objeto(Map.of("items", disponible)).get("items"));
        return result;
    }

    public EstadoCuentaDTO generarEstado(Cliente cliente, LocalDate when) {
        Long tiendaId = cliente.getTienda().getId();
        var limit = when.equals(reloj.hoy()) ? reloj.ahora() : when.atTime(LocalTime.MAX);
        var compras =
                consultas.ventas(tiendaId, cliente.getId()).stream()
                        .filter(
                                venta ->
                                        !venta.fechaCompra().toLocalDate().isAfter(when)
                                                && !venta.ciclo()
                                                        .atTime(venta.condiciones().horaCorte(), 0)
                                                        .isAfter(limit))
                        .sorted(
                                Comparator.comparing(VentaSalidaDTO::fechaCompra)
                                        .thenComparing(VentaSalidaDTO::id))
                        .toList();
        var ids = compras.stream().map(VentaSalidaDTO::id).collect(Collectors.toSet());
        var cuotas =
                consultas.cuotas(tiendaId, cliente.getId(), when).stream()
                        .filter(cuota -> ids.contains(cuota.ventaId()))
                        .toList();
        var pendientes = cuotas.stream().filter(cuota -> cuota.pagoId() == null).toList();
        var total =
                MotorFinanciero.redondearImporte(
                        pendientes.stream()
                                .map(CuotaSalidaDTO::total)
                                .reduce(BigDecimal.ZERO, BigDecimal::add));
        var fechasCiclo =
                pendientes.stream()
                        .map(CuotaSalidaDTO::fechaVencimiento)
                        .filter(cuota -> !cuota.isBefore(when))
                        .min(LocalDate::compareTo)
                        .orElse(when);
        var exigibles =
                pendientes.stream()
                        .filter(cuota -> !cuota.fechaVencimiento().isAfter(fechasCiclo))
                        .toList();
        var importe =
                MotorFinanciero.redondearImporte(
                        exigibles.stream()
                                .map(CuotaSalidaDTO::total)
                                .reduce(BigDecimal.ZERO, BigDecimal::add));
        var zero = MotorFinanciero.redondearImporte(BigDecimal.ZERO);
        return new EstadoCuentaDTO(
                ClienteSalidaDTO.desde(cliente, saldos.capitalPendiente(cliente.getId())),
                TiendaSalidaDTO.desde(cliente.getTienda()),
                when,
                cuotas,
                compras,
                exigibles,
                fechasCiclo,
                importe,
                total.subtract(importe),
                Map.of(
                        "PEN",
                        cliente.getMoneda().equals("PEN") ? total : zero,
                        "USD",
                        cliente.getMoneda().equals("USD") ? total : zero),
                reloj.ahora());
    }

    @Transactional
    public int cerrarPendientes() {
        int creados = 0;
        for (Cliente cliente : clientes.findAllByActivoTrueAndTiendaActivoTrue()) {
            var historial = ventas.findAllByClienteIdOrderByCicloAsc(cliente.getId());
            if (historial.isEmpty()) continue;
            LocalDate ciclo = historial.getFirst().getCiclo();
            for (int count = 0; count < 121 && !ciclo.isAfter(reloj.hoy()); count++) {
                if (!ciclo.atTime(cliente.getHoraCorte(), 0).isAfter(reloj.ahora())
                        && !estados.existsByClienteIdAndCiclo(cliente.getId(), ciclo)) {
                    EstadoCerrado estado = new EstadoCerrado();
                    estado.setTienda(cliente.getTienda());
                    estado.setCliente(cliente);
                    estado.setCiclo(ciclo);
                    estado.setFechaGeneracion(reloj.ahora());
                    estado.setContenidoJson(json.escribir(generarEstado(cliente, ciclo)));
                    estados.save(estado);
                    creados++;
                }
                ciclo = finanzas.fechaDelMes(ciclo, 1, cliente.getDiaCorte());
            }
        }
        return creados;
    }
}
