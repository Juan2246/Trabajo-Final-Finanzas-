package pe.cuentaclara.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.validation.Validator;

import org.springframework.stereotype.Component;

@Component
public class JsonSupport {
    private final ObjectMapper mapper;
    private final Validator validator;

    public JsonSupport(ObjectMapper mapper, Validator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public ObjectNode object(Object value) {
        return mapper.valueToTree(value);
    }

    public JsonNode parse(String value) {
        try {
            return mapper.readTree(value);
        } catch (Exception error) {
            throw new IllegalArgumentException("JSON inválido", error);
        }
    }

    public String encode(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public <T> T convert(JsonNode value, Class<T> type) {
        return mapper.convertValue(value, type);
    }

    public <T> T merge(Object current, JsonNode patch, Class<T> type) {
        if (!patch.isObject()) throw new BusinessException("El contenido debe ser un objeto.");
        ObjectNode merged = object(current);
        patch.fields().forEachRemaining(entry -> merged.set(entry.getKey(), entry.getValue()));
        T result = convert(merged, type);
        var violations = validator.validate(result);
        if (!violations.isEmpty()) {
            var first = violations.iterator().next();
            throw new BusinessException(
                    "Revisa " + first.getPropertyPath() + ": " + first.getMessage());
        }
        return result;
    }
}
