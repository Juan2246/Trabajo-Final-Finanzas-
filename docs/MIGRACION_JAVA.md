# Migración a Java

Esta versión sustituye el servidor anterior por Java 21 y Spring Boot. Mantiene el contrato de las operaciones y abre la misma base SQLite. La interfaz vive ahora en `src/main/resources/static/`.

## Antes del cambio

1. Instala un JDK 21 y configura `JAVA_HOME`. Ejecuta `.\mvnw.cmd verify` en Windows o `./mvnw verify` en Linux/macOS.
2. Guarda un respaldo consistente de `data/cuenta.db`. Con la herramienta SQLite: `sqlite3 data/cuenta.db ".backup data/antes-java.db"`. Conserva ese archivo fuera del repositorio.
3. Detén el servidor anterior. No ejecutes ambas versiones sobre la misma base.
4. Conserva `DB_PATH` apuntando al archivo existente y ejecuta `java -jar target/cuenta-clara.jar`.
5. Entra con los usuarios habituales y revisa saldos, ventas, pagos y cierres. El arranque no vuelve a crear datos de demostración si ya existen usuarios.

## Qué se conserva

- Las 14 tablas, sus IDs, relaciones y valores decimales.
- Usuarios, tiendas, clientes, catálogo, ventas, cuotas, pagos y auditoría.
- Las condiciones históricas de cada venta y los JSON originales de los cierres.
- Las contraseñas existentes. Al entrar se verifica el formato anterior y se guarda una derivación PBKDF2 actualizada; el usuario mantiene su contraseña.

Los DTOs pueden añadir campos financieros explicativos. Las compras del estado de cuenta se entregan por fecha ascendente. No cambian los montos del cronograma.

## Validación de la migración

`FinancialEngineTest` contrasta 180 planes completos con resultados de referencia de la versión anterior; además comprueba casos de mora y calendario. `PasswordServiceTest` y `ApiIntegrationTest` prueban la compatibilidad y actualización de claves. La integración utiliza SQLite real con archivos temporales, incluyendo concurrencia, transacciones y cierres.

## Si necesitas volver

Detén Java y restaura el respaldo previo antes de iniciar la versión anterior. Las contraseñas que ya actualizaron su derivación no son legibles por el servidor antiguo. Los movimientos posteriores al respaldo requieren conciliación; no sobrescribas una base con ventas nuevas sin resolverlos.

La versión anterior permanece en el historial de Git. Los archivos Python del servidor y sus pruebas antiguas ya no forman parte del arranque; Python solo se utiliza para los recorridos de navegador/contenedor durante el desarrollo.
