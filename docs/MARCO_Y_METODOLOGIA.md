# Marco de referencia y metodología

Fuentes consultadas el 29/10/2026. Las relaciones entre normas y funciones son una aplicación razonada al proyecto; no constituyen certificación ni acreditación de conformidad integral.

## Normativa peruana

| Fuente | Materia | Aplicación al proyecto |
| --- | --- | --- |
| Ley 29733 | Datos personales, finalidad, consentimiento y derechos del titular | Datos de identidad/contacto/crédito, política visible, consentimiento fechado y consulta propia. |
| DS 016-2024-JUS | Reglamento de la Ley 29733; sustituye el de 2013 | Definir responsable, conservación y atención de solicitudes para operación real. Desactivar no equivale a eliminar datos. |
| Ley 29571 | Protección del consumidor e información en relaciones de consumo | Mostrar moneda, precios, tasa, plazo, intereses y mora antes de confirmar y en el estado. |

Fuentes: [Ley 29733](https://cdn.www.gob.pe/uploads/document/file/272360/Ley%20N%C2%BA%2029733.pdf.pdf?v=1618338779), [DS 016-2024-JUS, El Peruano](https://busquedas.elperuano.pe/dispositivo/SE/2349653-1), [Ley 29571, Indecopi](https://www.gob.pe/institucion/indecopi/normas-legales/1244218-29571/). El PDF histórico de la Ley 29733 incluye el antiguo reglamento: para el reglamento se usa el DS 016-2024-JUS, no ese anexo.

La política de demostración explica el uso de datos y la atención mediante la tienda. Una implantación real debe identificar al responsable, definir conservación, revisar las obligaciones del banco de datos y establecer eliminación/anonimización cuando corresponda. No se inventan plazos legales únicos ni se comprueban límites legales de intereses. La transparencia aquí consiste en explicar el crédito al cliente; no se atribuyen a una bodega privada las obligaciones de publicación de una entidad pública.

## Conceptos financieros

- **Capital:** importe financiado al precio de lista.
- **Tasa nominal:** requiere período y frecuencia de capitalización.
- **Tasa efectiva:** expresa acumulación en su período; se transforma por equivalencia de factores.
- **Gracia total:** período sin pago cuyos intereses se capitalizan antes de las cuotas.
- **Anualidad ordinaria:** pagos al final de cada período.
- **Amortización:** parte de la cuota que reduce el capital.
- **Corte:** fecha/hora que asigna compras a un ciclo, distinta del vencimiento.
- **Mora:** interés sobre la obligación vencida por atraso.

La relación saldo/interés/amortización/pago se fundamenta en [OpenStax, Principles of Finance 2e, Loan Amortization](https://openstax.org/books/principles-finance-2e/pages/8-3-loan-amortization). La gracia y las restricciones operativas proceden del enunciado. Las fórmulas y convención exacta 30/360 están en [FINANZAS.md](FINANZAS.md).

## Organización del trabajo

Se propone para la continuidad del grupo una gestión iterativa inspirada en [Scrum Guide 2020](https://scrumguides.org/scrum-guide.html): backlog ordenado, objetivo por iteración, revisión del incremento y mejora del proceso. El grupo debe asignar responsables y registrar sus eventos reales; no se declaran reuniones ni sprints históricos sin evidencia.

| Artefacto | Aplicación concreta |
| --- | --- |
| Backlog | RF01–RF17 de `REQUISITOS.md`, priorizando cálculo, permisos y persistencia. |
| Objetivo de incremento | Registrar cliente, vender, consultar y cobrar sin exceder crédito. |
| Trabajo seleccionado | Issue con requisito, criterio de aceptación, archivos y caso de prueba. |
| Incremento | Código integrado, base reproducible, ayuda y CI. |
| Revisión | Ejecutar caso A o B; registrar resultado y diferencias. |
| Retrospectiva | Problema comprobado y mejora para la siguiente iteración. |

Distribución propuesta: acceso/datos; motor/ventas; cierres/cobros; interfaz/pruebas/documentación. Estas agrupaciones explican dependencias y organizan próximas tareas; no inventan fechas de trabajo.

**Definición de terminado:** requisito trazado, validaciones de servidor, mensajes comprensibles, pruebas del comportamiento, permisos revisados, sin credenciales en Git, documentación actualizada y CI del commit integrado en verde.

## Ciclo de vida y calidad

[ISO/IEC/IEEE 12207:2026](https://www.iso.org/standard/90219.html), publicada en abril de 2026, establece procesos del ciclo de vida y admite aplicación iterativa sin imponer una metodología concreta. Esta asociación se basa en su alcance público; no afirma certificación.

| Etapa | Evidencia del proyecto |
| --- | --- |
| Requisitos | Enunciado y matriz RF01–RF17. |
| Análisis/diseño | Diccionarios, fórmulas, arquitectura, ER y diagramas. |
| Construcción | Servidor, motor, esquema SQL e interfaz. |
| Integración/verificación | Pruebas numéricas, API, concurrencia, navegador y Docker. |
| Validación | Casos resueltos y demostración del dueño y cliente. |
| Operación/mantenimiento | Manual, configuración, respaldo y cambios en GitHub. |
| Retiro | Planificar exportación, conservación necesaria y eliminación autorizada. |

[ISO/IEC 25010:2023](https://www.iso.org/standard/78176.html) aporta un modelo de calidad del producto. Se seleccionan criterios para este alcance: adecuación funcional (casos financieros), fiabilidad (transacciones), seguridad (aislamiento/sesiones), capacidad de interacción (ayuda, teclado y móvil), mantenibilidad (motor separado/pruebas) y flexibilidad (Windows/Linux/Docker). No se afirma evaluar todo el estándar ni rendimiento a escala comercial.

[OWASP ASVS 5.0.0](https://asvs.owasp.org/) orienta la revisión de autenticación, autorización, sesiones, validación y protección de datos. Los controles están en `ARQUITECTURA.md`; sus pruebas no equivalen a una evaluación completa de ASVS.

## Base SQL

SQLite permite una entrega portable sin otro servidor. Se usan claves, referencias, restricciones, índices y transacciones SQL. Dinero en texto decimal y cálculo en Decimal evitan errores binarios; fechas ISO y booleanos 0/1 siguen los tipos disponibles en [SQLite](https://www.sqlite.org/datatype3.html).

No se declara compatibilidad completa con MySQL/PostgreSQL ni se presenta SQLite como una norma ISO. El DDL real es `schema.sql`; migrar requiere adaptar tipos, transacciones y pruebas.

## Bibliografía técnica complementaria

- [Python: Decimal](https://docs.python.org/3/library/decimal.html).
- [Python: sqlite3](https://docs.python.org/3/library/sqlite3.html).
- [Lucide](https://github.com/lucide-icons/lucide), licencia incluida en `static/vendor/`.
- [Public Sans](https://github.com/uswds/public-sans), licencia incluida en `static/fonts/`.
- Enunciado SI642 entregado por el grupo, fuente académica principal del alcance.
