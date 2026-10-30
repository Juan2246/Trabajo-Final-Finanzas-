package pe.cuentaclara;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import pe.cuentaclara.estados.EstadoCuentaService;
import pe.cuentaclara.inicio.InicializadorDatosRunner;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Recorre HTTP, seguridad, servicios y SQLite real; nunca usa la base del negocio. */
@SpringBootTest(
        properties = {
            "app.demo=true",
            "app.closing-enabled=false",
            "app.admin-password=PruebaSegura2026!",
            "app.demo-password=PruebaSegura2026!"
        })
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @TempDir static Path directory;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("app.database-path", () -> directory.resolve("integration.db").toString());
    }

    @TestConfiguration
    static class FixedTime {
        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(Instant.parse("2026-10-29T17:00:00Z"), ZoneId.of("America/Lima"));
        }
    }

    @Autowired MockMvc http;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate database;
    @Autowired InicializadorDatosRunner initializer;
    @Autowired EstadoCuentaService estados;

    @AfterAll
    static void releaseSqliteFiles(@Autowired javax.sql.DataSource dataSource) {
        ((com.zaxxer.hikari.HikariDataSource) dataSource).close();
    }

    record Session(Cookie cookie, String csrf) {}

    Session merchant;

    @BeforeEach
    void resetOnlyTheTemporaryDatabase() throws Exception {
        for (String table :
                List.of(
                        "sessions",
                        "audit",
                        "challenges",
                        "login_attempts",
                        "users",
                        "payment_lines",
                        "dues",
                        "payments",
                        "sale_items",
                        "sales",
                        "statements",
                        "products",
                        "customers",
                        "stores")) {
            database.update("DELETE FROM " + table);
        }
        initializer.run(new DefaultApplicationArguments());
        merchant = login("tienda");
    }

    @Test
    void loginSetsProtectedCookieAndLogoutRevokesIt() throws Exception {
        JsonNode verificacion = json(http.perform(get("/api/challenge")).andReturn());
        MvcResult result =
                http.perform(
                                post("/api/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                loginBody(
                                                        "tienda",
                                                        "PruebaSegura2026!",
                                                        verificacion)))
                        .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("HttpOnly", "SameSite=Lax", "Path=/");
        assertThat(solicitud(post("/api/logout"), merchant, Map.of()).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(solicitud(get("/api/me"), merchant, null).getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void consumesChallengeAndPersistsFailedAttemptCounter() throws Exception {
        JsonNode verificacion = json(http.perform(get("/api/challenge")).andReturn());
        String body = loginBody("tienda", "incorrecta", verificacion);
        assertThat(
                        http.perform(
                                        post("/api/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(401);
        assertThat(
                        database.queryForObject(
                                "SELECT count(*) FROM challenges WHERE id=?",
                                Integer.class,
                                verificacion.get("id").asText()))
                .isZero();
        assertThat(
                        database.queryForObject(
                                "SELECT failures FROM login_attempts WHERE key='user:tienda'",
                                Integer.class))
                .isEqualTo(1);
        String retry = loginBody("tienda", "PruebaSegura2026!", verificacion);
        assertThat(
                        http.perform(
                                        post("/api/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(retry))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(401);
    }

    @Test
    void blocksRepeatedLoginFailures() throws Exception {
        for (int i = 0; i < 5; i++) {
            JsonNode verificacion = json(http.perform(get("/api/challenge")).andReturn());
            http.perform(
                    post("/api/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("tienda", "incorrecta", verificacion)));
        }
        JsonNode verificacion = json(http.perform(get("/api/challenge")).andReturn());
        assertThat(
                        http.perform(
                                        post("/api/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        loginBody(
                                                                "tienda",
                                                                "PruebaSegura2026!",
                                                                verificacion)))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(429);
    }

    @Test
    void requiresAuthenticationAndCsrf() throws Exception {
        assertThat(http.perform(get("/api/data")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
        assertThat(
                        http.perform(
                                        post("/api/sales")
                                                .cookie(merchant.cookie())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        mapper.writeValueAsString(
                                                                venta("2026-10-28T12:00:00", "1"))))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
        assertThat(count("sales")).isEqualTo(2);
    }

    @Test
    void customerCannotCreateSalesOrReadAnotherAccount() throws Exception {
        Session cliente = login("cliente");
        assertThat(
                        solicitud(post("/api/sales"), cliente, venta("2026-10-28T12:00:00", "1"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
        assertThat(
                        solicitud(get("/api/statement?customer_id=999"), cliente, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
        JsonNode data = json(solicitud(get("/api/data"), cliente, null));
        assertThat(data.get("customers").size()).isEqualTo(1);
        assertThat(data.get("audit").size()).isZero();
    }

    @Test
    void merchantCannotAccessAnotherStoresProducts() throws Exception {
        database.update("INSERT INTO stores(id,name,business) VALUES(2,'Otra tienda','Panadería')");
        database.update(
                "INSERT INTO products(id,store_id,name,cash_price,credit_price,currency)"
                        + " VALUES(9,2,'Pan','1','1','PEN')");
        ObjectNode body = venta("2026-10-28T12:00:00", "1");
        ((ObjectNode) body.get("items").get(0)).put("product_id", 9);
        assertThat(
                        solicitud(post("/api/sales?store_id=2"), merchant, body)
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
        assertThat(
                        solicitud(patch("/api/products/9"), merchant, Map.of("name", "Cambio"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
        assertThat(count("sales")).isEqualTo(2);
    }

    @Test
    void validatesConsentAndRollsBackDuplicateUserCreation() throws Exception {
        ObjectNode body = newCustomer();
        body.put("consent", false);
        assertThat(solicitud(post("/api/customers"), merchant, body).getResponse().getStatus())
                .isEqualTo(400);
        body.put("consent", true).put("username", "TIENDA");
        assertThat(solicitud(post("/api/customers"), merchant, body).getResponse().getStatus())
                .isIn(400, 409);
        assertThat(count("customers")).isEqualTo(1);
        assertThat(count("users")).isEqualTo(3);
    }

    @Test
    void rejectsInvalidFieldsAndKeepsDecimalPrecision() throws Exception {
        ObjectNode body = newCustomer();
        body.put("cutoff_day", 32);
        assertThat(solicitud(post("/api/customers"), merchant, body).getResponse().getStatus())
                .isEqualTo(400);
        body.put("cutoff_day", 31).put("rate", "24.123456");
        assertThat(solicitud(post("/api/customers"), merchant, body).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(
                        database.queryForObject(
                                "SELECT rate FROM customers WHERE document='12345678'",
                                String.class))
                .isEqualTo("24.123456");
        assertThat(
                        solicitud(get("/api/statement?customer_id=1&date=no-fecha"), merchant, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
    }

    @Test
    void preservesHistoricalTermsAfterCustomerChanges() throws Exception {
        String condiciones =
                database.queryForObject("SELECT terms_json FROM sales WHERE id=1", String.class);
        assertThat(
                        solicitud(
                                        patch("/api/customers/1"),
                                        merchant,
                                        Map.of("rate", "99", "late_rate", "120"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(database.queryForObject("SELECT terms_json FROM sales WHERE id=1", String.class))
                .isEqualTo(condiciones);
        assertThat(
                        solicitud(patch("/api/customers/1"), merchant, Map.of("currency", "USD"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
    }

    @Test
    void revalidatesPreviewWhenPriceChanges() throws Exception {
        ObjectNode body = venta("2026-10-28T12:00:00", "1");
        JsonNode simulacion = json(solicitud(post("/api/sales/preview"), merchant, body));
        ObjectNode acuerdo = mapper.createObjectNode();
        for (String key :
                List.of("amount", "currency", "financed", "grace_days", "grace_interest", "dues"))
            acuerdo.set(key, simulacion.get(key));
        body.set("expected_plan", acuerdo);
        database.update("UPDATE products SET credit_price='5.00' WHERE id=1");
        assertThat(solicitud(post("/api/sales"), merchant, body).getResponse().getStatus())
                .isEqualTo(409);
        assertThat(count("sales")).isEqualTo(2);
    }

    @Test
    void recordsSaleItemsInstallmentsAndAuditAtomically() throws Exception {
        ObjectNode body = venta("2026-10-28T12:00:00", "5");
        body.put("mode", "installments").put("months", 3);
        JsonNode creados = json(solicitud(post("/api/sales"), merchant, body));
        assertThat(creados.has("id")).isTrue();
        assertThat(creados.get("dues").size()).isEqualTo(3);
        assertThat(count("sales")).isEqualTo(3);
        assertThat(count("dues")).isEqualTo(7);
        assertThat(
                        database.queryForObject(
                                "SELECT count(*) FROM audit WHERE action='Compra registrada'",
                                Integer.class))
                .isEqualTo(3);
    }

    @Test
    void rejectsExcessCreditAndForeignCustomerWithoutPartialWrites() throws Exception {
        assertThat(
                        solicitud(
                                        post("/api/sales"),
                                        merchant,
                                        venta("2026-10-28T12:00:00", "1000"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
        ObjectNode body = venta("2026-10-28T12:00:00", "1");
        body.put("customer_id", 999);
        assertThat(solicitud(post("/api/sales"), merchant, body).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(count("sales")).isEqualTo(2);
        assertThat(count("dues")).isEqualTo(4);
    }

    @Test
    void refusesPartialPaymentAndPaysAllDueObligations() throws Exception {
        JsonNode quote =
                json(solicitud(post("/api/payments/preview"), merchant, Map.of("customer_id", 1)));
        assertThat(
                        solicitud(
                                        post("/api/payments"),
                                        merchant,
                                        Map.of("customer_id", 1, "amount", "1.00"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
        assertThat(count("payments")).isZero();
        MvcResult pagada =
                solicitud(
                        post("/api/payments"),
                        merchant,
                        Map.of("customer_id", 1, "amount", quote.get("amount").asText()));
        assertThat(pagada.getResponse().getStatus()).isEqualTo(200);
        assertThat(count("payments")).isEqualTo(1);
        assertThat(count("payment_lines")).isEqualTo(quote.get("dues").size());
        assertThat(new java.math.BigDecimal(json(pagada).get("late_amount").asText()))
                .isGreaterThan(java.math.BigDecimal.ZERO);
    }

    @Test
    void serializesConcurrentPaymentsWithoutChargingTwice() throws Exception {
        JsonNode quote =
                json(solicitud(post("/api/payments/preview"), merchant, Map.of("customer_id", 1)));
        Map<String, Object> body = Map.of("customer_id", 1, "amount", quote.get("amount").asText());
        Callable<Integer> pay =
                () -> solicitud(post("/api/payments"), merchant, body).getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(pay, pay));
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(200, 400);
        }
        assertThat(count("payments")).isEqualTo(1);
    }

    @Test
    void closesPeriodsOnceAndPreservesSnapshotsAfterPayment() throws Exception {
        assertThat(estados.cerrarPendientes()).isPositive();
        String contenidoJson =
                database.queryForObject(
                        "SELECT snapshot FROM statements ORDER BY cycle LIMIT 1", String.class);
        assertThat(estados.cerrarPendientes()).isZero();
        JsonNode quote =
                json(solicitud(post("/api/payments/preview"), merchant, Map.of("customer_id", 1)));
        solicitud(
                post("/api/payments"),
                merchant,
                Map.of("customer_id", 1, "amount", quote.get("amount").asText()));
        assertThat(
                        database.queryForObject(
                                "SELECT snapshot FROM statements ORDER BY cycle LIMIT 1",
                                String.class))
                .isEqualTo(contenidoJson);
        assertThat(
                        solicitud(post("/api/sales"), merchant, venta("2026-08-19T12:00:00", "1"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
    }

    @Test
    void changingPasswordRevokesOtherSessions() throws Exception {
        Session other = login("tienda");
        assertThat(
                        solicitud(
                                        post("/api/password"),
                                        merchant,
                                        Map.of(
                                                "current_password",
                                                "PruebaSegura2026!",
                                                "password",
                                                "NuevaSegura2026!"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(solicitud(get("/api/me"), other, null).getResponse().getStatus()).isEqualTo(401);
        assertThat(solicitud(get("/api/me"), merchant, null).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void concurrentPurchasesCannotExceedTheCreditLimit() throws Exception {
        var pendientes =
                database
                        .queryForList(
                                "SELECT principal FROM dues WHERE payment_id IS NULL", String.class)
                        .stream()
                        .map(java.math.BigDecimal::new)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        database.update(
                "UPDATE customers SET credit_limit=? WHERE id=1",
                pendientes.add(new java.math.BigDecimal("30")).toPlainString());
        Callable<Integer> buy =
                () ->
                        solicitud(post("/api/sales"), merchant, venta("2026-10-28T12:00:00", "5"))
                                .getResponse()
                                .getStatus();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(buy, buy));
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(200, 400);
        }
        assertThat(count("sales")).isEqualTo(3);
    }

    @Test
    void laterPaymentDoesNotRewriteHistoricalDebt() throws Exception {
        String path = "/api/statement?customer_id=1&date=2026-08-26";
        JsonNode before = json(solicitud(get(path), merchant, null));
        JsonNode quote =
                json(solicitud(post("/api/payments/preview"), merchant, Map.of("customer_id", 1)));
        solicitud(
                post("/api/payments"),
                merchant,
                Map.of("customer_id", 1, "amount", quote.get("amount").asText()));
        JsonNode after = json(solicitud(get(path), merchant, null));
        assertThat(after.get("amount_due")).isEqualTo(before.get("amount_due"));
        assertThat(after.get("dues").get(0).get("payment_id").isNull()).isTrue();
    }

    @Test
    void legacyPasswordIsUpgradedWithoutChangingTheAccount() throws Exception {
        String legacy =
                new String(
                                getClass()
                                        .getResourceAsStream("/legacy-password.txt")
                                        .readAllBytes(),
                                java.nio.charset.StandardCharsets.UTF_8)
                        .strip();
        database.update("UPDATE users SET password=? WHERE username='tienda'", legacy);
        login("tienda");
        assertThat(
                        database.queryForObject(
                                "SELECT password FROM users WHERE username='tienda'", String.class))
                .startsWith("{pbkdf2}");
        assertThat(count("users")).isEqualTo(3);
        assertThat(count("sales")).isEqualTo(2);
    }

    @Test
    void inactiveStoreRevokesAccessImmediately() throws Exception {
        database.update("UPDATE stores SET active=0 WHERE id=1");
        assertThat(solicitud(get("/api/data"), merchant, null).getResponse().getStatus())
                .isEqualTo(401);
    }

    int count(String table) {
        return database.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    MvcResult solicitud(MockHttpServletRequestBuilder solicitud, Session sesion, Object body)
            throws Exception {
        solicitud.cookie(sesion.cookie()).header("X-CSRF-Token", sesion.csrf());
        if (body != null)
            solicitud
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(body));
        return http.perform(solicitud).andReturn();
    }

    Session login(String username) throws Exception {
        JsonNode verificacion = json(http.perform(get("/api/challenge")).andReturn());
        MvcResult result =
                http.perform(
                                post("/api/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                loginBody(
                                                        username,
                                                        "PruebaSegura2026!",
                                                        verificacion)))
                        .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return new Session(
                result.getResponse().getCookie("cc_session"), json(result).get("csrf").asText());
    }

    String loginBody(String username, String password, JsonNode verificacion) throws Exception {
        String[] digits =
                verificacion
                        .get("question")
                        .asText()
                        .replaceAll("[^0-9]+", " ")
                        .strip()
                        .split(" +");
        return mapper.writeValueAsString(
                Map.of(
                        "username",
                        username,
                        "password",
                        password,
                        "challenge_id",
                        verificacion.get("id").asText(),
                        "answer",
                        String.valueOf(Integer.parseInt(digits[0]) + Integer.parseInt(digits[1]))));
    }

    ObjectNode venta(String fechaCompra, String cantidad) {
        ObjectNode body = mapper.createObjectNode();
        body.put("customer_id", 1)
                .put("purchased_at", fechaCompra)
                .put("mode", "end")
                .put("months", 1);
        body.putArray("items").addObject().put("product_id", 1).put("quantity", cantidad);
        return body;
    }

    ObjectNode newCustomer() throws Exception {
        return (ObjectNode)
                mapper.readTree(
                        """
{"name":"Cliente de prueba","document":"12345678","email":"prueba@example.com","phone":"999111222",
 "currency":"PEN","credit_limit":"1000","rate":"24","rate_kind":"effective","rate_period":360,"frequency":12,
 "late_rate":"36","late_kind":"nominal","late_period":360,"late_frequency":12,"max_months":12,
 "cutoff_day":20,"cutoff_hour":23,"pay_day":26,"consent":true,"username":"nuevo","password":"PruebaSegura2026!"}
""");
    }
}
