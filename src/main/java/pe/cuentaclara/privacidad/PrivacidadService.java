package pe.cuentaclara.privacidad;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.cuentaclara.acceso.*;
import pe.cuentaclara.auditoria.AuditoriaService;
import pe.cuentaclara.comun.ReglaNegocioException;
import pe.cuentaclara.comun.RelojNegocioComponent;
import pe.cuentaclara.tiendas.AccesoTiendaService;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PrivacidadService {
    private final JdbcTemplate db;
    private final PoliticaPrivacidadRepository politicas;
    private final AccesoTiendaService acceso;
    private final RelojNegocioComponent reloj;
    private final AuditoriaService auditoria;
    private final ObjectMapper json;
    private final ContrasenaService contrasenas;

    public PrivacidadService(
            JdbcTemplate db,
            PoliticaPrivacidadRepository politicas,
            AccesoTiendaService acceso,
            RelojNegocioComponent reloj,
            AuditoriaService auditoria,
            ObjectMapper json,
            ContrasenaService contrasenas) {
        this.db = db;
        this.politicas = politicas;
        this.acceso = acceso;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.json = json;
        this.contrasenas = contrasenas;
    }

    @Transactional(readOnly = true)
    public int diasConservacion(Long tiendaId) {
        return politicas
                .findById(tiendaId)
                .map(PoliticaPrivacidadEntity::getDiasConservacion)
                .orElse(180);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> consultar(UsuarioActualPrincipal usuario, Long tiendaId) {
        var tienda = acceso.resolverTienda(usuario, tiendaId, true);
        var politica = politicas.findById(tienda.getId()).orElse(null);
        var clientes =
                db.queryForList(
                        "SELECT"
                            + " id,name,active,inactive_since,erase_requested_at,anonymized_at,retention_days"
                            + " FROM customers WHERE store_id=? ORDER BY name",
                        tienda.getId());
        List<Map<String, Object>> estados = new ArrayList<>();
        for (var cliente : clientes) {
            long id = ((Number) cliente.get("id")).longValue();
            if (usuario.esCliente() && id != usuario.clienteId()) continue;
            var estado = estado(cliente, tienda.getId());
            if (usuario.esCliente()
                    || cliente.get("inactive_since") != null
                    || cliente.get("erase_requested_at") != null
                    || cliente.get("anonymized_at") != null) estados.add(estado);
        }
        return Map.of(
                "retention_days",
                diasConservacion(tienda.getId()),
                "contact",
                politica == null ? "" : politica.getContacto(),
                "version",
                "2",
                "customers",
                estados);
    }

    @Transactional
    public void configurar(
            UsuarioActualPrincipal usuario,
            Long tiendaId,
            int dias,
            String contacto,
            String password) {
        if (usuario.esCliente())
            throw new ReglaNegocioException("Solo el comercio configura esta política.", 403);
        var tienda = acceso.resolverTienda(usuario, tiendaId, true);
        confirmar(usuario, password);
        var politica = politicas.findById(tienda.getId()).orElseGet(PoliticaPrivacidadEntity::new);
        politica.setTiendaId(tienda.getId());
        politica.setDiasConservacion(dias);
        politica.setContacto(contacto.strip());
        politica.setActualizadaEn(reloj.ahora());
        politicas.save(politica);
        db.update(
                "UPDATE customers SET retention_days=? WHERE store_id=? AND retention_days>? AND"
                        + " anonymized_at IS NULL",
                dias,
                tienda.getId(),
                dias);
        auditoria.registrar(
                usuario.id(),
                tienda.getId(),
                "Política de conservación actualizada",
                null,
                "Plazo: " + dias + " días");
    }

    @Transactional
    public void solicitar(UsuarioActualPrincipal usuario, boolean cancelar, String password) {
        if (!usuario.esCliente())
            throw new ReglaNegocioException(
                    "Esta opción corresponde a la cuenta del cliente.", 403);
        confirmar(usuario, password);
        if (cancelar)
            db.update(
                    "UPDATE customers SET erase_requested_at=NULL WHERE id=? AND anonymized_at IS"
                            + " NULL",
                    usuario.clienteId());
        else
            db.update(
                    "UPDATE customers SET erase_requested_at=coalesce(erase_requested_at,?) WHERE"
                            + " id=? AND anonymized_at IS NULL",
                    reloj.ahora().toString(),
                    usuario.clienteId());
        auditoria.registrar(
                usuario.id(),
                usuario.tiendaId(),
                cancelar ? "Solicitud de retiro cancelada" : "Retiro de datos solicitado",
                usuario.clienteId(),
                "");
    }

    @Transactional
    public int procesarTienda(UsuarioActualPrincipal usuario, Long tiendaId, String password) {
        if (usuario.esCliente())
            throw new ReglaNegocioException("Solo el comercio administra este proceso.", 403);
        var tienda = acceso.resolverTienda(usuario, tiendaId, true);
        confirmar(usuario, password);
        return procesar(tienda.getId(), usuario.id());
    }

    @Transactional
    public void procesarProgramados() {
        for (Long id : db.queryForList("SELECT id FROM stores", Long.class)) procesar(id, null);
        db.update("DELETE FROM sessions WHERE expires_at<?", reloj.segundosEpoch());
        db.update("DELETE FROM challenges WHERE expires_at<?", reloj.segundosEpoch());
        db.update("DELETE FROM login_steps WHERE expires_at<?", reloj.segundosEpoch());
        db.update(
                "DELETE FROM login_attempts WHERE updated_at<? AND blocked_until<?",
                reloj.segundosEpoch() - 86400,
                reloj.segundosEpoch());
    }

    private Map<String, Object> estado(Map<String, Object> cliente, Long tiendaId) {
        long id = ((Number) cliente.get("id")).longValue();
        long pendientes =
                db.queryForObject(
                        "SELECT count(*) FROM dues d JOIN sales s ON s.id=d.sale_id WHERE"
                                + " s.customer_id=? AND d.payment_id IS NULL",
                        Long.class,
                        id);
        LocalDateTime inicio = fecha(cliente.get("erase_requested_at"));
        LocalDateTime baja = fecha(cliente.get("inactive_since"));
        if (inicio == null || (baja != null && baja.isAfter(inicio))) inicio = baja;
        int dias =
                Math.min(
                        ((Number) cliente.get("retention_days")).intValue(),
                        diasConservacion(tiendaId));
        LocalDateTime vence = null;
        if (inicio != null) {
            for (String consulta :
                    List.of(
                            "SELECT max(created_at) FROM sales WHERE customer_id=?",
                            "SELECT max(paid_at) FROM payments WHERE customer_id=?")) {
                LocalDateTime ultima = fecha(db.queryForObject(consulta, String.class, id));
                if (ultima != null && ultima.isAfter(inicio)) inicio = ultima;
            }
            vence = inicio.plusDays(dias);
        }
        boolean retirado = cliente.get("anonymized_at") != null;
        String estado =
                retirado
                        ? "removed"
                        : inicio == null
                                ? "active"
                                : pendientes > 0
                                        ? "debt"
                                        : vence.isAfter(reloj.ahora()) ? "waiting" : "ready";
        Map<String, Object> salida = new LinkedHashMap<>();
        salida.put("id", id);
        salida.put("name", cliente.get("name"));
        salida.put("status", estado);
        salida.put("retention_days", dias);
        salida.put("pending_dues", pendientes);
        salida.put("requested_at", cliente.get("erase_requested_at"));
        salida.put("scheduled_at", pendientes > 0 || retirado ? null : vence);
        salida.put("removed_at", cliente.get("anonymized_at"));
        return salida;
    }

    private int procesar(Long tiendaId, Long actor) {
        int total = 0;
        for (var cliente :
                db.queryForList(
                        "SELECT"
                            + " id,name,active,inactive_since,erase_requested_at,anonymized_at,retention_days"
                            + " FROM customers WHERE store_id=? AND anonymized_at IS NULL AND"
                            + " (inactive_since IS NOT NULL OR erase_requested_at IS NOT NULL)",
                        tiendaId)) {
            if (!"ready".equals(estado(cliente, tiendaId).get("status"))) continue;
            long id = ((Number) cliente.get("id")).longValue();
            String nombre = "Cliente retirado #" + id;
            String documento = "retirado-" + id + "-" + TokensAleatoriosUtil.token(8);
            for (var usuario :
                    db.queryForList("SELECT id,username FROM users WHERE customer_id=?", id)) {
                Long uid = ((Number) usuario.get("id")).longValue();
                for (String tabla :
                        List.of("sessions", "second_factors", "login_steps", "recovery_codes"))
                    db.update("DELETE FROM " + tabla + " WHERE user_id=?", uid);
                db.update(
                        "DELETE FROM login_attempts WHERE key=?",
                        "user:" + usuario.get("username").toString().toLowerCase(Locale.ROOT));
                db.update(
                        "UPDATE users SET name=?,username=?,password=?,active=0 WHERE id=?",
                        nombre,
                        "retirado_" + TokensAleatoriosUtil.token(16),
                        "!retirado",
                        uid);
            }
            for (var registro :
                    db.queryForList("SELECT id,snapshot FROM statements WHERE customer_id=?", id)) {
                try {
                    JsonNode snapshot = json.readTree(registro.get("snapshot").toString());
                    retirarCopia(snapshot, nombre, documento);
                    db.update(
                            "UPDATE statements SET snapshot=? WHERE id=?",
                            json.writeValueAsString(snapshot),
                            registro.get("id"));
                } catch (Exception error) {
                    throw new IllegalStateException(
                            "No se pudo retirar la copia del estado de cuenta.", error);
                }
            }
            db.update(
                    "UPDATE audit SET detail='' WHERE store_id=? AND entity_id=? AND action IN"
                            + " ('Cliente registrado','Cliente actualizado')",
                    tiendaId,
                    id);
            db.update(
                    "UPDATE customers SET"
                            + " name=?,document=?,email='',phone='',active=0,anonymized_at=? WHERE"
                            + " id=?",
                    nombre,
                    documento,
                    reloj.ahora().toString(),
                    id);
            auditoria.registrar(
                    actor,
                    tiendaId,
                    "Datos personales retirados",
                    id,
                    "Se conserva el historial de importes y pagos.");
            total++;
        }
        return total;
    }

    private void retirarCopia(JsonNode nodo, String nombre, String documento) {
        if (nodo.isObject()) {
            ObjectNode objeto = (ObjectNode) nodo;
            if (objeto.has("customer_name")) objeto.put("customer_name", nombre);
            JsonNode cliente = objeto.get("customer");
            if (cliente instanceof ObjectNode c) {
                c.put("name", nombre);
                c.put("document", documento);
                c.put("email", "");
                c.put("phone", "");
                c.put("active", false);
            }
        }
        for (JsonNode hijo : nodo)
            if (hijo.isContainerNode()) retirarCopia(hijo, nombre, documento);
    }

    private LocalDateTime fecha(Object valor) {
        if (valor == null || valor.toString().isBlank()) return null;
        String texto = valor.toString().replace(' ', 'T');
        return texto.length() == 10
                ? java.time.LocalDate.parse(texto).atStartOfDay()
                : LocalDateTime.parse(texto);
    }

    private void confirmar(UsuarioActualPrincipal usuario, String password) {
        String hash =
                db.queryForObject(
                        "SELECT password FROM users WHERE id=?", String.class, usuario.id());
        if (!contrasenas.verificar(password, hash))
            throw new ReglaNegocioException("La contraseña actual no coincide.");
    }
}
