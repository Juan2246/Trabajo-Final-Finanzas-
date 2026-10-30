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
            "app.retention-enabled=false",
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
                        "second_factors",
                        "login_steps",
                        "recovery_codes",
                        "privacy_policies",
                        "product_images",
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

    @Test
    void mfaUsesEncryptedSecretsAndDoesNotIssueSessionBeforeSecondStep() throws Exception {
        Session otra = login("tienda");
        JsonNode setup =
                json(
                        solicitud(
                                post("/api/security/mfa/setup"),
                                merchant,
                                Map.of("password", "PruebaSegura2026!")));
        String secreto = setup.get("secret").asText();
        assertThat(setup.get("qr").asText()).startsWith("data:image/png;base64,");
        assertThat(
                        database.queryForObject(
                                "SELECT pending_secret FROM second_factors", String.class))
                .doesNotContain(secreto);
        String otp = codigoOtp(secreto, 0);
        JsonNode activacion =
                json(solicitud(post("/api/security/mfa/confirm"), merchant, Map.of("codigo", otp)));
        assertThat(activacion.get("recovery_codes").size()).isEqualTo(8);
        assertThat(solicitud(get("/api/me"), otra, null).getResponse().getStatus()).isEqualTo(401);
        MvcResult primerPaso = primerPasoMfa();
        assertThat(primerPaso.getResponse().getCookie("cc_session")).isNull();
        assertThat(json(primerPaso).get("mfa_required").asBoolean()).isTrue();
        String token = json(primerPaso).get("mfa_token").asText();
        assertThat(database.queryForObject("SELECT token_hash FROM login_steps", String.class))
                .doesNotContain(token);
        assertThat(segundoPasoMfa(token, otp).getResponse().getStatus()).isEqualTo(400);
        MvcResult acceso = segundoPasoMfa(token, codigoOtp(secreto, 1));
        assertThat(acceso.getResponse().getStatus()).isEqualTo(200);
        assertThat(acceso.getResponse().getCookie("cc_session")).isNotNull();
        assertThat(segundoPasoMfa(token, codigoOtp(secreto, 1)).getResponse().getStatus())
                .isEqualTo(401);
        assertThat(count("login_steps")).isZero();
    }

    @Test
    void recoveryCodesAreHashedAndConsumedOnlyOnce() throws Exception {
        var codigos = activarMfa();
        String codigo = codigos.get(0).asText();
        assertThat(database.queryForList("SELECT code_hash FROM recovery_codes", String.class))
                .allMatch(hash -> hash.length() == 64 && !hash.contains(codigo));
        String token = json(primerPasoMfa()).get("mfa_token").asText();
        assertThat(segundoPasoMfa(token, codigo).getResponse().getStatus()).isEqualTo(200);
        assertThat(count("recovery_codes")).isEqualTo(7);
        String otro = json(primerPasoMfa()).get("mfa_token").asText();
        assertThat(segundoPasoMfa(otro, codigo).getResponse().getStatus()).isEqualTo(400);
        assertThat(count("recovery_codes")).isEqualTo(7);
    }

    @Test
    void mfaLimitsAttemptsAcrossLoginTokensAndRejectsExpiredSteps() throws Exception {
        activarMfa();
        String token = json(primerPasoMfa()).get("mfa_token").asText();
        database.update("UPDATE login_steps SET expires_at=0");
        assertThat(segundoPasoMfa(token, "INVALID").getResponse().getStatus()).isEqualTo(401);
        token = json(primerPasoMfa()).get("mfa_token").asText();
        for (int i = 0; i < 5; i++)
            assertThat(segundoPasoMfa(token, "INVALID").getResponse().getStatus()).isEqualTo(400);
        assertThat(segundoPasoMfa(token, "INVALID").getResponse().getStatus()).isEqualTo(429);
        assertThat(database.queryForObject("SELECT attempts FROM second_factors", Integer.class))
                .isEqualTo(5);
        assertThat(primerPasoMfa().getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    void passwordChangeInvalidatesPendingMfaAndDisableRequiresBothFactors() throws Exception {
        var codigos = activarMfa();
        String token = json(primerPasoMfa()).get("mfa_token").asText();
        assertThat(
                        solicitud(
                                        post("/api/password"),
                                        merchant,
                                        Map.of(
                                                "current_password",
                                                "PruebaSegura2026!",
                                                "password",
                                                "OtraClaveSegura2026!"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(segundoPasoMfa(token, codigos.get(0).asText()).getResponse().getStatus())
                .isEqualTo(401);
        assertThat(
                        solicitud(
                                        post("/api/security/mfa/disable"),
                                        merchant,
                                        Map.of(
                                                "password",
                                                "OtraClaveSegura2026!",
                                                "codigo",
                                                "incorrecto"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
        assertThat(
                        solicitud(
                                        post("/api/security/mfa/disable"),
                                        merchant,
                                        Map.of(
                                                "password",
                                                "OtraClaveSegura2026!",
                                                "codigo",
                                                codigos.get(0).asText()))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(count("recovery_codes")).isZero();
        assertThat(
                        json(solicitud(get("/api/security/mfa"), merchant, null))
                                .get("enabled")
                                .asBoolean())
                .isFalse();
    }

    @Test
    void mfaSetupNeedsPasswordCsrfAndFreshConfirmation() throws Exception {
        assertThat(
                        http.perform(
                                        post("/api/security/mfa/setup")
                                                .cookie(merchant.cookie())
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"password\":\"PruebaSegura2026!\"}"))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
        assertThat(
                        solicitud(
                                        post("/api/security/mfa/setup"),
                                        merchant,
                                        Map.of("password", "incorrecta"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
        var setup =
                json(
                        solicitud(
                                post("/api/security/mfa/setup"),
                                merchant,
                                Map.of("password", "PruebaSegura2026!")));
        database.update("UPDATE second_factors SET pending_until=0");
        assertThat(
                        solicitud(
                                        post("/api/security/mfa/confirm"),
                                        merchant,
                                        Map.of(
                                                "codigo",
                                                codigoOtp(setup.get("secret").asText(), 0)))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
        assertThat(
                        json(solicitud(get("/api/security/mfa"), merchant, null))
                                .get("enabled")
                                .asBoolean())
                .isFalse();
    }

    @Test
    void imageUploadValidatesContentAndRequiresStoreOwnership() throws Exception {
        byte[] imagen = getClass().getResourceAsStream("/static/catalogo/leche.jpg").readAllBytes();
        var archivo =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "foto.jpg", "image/jpeg", imagen);
        MvcResult subida =
                http.perform(
                                multipart("/api/product-images")
                                        .file(archivo)
                                        .cookie(merchant.cookie())
                                        .header("X-CSRF-Token", merchant.csrf()))
                        .andReturn();
        assertThat(subida.getResponse().getStatus()).isEqualTo(200);
        String ruta = json(subida).get("image").asText();
        assertThat(
                        solicitud(patch("/api/products/1"), merchant, Map.of("image", ruta))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        MvcResult lectura = solicitud(get(ruta), merchant, null);
        assertThat(lectura.getResponse().getContentType()).isEqualTo("image/jpeg");
        assertThat(lectura.getResponse().getContentAsByteArray()).isNotEmpty();
        assertThat(solicitud(get(ruta), login("cliente"), null).getResponse().getStatus())
                .isEqualTo(200);
        database.update(
                "INSERT INTO stores(id,name,business,address,active)"
                        + " VALUES(2,'Otra','Bazar','',1)");
        database.update("UPDATE product_images SET store_id=2");
        assertThat(solicitud(get(ruta), merchant, null).getResponse().getStatus()).isEqualTo(404);
        assertThat(
                        solicitud(patch("/api/products/2"), merchant, Map.of("image", ruta))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
        var falsa =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "imagen.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes());
        assertThat(
                        http.perform(
                                        multipart("/api/product-images")
                                                .file(falsa)
                                                .cookie(merchant.cookie())
                                                .header("X-CSRF-Token", merchant.csrf()))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
        assertThat(
                        http.perform(
                                        multipart("/api/product-images")
                                                .file(archivo)
                                                .cookie(merchant.cookie()))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
        assertThat(
                        solicitud(patch("/api/products/1"), merchant, Map.of("image", ""))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
    }

    @Test
    void imageUploadRejectsHugeDimensionsAndCustomersCannotUpload() throws Exception {
        var enorme =
                new java.awt.image.BufferedImage(
                        13000, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(enorme, "png", bytes);
        var archivo =
                new org.springframework.mock.web.MockMultipartFile(
                        "file", "grande.png", "image/png", bytes.toByteArray());
        assertThat(
                        http.perform(
                                        multipart("/api/product-images")
                                                .file(archivo)
                                                .cookie(merchant.cookie())
                                                .header("X-CSRF-Token", merchant.csrf()))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
        Session cliente = login("cliente");
        assertThat(
                        http.perform(
                                        multipart("/api/product-images")
                                                .file(archivo)
                                                .cookie(cliente.cookie())
                                                .header("X-CSRF-Token", cliente.csrf()))
                                .andReturn()
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
        assertThat(count("product_images")).isZero();
    }

    @Test
    void privacyRequestBlocksPurchasesAndDebtBlocksDataRemoval() throws Exception {
        Session cliente = login("cliente");
        assertThat(
                        solicitud(
                                        post("/api/privacy/request"),
                                        cliente,
                                        Map.of("password", "PruebaSegura2026!"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        solicitud(
                                        post("/api/sales/preview"),
                                        merchant,
                                        venta("2026-10-28T10:00:00", "1"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
        database.update("UPDATE customers SET erase_requested_at='2020-01-01T00:00:00'");
        database.update("UPDATE sales SET created_at='2020-01-01T00:00:00'");
        var privacidad = json(solicitud(get("/api/privacy"), cliente, null));
        assertThat(privacidad.get("customers").get(0).get("status").asText()).isEqualTo("debt");
        assertThat(
                        json(solicitud(
                                        post("/api/privacy/process"),
                                        merchant,
                                        Map.of("password", "PruebaSegura2026!")))
                                .get("processed")
                                .asInt())
                .isZero();
        assertThat(database.queryForObject("SELECT email FROM customers WHERE id=1", String.class))
                .isEqualTo("maria@example.com");
        assertThat(
                        solicitud(
                                        post("/api/privacy/cancel"),
                                        cliente,
                                        Map.of("password", "PruebaSegura2026!"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        solicitud(
                                        post("/api/sales/preview"),
                                        merchant,
                                        venta("2026-10-28T10:00:00", "1"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
    }

    @Test
    void privacyPurgesPersonalCopiesAndSessionsButKeepsFinancialHistory() throws Exception {
        Session cliente = login("cliente");
        estados.cerrarPendientes();
        assertThat(count("statements")).isPositive();
        database.update(
                "INSERT INTO"
                    + " payments(id,store_id,customer_id,paid_at,base_amount,late_amount,amount,currency)"
                    + " VALUES(1,1,1,'2026-10-29T10:00:00','220','0','220','PEN')");
        database.update("UPDATE dues SET payment_id=1");
        database.update("UPDATE customers SET erase_requested_at='2020-01-01T00:00:00'");
        database.update("UPDATE sales SET created_at='2020-01-01T00:00:00'");
        assertThat(
                        json(solicitud(get("/api/privacy"), cliente, null))
                                .get("customers")
                                .get(0)
                                .get("status")
                                .asText())
                .isEqualTo("waiting");
        database.update("UPDATE payments SET paid_at='2020-01-01T00:00:00'");
        assertThat(
                        json(solicitud(
                                        post("/api/privacy/process"),
                                        merchant,
                                        Map.of("password", "PruebaSegura2026!")))
                                .get("processed")
                                .asInt())
                .isEqualTo(1);
        var registro =
                database.queryForMap(
                        "SELECT name,email,phone,document,active,anonymized_at FROM customers WHERE"
                                + " id=1");
        assertThat(registro.get("name")).isEqualTo("Cliente retirado #1");
        assertThat(registro.get("email")).isEqualTo("");
        assertThat(registro.get("phone")).isEqualTo("");
        assertThat(registro.get("document").toString()).startsWith("retirado-");
        assertThat(registro.get("anonymized_at")).isNotNull();
        assertThat(solicitud(get("/api/me"), cliente, null).getResponse().getStatus())
                .isEqualTo(401);
        assertThat(count("sales")).isEqualTo(2);
        assertThat(count("dues")).isEqualTo(4);
        assertThat(count("payments")).isEqualTo(1);
        for (String copia : database.queryForList("SELECT snapshot FROM statements", String.class))
            assertThat(copia)
                    .doesNotContain("María Flores", "maria@example.com", "999000111", "00000001")
                    .contains("Cliente retirado #1");
        assertThat(
                        database.queryForObject(
                                "SELECT detail FROM audit WHERE action='Cliente registrado'",
                                String.class))
                .isEmpty();
        assertThat(
                        solicitud(patch("/api/customers/1"), merchant, Map.of("active", true))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
        assertThat(
                        json(solicitud(
                                        post("/api/privacy/process"),
                                        merchant,
                                        Map.of("password", "PruebaSegura2026!")))
                                .get("processed")
                                .asInt())
                .isZero();
    }

    @Test
    void privacyExportIsPersonalAndPolicyCannotExtendExistingConsent() throws Exception {
        assertThat(
                        solicitud(post("/api/customers"), merchant, newCustomer())
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        Session cliente = login("cliente");
        JsonNode exportacion = json(solicitud(get("/api/privacy/export"), cliente, null));
        assertThat(exportacion.get("customers").size()).isEqualTo(1);
        assertThat(exportacion.toString())
                .doesNotContain("Cliente de prueba", "password", "csrf", "secret");
        assertThat(
                        solicitud(
                                        post("/api/privacy/policy"),
                                        cliente,
                                        Map.of(
                                                "retention_days",
                                                365,
                                                "contact",
                                                "tienda@example.com",
                                                "password",
                                                "PruebaSegura2026!"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(403);
        assertThat(
                        solicitud(
                                        post("/api/privacy/policy"),
                                        merchant,
                                        Map.of(
                                                "retention_days",
                                                365,
                                                "contact",
                                                "tienda@example.com",
                                                "password",
                                                "PruebaSegura2026!"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        assertThat(
                        json(solicitud(get("/api/privacy"), cliente, null))
                                .get("customers")
                                .get(0)
                                .get("retention_days")
                                .asInt())
                .isEqualTo(180);
        solicitud(
                post("/api/privacy/policy"),
                merchant,
                Map.of("retention_days", 90, "contact", "", "password", "PruebaSegura2026!"));
        solicitud(
                post("/api/privacy/policy"),
                merchant,
                Map.of("retention_days", 365, "contact", "", "password", "PruebaSegura2026!"));
        assertThat(
                        json(solicitud(get("/api/privacy"), cliente, null))
                                .get("customers")
                                .get(0)
                                .get("retention_days")
                                .asInt())
                .isEqualTo(90);
        assertThat(
                        solicitud(
                                        post("/api/privacy/policy"),
                                        merchant,
                                        Map.of(
                                                "retention_days",
                                                1,
                                                "contact",
                                                "",
                                                "password",
                                                "PruebaSegura2026!"))
                                .getResponse()
                                .getStatus())
                .isEqualTo(400);
    }

    @Test
    void inactiveCustomerRetainsDataUntilTheLatestRequestOrDeactivationExpires() throws Exception {
        long id =
                json(solicitud(post("/api/customers"), merchant, newCustomer())).get("id").asLong();
        assertThat(
                        solicitud(patch("/api/customers/" + id), merchant, Map.of("active", false))
                                .getResponse()
                                .getStatus())
                .isEqualTo(200);
        database.update(
                "UPDATE customers SET erase_requested_at='2020-01-01T00:00:00' WHERE id=?", id);
        assertThat(
                        json(solicitud(
                                        post("/api/privacy/process"),
                                        merchant,
                                        Map.of("password", "PruebaSegura2026!")))
                                .get("processed")
                                .asInt())
                .isZero();
        database.update(
                "UPDATE customers SET"
                    + " inactive_since='2020-01-01T00:00:00',erase_requested_at='2026-10-29T10:00:00'"
                    + " WHERE id=?",
                id);
        assertThat(
                        json(solicitud(
                                        post("/api/privacy/process"),
                                        merchant,
                                        Map.of("password", "PruebaSegura2026!")))
                                .get("processed")
                                .asInt())
                .isZero();
        database.update(
                "UPDATE customers SET erase_requested_at='2020-01-01T00:00:00' WHERE id=?", id);
        assertThat(
                        json(solicitud(
                                        post("/api/privacy/process"),
                                        merchant,
                                        Map.of("password", "PruebaSegura2026!")))
                                .get("processed")
                                .asInt())
                .isEqualTo(1);
        assertThat(
                        database.queryForObject(
                                "SELECT email FROM customers WHERE id=?", String.class, id))
                .isEmpty();
    }

    JsonNode activarMfa() throws Exception {
        var setup =
                json(
                        solicitud(
                                post("/api/security/mfa/setup"),
                                merchant,
                                Map.of("password", "PruebaSegura2026!")));
        var resultado =
                solicitud(
                        post("/api/security/mfa/confirm"),
                        merchant,
                        Map.of("codigo", codigoOtp(setup.get("secret").asText(), 0)));
        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        return json(resultado).get("recovery_codes");
    }

    MvcResult primerPasoMfa() throws Exception {
        var desafio = json(http.perform(get("/api/challenge")).andReturn());
        return http.perform(
                        post("/api/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody("tienda", "PruebaSegura2026!", desafio)))
                .andReturn();
    }

    MvcResult segundoPasoMfa(String token, String codigo) throws Exception {
        return http.perform(
                        post("/api/login/mfa")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsString(
                                                Map.of("token", token, "codigo", codigo))))
                .andReturn();
    }

    String codigoOtp(String secreto, int desfase) throws Exception {
        // Referencia HOTP/TOTP de RFC 4226/6238, independiente del generador usado por el servicio.
        long paso = Instant.parse("2026-10-29T17:00:00Z").getEpochSecond() / 30 + desfase;
        var mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(
                new javax.crypto.spec.SecretKeySpec(
                        new org.apache.commons.codec.binary.Base32().decode(secreto), "HmacSHA1"));
        byte[] hash = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(paso).array());
        int offset = hash[hash.length - 1] & 15;
        int binario = java.nio.ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
        return String.format(java.util.Locale.ROOT, "%06d", binario % 1_000_000);
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
