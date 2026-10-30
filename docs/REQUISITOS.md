# Requisitos y evidencias

Referencia: enunciado de SI642 Finanzas e Ingeniería Económica proporcionado para el proyecto. Esta matriz permite revisar la implementación sin confundir las funciones de la aplicación con los documentos de la entrega.

## Aplicación

| ID | Requisito | Implementación y comprobación |
| --- | --- | --- |
| RF01 | Varios negocios de distintos giros en una plataforma | Tiendas independientes por `store_id`; rubro libre. Pruebas de aislamiento y concurrencia en `ApiIntegrationTest.java`. |
| RF02 | Administrador de plataforma distinto al del comercio | Perfiles `platform`, `merchant`, `clientes`; autorización en el servidor. Prueba real de los tres perfiles en `browser_smoke.py`. |
| RF03 | Alta y baja de tiendas y accesos | Sección Tiendas del administrador; baja lógica con revocación de sesiones. |
| RF04 | Acceso y salida seguros | Contraseñas de 12–128 caracteres, PBKDF2, desafío aritmético de un solo uso, bloqueo de intentos, sesión con vencimiento, logout y CSRF. El desafío es el mecanismo de verificación elegido; no se presenta como MFA ni verificación por correo. |
| RF05 | Catálogo genérico de productos y servicios | Nombre, descripción, proveedor, marca, unidad, imagen HTTPS, precios contado/crédito, moneda y permiso de cuotas. Una prestación se registra como servicio con su unidad correspondiente. Alta, edición y baja lógica. |
| RF06 | Clientes y condiciones pactadas | Identificación, contacto, moneda, límite, tasas nominales/efectivas, frecuencia, plazo máximo, corte con hora, día de pago y usuario de consulta. |
| RF07 | Política y consentimiento | Política visible en el acceso y alta; consentimiento obligatorio, fecha y versión guardadas. La baja no borra saldos ni recibos. |
| RF08 | Método francés vencido, meses de 30 días | `MotorFinanciero.calcularPlanPagos` en `finanzas/MotorFinanciero.java`, BigDecimal y última cuota conciliada. Caso numérico A en `DATOS_DE_PRUEBA.md`. |
| RF09 | Gracia total capitalizada antes de las cuotas | Compra del 15/09, corte 20, pago 26: 11 días de gracia; primera cuota el 26/10. Prueba `MotorFinancieroTest` y su matriz de 180 planes. |
| RF10 | Interés por compra hasta el pago al cierre | Factor correspondiente a los días comerciales de cada compra y su tasa guardada. Caso B. |
| RF11 | Compra posterior al corte al ciclo siguiente | Comparación de fecha y hora; pruebas del instante exacto y un segundo después. |
| RF12 | Control de crédito y rechazo de pagos parciales | Verificación del capital pendiente dentro de transacción; pago exacto de la liquidación completa, sin doble cancelación. Pruebas de API y concurrencia. |
| RF13 | Listado de pago generado al corte | Cierres persistidos, proceso al arrancar y cada minuto; recuperación de cortes pendientes si el servidor estuvo apagado. |
| RF14 | Compras por fecha, intereses y mora separada | Estado de cuenta con compras ordenadas, cuotas, capital, interés y mora. Cada mora detalla base vencida, fecha, días, tasa original y monto; exportación CSV e impresión/PDF. |
| RF15 | Moneda y condiciones históricas | PEN/USD separados; cada compra conserva tasas, precios y calendario. Editar el cliente afecta a compras nuevas. |
| RF16 | Base de datos y trazabilidad | SQLite persistente; ventas, cobros, accesos y cambios se registran. Ver `MODELO_DE_DATOS.md`. |
| RF17 | Ayuda en la interfaz | Guía accesible desde la navegación, indicaciones junto a controles y botones de ayuda por campo; teclado, foco y movimiento reducido. |

El inventario/stock y los impuestos se excluyen expresamente de lo obligatorio al final del enunciado. No se añaden operaciones tributarias al cuaderno de crédito.

## Documentación para preparar la entrega

| Parte solicitada | Material disponible |
| --- | --- |
| Introducción y objetivos del sistema | README y `ANALISIS.md`. |
| Student Outcome | Debe usar el texto oficial de la sección y evidencias de cada integrante. No se deduce de los commits. |
| Conceptos, fórmulas y dos juegos de prueba | `FINANZAS.md` y `DATOS_DE_PRUEBA.md`. |
| Marco legal, metodología, ciclo de vida y calidad | `MARCO_Y_METODOLOGIA.md`, con fuentes primarias. |
| Entradas, salidas, datos intermedios e interfaces | `ANALISIS.md`, `API.md` y capturas del recorrido de navegador. |
| Algoritmo y procesos | `ALGORITMOS.md`: pseudocódigo financiero y diagramas de actividades. |
| Modelo lógico/físico y diccionario | `MODELO_DE_DATOS.md` y el DDL ejecutable `src/main/resources/db/schema.sql`. |
| Conectividad, código, ayuda y pruebas | `ARQUITECTURA.md`, `MANUAL.md`, `PRUEBAS.md`, fuentes y GitHub Actions. |
| Presentación y anexos | Guion de demostración en `MANUAL.md`; capturas y PDF de la prueba real como artefactos de CI. |

El informe final requiere además los datos académicos del grupo y sus evidencias personales. Estos documentos técnicos no acreditan por sí solos la presentación, las entrevistas con comercios ni la participación de cada integrante.

## Criterios y límites explícitos

- Base financiera 30/360 definida en `FINANZAS.md`; no se mezcla con días naturales.
- Cortes y pagos 1–31, ajustados al último día disponible. La hora de corte admite horas enteras 0–23 en America/Lima.
- El límite disponible descuenta capital pendiente, incluyendo la gracia capitalizada; no suma intereses futuros dos veces.
- Las bajas preservan historia. La aplicación no ejecuta eliminación automática de datos personales.
- El catálogo es común a los giros comerciales; no incluye módulos específicos de farmacia, peluquería o facturación.
- El acceso académico/local funciona por HTTP. Una exposición pública requiere el proxy HTTPS y la configuración descrita en arquitectura.
- El desafío aritmético no es resistente a automatización avanzada. Las pruebas verifican los controles incluidos, no una certificación de seguridad.
- Los plazos de conservación, el responsable del tratamiento y las tasas legalmente aplicables deben definirse para una operación real; no se inventan para un negocio de demostración.
