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

import pe.cuentaclara.bootstrap.DatabaseInitializer;
import pe.cuentaclara.statement.StatementService;

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
    @Autowired DatabaseInitializer initializer;
    @Autowired StatementService statements;

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
        JsonNode challenge = json(http.perform(get("/api/challenge")).andReturn());
        MvcResult result =
                http.perform(
                                post("/api/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                loginBody(
                                                        "tienda", "PruebaSegura2026!", challenge)))
                        .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("HttpOnly", "SameSite=Lax", "Path=/");
        assertThat(request(post("/api/logout"), merchant, Map.of()).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(request(get("/api/me"), merchant, null).getResponse().getStatus())
                .isEqualTo(401);
    }

    @Test
    void consumesChallengeAndPersistsFailedAttemptCounter() throws Exception {
        JsonNode challenge = json(http.perform(get("/api/challenge")).andReturn());
        String body = loginBody("tienda", "incorrecta", challenge);
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
                                challenge.get("id").asText()))
                .isZero();
        assertThat(
                        database.queryForObject(
                                "SELECT failures FROM login_attempts WHERE key='user:tienda'",
                                Integer.class))
                .isEqualTo(1);
        String retry = loginBody("tienda", "PruebaSegura2026!", challenge);
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
            JsonNode challenge = json(http.perform(get("/api/challenge")).andReturn());
            http.perform(
                    post("/api/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody("tienda", "incorrecta", challenge)));
        }
        JsonNode challenge = json(http.perform(get("/api/challenge")).andReturn());
        assertThat(
                        http.perform(
                                        post("/api/login")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        loginBody(
                                                                "tienda",
                                                                "PruebaSegura2026!",
                                                                challenge)))
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
                                                                sale("2026-10-28T12:00:00", "1"))))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
        assertThat(count("sales")).isEqualTo(2);
    }

    @Test
    void customerCannotCreateSalesOrReadAnotherAccount() throws Exception {
        Session customer = login("cliente");
        assertThat(
                        request(post("/api/sales"), customer, sale("2026-10-28T12:00:00", "1"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
        assertThat(
                        request(get("/api/statement?customer_id=999"), customer, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
        JsonNode data = json(request(get("/api/data"), customer, null));
        assertThat(data.get("customers").size()).isEqualTo(1);
        assertThat(data.get("audit").size()).isZero();
    }

    @Test
    void merchantCannotAccessAnotherStoresProducts() throws Exception {
        database.update("INSERT INTO stores(id,name,business) VALUES(2,'Otra tienda','Panadería')");
        database.update(
                "INSERT INTO products(id,store_id,name,cash_price,credit_price,currency)"
                        + " VALUES(9,2,'Pan','1','1','PEN')");
        ObjectNode body = sale("2026-10-28T12:00:00", "1");
        ((ObjectNode) body.get("items").get(0)).put("product_id", 9);
        assertThat(request(post("/api/sales?store_id=2"), merchant, body).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(
                        request(patch("/api/products/9"), merchant, Map.of("name", "Cambio"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(404);
        assertThat(count("sales")).isEqualTo(2);
    }

    @Test
    void validatesConsentAndRollsBackDuplicateUserCreation() throws Exception {
        ObjectNode body = newCustomer();
        body.put("consent", false);
        assertThat(request(post("/api/customers"), merchant, body).getResponse().getStatus())
                .isEqualTo(400);
        body.put("consent", true).put("username", "TIENDA");
        assertThat(request(post("/api/customers"), merchant, body).getResponse().getStatus())
                .isIn(400, 409);
        assertThat(count("customers")).isEqualTo(1);
        assertThat(count("users")).isEqualTo(3);
    }

    @Test
    void rejectsInvalidFieldsAndKeepsDecimalPrecision() throws Exception {
        ObjectNode body = newCustomer();
        body.put("cutoff_day", 32);
        assertThat(request(post("/api/customers"), merchant, body).getResponse().getStatus())
                .isEqualTo(400);
        body.put("cutoff_day", 31).put("rate", "24.123456");
        assertThat(request(post("/api/customers"), merchant, body).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(
                        database.queryForObject(
                                "SELECT rate FROM customers WHERE document='12345678'",
                                String.class))
                .isEqualTo("24.123456");
        assertThat(
                        request(get("/api/statement?customer_id=1&date=no-fecha"), merchant, null)
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
    }

    @Test
    void preservesHistoricalTermsAfterCustomerChanges() throws Exception {
        String terms =
                database.queryForObject("SELECT terms_json FROM sales WHERE id=1", String.class);
        assertThat(
                        request(
                                        patch("/api/customers/1"),
                                        merchant,
                                        Map.of("rate", "99", "late_rate", "120"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(database.queryForObject("SELECT terms_json FROM sales WHERE id=1", String.class))
                .isEqualTo(terms);
        assertThat(
                        request(patch("/api/customers/1"), merchant, Map.of("currency", "USD"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
    }

    @Test
    void revalidatesPreviewWhenPriceChanges() throws Exception {
        ObjectNode body = sale("2026-10-28T12:00:00", "1");
        JsonNode preview = json(request(post("/api/sales/preview"), merchant, body));
        ObjectNode agreement = mapper.createObjectNode();
        for (String key :
                List.of("amount", "currency", "financed", "grace_days", "grace_interest", "dues"))
            agreement.set(key, preview.get(key));
        body.set("expected_plan", agreement);
        database.update("UPDATE products SET credit_price='5.00' WHERE id=1");
        assertThat(request(post("/api/sales"), merchant, body).getResponse().getStatus())
                .isEqualTo(409);
        assertThat(count("sales")).isEqualTo(2);
    }

    @Test
    void recordsSaleItemsInstallmentsAndAuditAtomically() throws Exception {
        ObjectNode body = sale("2026-10-28T12:00:00", "5");
        body.put("mode", "installments").put("months", 3);
        JsonNode created = json(request(post("/api/sales"), merchant, body));
        assertThat(created.has("id")).isTrue();
        assertThat(created.get("dues").size()).isEqualTo(3);
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
                        request(post("/api/sales"), merchant, sale("2026-10-28T12:00:00", "1000"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
        ObjectNode body = sale("2026-10-28T12:00:00", "1");
        body.put("customer_id", 999);
        assertThat(request(post("/api/sales"), merchant, body).getResponse().getStatus())
                .isEqualTo(404);
        assertThat(count("sales")).isEqualTo(2);
        assertThat(count("dues")).isEqualTo(4);
    }

    @Test
    void refusesPartialPaymentAndPaysAllDueObligations() throws Exception {
        JsonNode quote =
                json(request(post("/api/payments/preview"), merchant, Map.of("customer_id", 1)));
        assertThat(
                        request(
                                        post("/api/payments"),
                                        merchant,
                                        Map.of("customer_id", 1, "amount", "1.00"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
        assertThat(count("payments")).isZero();
        MvcResult paid =
                request(
                        post("/api/payments"),
                        merchant,
                        Map.of("customer_id", 1, "amount", quote.get("amount").asText()));
        assertThat(paid.getResponse().getStatus()).isEqualTo(200);
        assertThat(count("payments")).isEqualTo(1);
        assertThat(count("payment_lines")).isEqualTo(quote.get("dues").size());
        assertThat(new java.math.BigDecimal(json(paid).get("late_amount").asText()))
                .isGreaterThan(java.math.BigDecimal.ZERO);
    }

    @Test
    void serializesConcurrentPaymentsWithoutChargingTwice() throws Exception {
        JsonNode quote =
                json(request(post("/api/payments/preview"), merchant, Map.of("customer_id", 1)));
        Map<String, Object> body = Map.of("customer_id", 1, "amount", quote.get("amount").asText());
        Callable<Integer> pay =
                () -> request(post("/api/payments"), merchant, body).getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var results = pool.invokeAll(List.of(pay, pay));
            assertThat(List.of(results.get(0).get(), results.get(1).get()))
                    .containsExactlyInAnyOrder(200, 400);
        }
        assertThat(count("payments")).isEqualTo(1);
    }

    @Test
    void closesPeriodsOnceAndPreservesSnapshotsAfterPayment() throws Exception {
        assertThat(statements.closeAll()).isPositive();
        String snapshot =
                database.queryForObject(
                        "SELECT snapshot FROM statements ORDER BY cycle LIMIT 1", String.class);
        assertThat(statements.closeAll()).isZero();
        JsonNode quote =
                json(request(post("/api/payments/preview"), merchant, Map.of("customer_id", 1)));
        request(
                post("/api/payments"),
                merchant,
                Map.of("customer_id", 1, "amount", quote.get("amount").asText()));
        assertThat(
                        database.queryForObject(
                                "SELECT snapshot FROM statements ORDER BY cycle LIMIT 1",
                                String.class))
                .isEqualTo(snapshot);
        assertThat(
                        request(post("/api/sales"), merchant, sale("2026-08-19T12:00:00", "1"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
    }

    @Test
    void changingPasswordRevokesOtherSessions() throws Exception {
        Session other = login("tienda");
        assertThat(
                        request(
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
        assertThat(request(get("/api/me"), other, null).getResponse().getStatus()).isEqualTo(401);
        assertThat(request(get("/api/me"), merchant, null).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    void concurrentPurchasesCannotExceedTheCreditLimit() throws Exception {
        var pending =
                database
                        .queryForList(
                                "SELECT principal FROM dues WHERE payment_id IS NULL", String.class)
                        .stream()
                        .map(java.math.BigDecimal::new)
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        database.update(
                "UPDATE customers SET credit_limit=? WHERE id=1",
                pending.add(new java.math.BigDecimal("30")).toPlainString());
        Callable<Integer> buy =
                () ->
                        request(post("/api/sales"), merchant, sale("2026-10-28T12:00:00", "5"))
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
        JsonNode before = json(request(get(path), merchant, null));
        JsonNode quote =
                json(request(post("/api/payments/preview"), merchant, Map.of("customer_id", 1)));
        request(
                post("/api/payments"),
                merchant,
                Map.of("customer_id", 1, "amount", quote.get("amount").asText()));
        JsonNode after = json(request(get(path), merchant, null));
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
        assertThat(request(get("/api/data"), merchant, null).getResponse().getStatus())
                .isEqualTo(401);
    }

    int count(String table) {
        return database.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    JsonNode json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    MvcResult request(MockHttpServletRequestBuilder request, Session session, Object body)
            throws Exception {
        request.cookie(session.cookie()).header("X-CSRF-Token", session.csrf());
        if (body != null)
            request.contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(body));
        return http.perform(request).andReturn();
    }

    Session login(String username) throws Exception {
        JsonNode challenge = json(http.perform(get("/api/challenge")).andReturn());
        MvcResult result =
                http.perform(
                                post("/api/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                loginBody(
                                                        username, "PruebaSegura2026!", challenge)))
                        .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return new Session(
                result.getResponse().getCookie("cc_session"), json(result).get("csrf").asText());
    }

    String loginBody(String username, String password, JsonNode challenge) throws Exception {
        String[] digits =
                challenge.get("question").asText().replaceAll("[^0-9]+", " ").strip().split(" +");
        return mapper.writeValueAsString(
                Map.of(
                        "username",
                        username,
                        "password",
                        password,
                        "challenge_id",
                        challenge.get("id").asText(),
                        "answer",
                        String.valueOf(Integer.parseInt(digits[0]) + Integer.parseInt(digits[1]))));
    }

    ObjectNode sale(String purchasedAt, String quantity) {
        ObjectNode body = mapper.createObjectNode();
        body.put("customer_id", 1)
                .put("purchased_at", purchasedAt)
                .put("mode", "end")
                .put("months", 1);
        body.putArray("items").addObject().put("product_id", 1).put("quantity", quantity);
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
