package pe.cuentaclara.config;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import javax.sql.DataSource;

/** Cambios aditivos para abrir también las bases creadas por versiones anteriores. */
public final class MigracionesDatosUtil {
    private MigracionesDatosUtil() {}

    public static void aplicar(DataSource source, Clock clock) {
        JdbcTemplate db = new JdbcTemplate(source);
        Set<String> columnas =
                db.queryForList("PRAGMA table_info(customers)").stream()
                        .map(row -> row.get("name").toString())
                        .collect(Collectors.toSet());
        for (String columna :
                new String[] {"inactive_since", "erase_requested_at", "anonymized_at"}) {
            if (!columnas.contains(columna))
                db.execute("ALTER TABLE customers ADD COLUMN " + columna + " TEXT");
        }
        if (!columnas.contains("retention_days")) {
            db.execute(
                    "ALTER TABLE customers ADD COLUMN retention_days INTEGER NOT NULL DEFAULT 180");
        }
        // Una baja antigua inicia su plazo ahora; la actualización nunca borra perfiles al
        // arrancar.
        db.update(
                "UPDATE customers SET inactive_since=? WHERE active=0 AND inactive_since IS NULL"
                    + " AND anonymized_at IS NULL",
                LocalDateTime.now(clock).withNano(0).toString());
        db.update(
                "UPDATE products SET image=CASE WHEN lower(name) LIKE '%arroz%' THEN"
                    + " '/catalogo/arroz.jpg' WHEN lower(name) LIKE '%aceite%' THEN"
                    + " '/catalogo/aceite.jpg' WHEN lower(name) LIKE '%leche%' THEN"
                    + " '/catalogo/leche.jpg' WHEN lower(name) LIKE '%reparto%' THEN"
                    + " '/catalogo/reparto.jpg' ELSE '/catalogo/producto.jpg' END WHERE image IS"
                    + " NULL OR trim(image)=''");
    }
}
