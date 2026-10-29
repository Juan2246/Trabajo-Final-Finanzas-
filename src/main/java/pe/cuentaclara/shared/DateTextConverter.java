package pe.cuentaclara.shared;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

/** Conserva el formato de la base existente sin convertir dinero a double. */
@Converter(autoApply = true)
public class DateTextConverter implements AttributeConverter<LocalDate, String> {
    public String convertToDatabaseColumn(LocalDate value) {
        return value == null ? null : value.toString();
    }

    public LocalDate convertToEntityAttribute(String value) {
        return value == null ? null : LocalDate.parse(value);
    }
}
