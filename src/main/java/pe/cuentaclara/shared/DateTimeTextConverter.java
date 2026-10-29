package pe.cuentaclara.shared;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;

/** Conserva el formato de la base existente sin convertir dinero a double. */
@Converter(autoApply = true)
public class DateTimeTextConverter implements AttributeConverter<LocalDateTime, String> {
    public String convertToDatabaseColumn(LocalDateTime value) {
        return value == null
                ? null
                : value.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    public LocalDateTime convertToEntityAttribute(String value) {
        return value == null ? null : LocalDateTime.parse(value);
    }
}
