package pe.cuentaclara.clientes;

import com.fasterxml.jackson.databind.JsonNode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.acceso.UsuarioActualPrincipal;
import pe.cuentaclara.acceso.UsuarioService;
import pe.cuentaclara.auditoria.AuditoriaService;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.comun.RelojNegocioComponent;
import pe.cuentaclara.comun.SoporteJsonComponent;
import pe.cuentaclara.creditos.SaldoCreditoService;
import pe.cuentaclara.finanzas.MotorFinancieroService;
import pe.cuentaclara.tiendas.AccesoTiendaService;
import pe.cuentaclara.tiendas.TiendaEntity;

@Service
public class ClienteService {
    private final pe.cuentaclara.privacidad.PrivacidadService privacidad;
    private final ClienteRepository clientes;
    private final AccesoTiendaService acceso;
    private final UsuarioService usuarios;
    private final SaldoCreditoService saldos;
    private final MotorFinancieroService finanzas;
    private final RelojNegocioComponent reloj;
    private final AuditoriaService auditoria;
    private final SoporteJsonComponent json;

    public ClienteService(
            pe.cuentaclara.privacidad.PrivacidadService privacidad,
            ClienteRepository clientes,
            AccesoTiendaService acceso,
            UsuarioService usuarios,
            SaldoCreditoService saldos,
            MotorFinancieroService finanzas,
            RelojNegocioComponent reloj,
            AuditoriaService auditoria,
            SoporteJsonComponent json) {
        this.privacidad = privacidad;
        this.clientes = clientes;
        this.acceso = acceso;
        this.usuarios = usuarios;
        this.saldos = saldos;
        this.finanzas = finanzas;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.json = json;
    }

    @Transactional
    public Long crear(
            UsuarioActualPrincipal usuarioActual,
            Long tiendaSeleccionada,
            ClienteEntradaDTO solicitud) {
        TiendaEntity tienda = acceso.resolverTienda(usuarioActual, tiendaSeleccionada, true);
        if (!Boolean.TRUE.equals(solicitud.consentimiento()))
            throw new ReglaNegocioException(
                    "Se necesita el consentimiento del cliente para registrar sus datos.");
        ClienteEntity cliente = new ClienteEntity();
        cliente.setTienda(tienda);
        aplicarDatos(cliente, solicitud);
        cliente.setFechaConsentimiento(reloj.ahora());
        cliente.setVersionConsentimiento("2");
        cliente.setDiasConservacion(privacidad.diasConservacion(tienda.getId()));
        clientes.saveAndFlush(cliente);
        usuarios.crear(
                solicitud.username(),
                cliente.getNombre(),
                solicitud.password(),
                "customer",
                tienda,
                cliente);
        auditoria.registrar(
                usuarioActual.id(),
                tienda.getId(),
                "Cliente registrado",
                cliente.getId(),
                cliente.getNombre());
        return cliente.getId();
    }

    @Transactional
    public void actualizar(
            UsuarioActualPrincipal usuarioActual,
            Long tiendaSeleccionada,
            Long id,
            JsonNode patch) {
        TiendaEntity tienda = acceso.resolverTienda(usuarioActual, tiendaSeleccionada, true);
        ClienteEntity cliente = acceso.buscarCliente(id, tienda.getId());
        if (cliente.getAnonimizadoEn() != null)
            throw new ReglaNegocioException(
                    "Los datos retirados no se pueden recuperar ni editar.");
        var pendientes = saldos.capitalPendiente(id);
        ClienteEntradaDTO solicitud =
                json.combinar(
                        ClienteSalidaDTO.desde(cliente, pendientes),
                        patch,
                        ClienteEntradaDTO.class);
        if (pendientes.signum() > 0 && !solicitud.moneda().equals(cliente.getMoneda()))
            throw new ReglaNegocioException("No puedes cambiar la moneda mientras exista deuda.");
        if (solicitud.limiteCredito().compareTo(pendientes) < 0)
            throw new ReglaNegocioException(
                    "El límite no puede ser menor que el capital pendiente.");
        aplicarDatos(cliente, solicitud);
        usuarios.actualizarCliente(cliente, solicitud.password());
        auditoria.registrar(usuarioActual.id(), tienda.getId(), "Cliente actualizado", id, "");
    }

    public void aplicarDatos(ClienteEntity cliente, ClienteEntradaDTO solicitud) {
        int frecuencia = solicitud.frecuencia() == null ? 12 : solicitud.frecuencia();
        int frecuenciaMora = solicitud.frecuenciaMora() == null ? 12 : solicitud.frecuenciaMora();
        finanzas.factorAcumulacion(
                solicitud.tasa(), solicitud.tipoTasa(), solicitud.periodoTasa(), 30, frecuencia);
        finanzas.factorAcumulacion(
                solicitud.tasaMora(),
                solicitud.tipoTasaMora(),
                solicitud.periodoTasaMora(),
                30,
                frecuenciaMora);
        cliente.setNombre(solicitud.nombre().strip());
        cliente.setDocumento(solicitud.documento().strip());
        cliente.setEmail(solicitud.email().strip());
        cliente.setTelefono(solicitud.telefono() == null ? "" : solicitud.telefono().strip());
        cliente.setMoneda(solicitud.moneda());
        cliente.setLimiteCredito(solicitud.limiteCredito());
        cliente.setTasa(solicitud.tasa());
        cliente.setTipoTasa(solicitud.tipoTasa());
        cliente.setPeriodoTasa(solicitud.periodoTasa());
        cliente.setFrecuencia(frecuencia);
        cliente.setTasaMora(solicitud.tasaMora());
        cliente.setTipoTasaMora(solicitud.tipoTasaMora());
        cliente.setPeriodoTasaMora(solicitud.periodoTasaMora());
        cliente.setFrecuenciaMora(frecuenciaMora);
        cliente.setMaximoMeses(solicitud.maximoMeses());
        cliente.setDiaCorte(solicitud.diaCorte());
        cliente.setHoraCorte(solicitud.horaCorte() == null ? 23 : solicitud.horaCorte());
        cliente.setDiaPago(solicitud.diaPago());
        boolean activo = solicitud.activo() == null || solicitud.activo();
        if (!activo && cliente.getInactivoDesde() == null) cliente.setInactivoDesde(reloj.ahora());
        if (activo) cliente.setInactivoDesde(null);
        cliente.setActivo(activo);
    }
}
