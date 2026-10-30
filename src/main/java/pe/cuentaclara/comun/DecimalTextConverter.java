package pe.cuentaclara.comun;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

/** Conserva el formato de la base existente sin convertir dinero a double. */
@Converter(autoApply = true)
public class DecimalTextConverter implements AttributeConverter<BigDecimal, String> {
    public String convertToDatabaseColumn(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    public BigDecimal convertToEntityAttribute(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
