package pe.cuentaclara.comun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.validation.Validator;

import org.springframework.stereotype.Component;

@Component
public class SoporteJson {
    private final ObjectMapper mapper;
    private final Validator validator;

    public SoporteJson(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public ObjectNode objeto(Object value) {
        return mapper.valueToTree(value);
    }

    public JsonNode leer(String value) {
        try {
            return mapper.readTree(value);
        } catch (Exception error) {
            throw new IllegalArgumentException("JSON inválido", error);
        }
    }

    public String escribir(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public <T> T convertir(JsonNode value, Class<T> type) {
        return mapper.convertValue(value, type);
    }

    public <T> T combinar(Object current, JsonNode patch, Class<T> type) {
        if (!patch.isObject()) throw new ReglaNegocioException("El contenido debe ser un objeto.");
        ObjectNode merged = objeto(current);
        patch.fields().forEachRemaining(entry -> merged.set(entry.getKey(), entry.getValue()));
        T result = convertir(merged, type);
        var violations = validator.validate(result);
        if (!violations.isEmpty()) {
            var first = violations.iterator().next();
            throw new ReglaNegocioException(
                    "Revisa " + first.getPropertyPath() + ": " + first.getMessage());
        }
        return result;
    }
}
