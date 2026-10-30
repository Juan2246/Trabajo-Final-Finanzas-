package pe.cuentaclara.finanzas;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class MotorFinancieroTest {
    private final MotorFinanciero engine = new MotorFinanciero();
    private final ObjectMapper json =
            new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .registerModule(
                            new SimpleModule()
                                    .addSerializer(BigDecimal.class, ToStringSerializer.instance))
                    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                    .disable(
                            com.fasterxml.jackson.databind.SerializationFeature
                                    .WRITE_DATES_AS_TIMESTAMPS);

    @TestFactory
    Stream<DynamicTest> matchesPreviousVersion() throws Exception {
        JsonNode fixtures =
                json.readTree(getClass().getResourceAsStream("/legacy-finance-cases.json"));
        return StreamSupport.stream(fixtures.spliterator(), false)
                .map(
                        fixture ->
                                DynamicTest.dynamicTest(
                                        fixture.get("purchased").asText()
                                                + " "
                                                + fixture.get("mode").asText()
                                                + " "
                                                + fixture.get("months")
                                                + " "
                                                + fixture.get("terms").get("rate"),
                                        () -> {
                                            CondicionesCredito condiciones =
                                                    json.treeToValue(
                                                            fixture.get("terms"),
                                                            CondicionesCredito.class);
                                            var result =
                                                    engine.calcularPlanPagos(
                                                            new BigDecimal(
                                                                    fixture.get("principal")
                                                                            .asText()),
                                                            LocalDateTime.parse(
                                                                    fixture.get("purchased")
                                                                            .asText()),
                                                            condiciones,
                                                            fixture.get("mode").asText(),
                                                            fixture.get("months").asInt());
                                            assertEquals(
                                                    fixture.get("expected"),
                                                    json.valueToTree(result));
                                        }));
    }

    private CondicionesCredito condiciones() {
        return new CondicionesCredito(
                new BigDecimal("24"),
                "effective",
                360,
                12,
                new BigDecimal("24"),
                "nominal",
                360,
                12,
                12,
                20,
                18,
                26);
    }

    @Test
    void documentedMora() {
        var planPagos =
                engine.calcularPlanPagos(
                        new BigDecimal("500"),
                        LocalDateTime.of(2026, 9, 21, 12, 0),
                        condiciones(),
                        "end",
                        1);
        assertEquals(new BigDecimal("510.57"), planPagos.cuotas().getFirst().importe());
        var charge =
                engine.calcularMora(
                        planPagos.cuotas().getFirst().importe(),
                        LocalDate.of(2026, 10, 26),
                        LocalDate.of(2026, 11, 5),
                        condiciones());
        assertEquals(9, charge.dias());
        assertEquals(new BigDecimal("3.04"), charge.importe());
    }

    @Test
    void sameDayPaymentHasNoMora() {
        var day = LocalDate.of(2026, 10, 26);
        assertEquals(
                new MotorFinanciero.MoraCalculada(new BigDecimal("0.00"), 0),
                engine.calcularMora(new BigDecimal("100"), day, day, condiciones()));
    }

    @Test
    void invalidTermsAreRejected() {
        assertThrows(
                RuntimeException.class,
                () -> engine.factorAcumulacion(new BigDecimal("-1"), "effective", 360, 30, 12));
        assertThrows(
                RuntimeException.class,
                () -> engine.factorAcumulacion(new BigDecimal("12"), "effective", 365, 30, 12));
        assertThrows(
                RuntimeException.class,
                () -> engine.factorAcumulacion(new BigDecimal("12"), "nominal", 360, 30, 7));
        assertThrows(RuntimeException.class, () -> engine.fechaDelMes(LocalDate.now(), 1, 32));
        assertThrows(
                RuntimeException.class,
                () -> engine.diasComerciales(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)));
        assertThrows(
                RuntimeException.class,
                () ->
                        engine.calcularPlanPagos(
                                BigDecimal.ZERO, LocalDateTime.now(), condiciones(), "end", 1));
        assertThrows(
                RuntimeException.class,
                () ->
                        engine.calcularPlanPagos(
                                BigDecimal.ONE, LocalDateTime.now(), condiciones(), "end", 2));
    }

    @Test
    void monthEndRecoversTheAgreedDay() {
        var february = engine.fechaDelMes(LocalDate.of(2026, 1, 31), 1, 31);
        assertEquals(LocalDate.of(2026, 2, 28), february);
        assertEquals(LocalDate.of(2026, 3, 31), engine.fechaDelMes(february, 1, 31));
        assertEquals(
                LocalDate.of(2028, 2, 29), engine.fechaDelMes(LocalDate.of(2028, 1, 31), 1, 31));
    }
}
