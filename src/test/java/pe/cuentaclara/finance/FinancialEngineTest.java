package pe.cuentaclara.finance;

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

class FinancialEngineTest {
    private final FinancialEngine engine = new FinancialEngine();
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
                                            CreditTerms terms =
                                                    json.treeToValue(
                                                            fixture.get("terms"),
                                                            CreditTerms.class);
                                            var result =
                                                    engine.plan(
                                                            new BigDecimal(
                                                                    fixture.get("principal")
                                                                            .asText()),
                                                            LocalDateTime.parse(
                                                                    fixture.get("purchased")
                                                                            .asText()),
                                                            terms,
                                                            fixture.get("mode").asText(),
                                                            fixture.get("months").asInt());
                                            assertEquals(
                                                    fixture.get("expected"),
                                                    json.valueToTree(result));
                                        }));
    }

    private CreditTerms terms() {
        return new CreditTerms(
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
        var plan =
                engine.plan(
                        new BigDecimal("500"),
                        LocalDateTime.of(2026, 9, 21, 12, 0),
                        terms(),
                        "end",
                        1);
        assertEquals(new BigDecimal("510.57"), plan.dues().getFirst().amount());
        var charge =
                engine.lateInterest(
                        plan.dues().getFirst().amount(),
                        LocalDate.of(2026, 10, 26),
                        LocalDate.of(2026, 11, 5),
                        terms());
        assertEquals(9, charge.days());
        assertEquals(new BigDecimal("3.04"), charge.amount());
    }

    @Test
    void sameDayPaymentHasNoMora() {
        var day = LocalDate.of(2026, 10, 26);
        assertEquals(
                new FinancialEngine.LateCharge(new BigDecimal("0.00"), 0),
                engine.lateInterest(new BigDecimal("100"), day, day, terms()));
    }

    @Test
    void invalidTermsAreRejected() {
        assertThrows(
                RuntimeException.class,
                () -> engine.factor(new BigDecimal("-1"), "effective", 360, 30, 12));
        assertThrows(
                RuntimeException.class,
                () -> engine.factor(new BigDecimal("12"), "effective", 365, 30, 12));
        assertThrows(
                RuntimeException.class,
                () -> engine.factor(new BigDecimal("12"), "nominal", 360, 30, 7));
        assertThrows(RuntimeException.class, () -> engine.monthDate(LocalDate.now(), 1, 32));
        assertThrows(
                RuntimeException.class,
                () -> engine.commercialDays(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)));
        assertThrows(
                RuntimeException.class,
                () -> engine.plan(BigDecimal.ZERO, LocalDateTime.now(), terms(), "end", 1));
        assertThrows(
                RuntimeException.class,
                () -> engine.plan(BigDecimal.ONE, LocalDateTime.now(), terms(), "end", 2));
    }

    @Test
    void monthEndRecoversTheAgreedDay() {
        var february = engine.monthDate(LocalDate.of(2026, 1, 31), 1, 31);
        assertEquals(LocalDate.of(2026, 2, 28), february);
        assertEquals(LocalDate.of(2026, 3, 31), engine.monthDate(february, 1, 31));
        assertEquals(LocalDate.of(2028, 2, 29), engine.monthDate(LocalDate.of(2028, 1, 31), 1, 31));
    }
}
