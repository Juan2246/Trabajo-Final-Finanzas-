# Análisis del sistema

## Problema y objetivo

Un comercio que registra fiados en papel necesita identificar qué compró cada cliente, cuánto debe pagar, en qué fecha y cómo se calculó el interés. Cuenta Clara mantiene ese historial por tienda y permite al dueño registrar y cobrar créditos; el cliente consulta su cuenta con su propio acceso.

Objetivos verificables: conservar operaciones en una base SQL, separar negocios y perfiles, calcular planes franceses con gracia, controlar el límite disponible, producir estados al corte y explicar los intereses. El alcance excluye stock e impuestos según el enunciado.

## Entradas

Las longitudes siguientes son controles del servidor. SQLite no impone longitudes de `VARCHAR`: sus columnas son TEXT/INTEGER y las restricciones del DDL se complementan con validación de API. Un campo sin valor por defecto indicado debe recibirse o completarse antes de guardar.

| Dato | Tipo / formato / tamaño | Valor inicial y restricciones |
| --- | --- | --- |
| Usuario | Texto 3–80; letras, números, punto, @, guion y guion bajo | Único sin distinguir mayúsculas. |
| Contraseña | Texto 12–128 | Mayúscula, minúscula y número; se recibe por acceso/alta/cambio, se guarda solo su derivado con sal. |
| Respuesta de acceso | Cadena numérica | Suma visible, desafío temporal y de un solo uso. |
| Nombre de tienda / rubro / dirección | Texto, hasta 120 / 100 / 240 respectivamente | Nombre y rubro requeridos; dirección opcional. |
| Nombre de administrador | Texto hasta 120 | Obligatorio al crear tienda. |
| Nombre de cliente | Texto hasta 120 | Obligatorio. |
| Documento / teléfono | Texto hasta 30 cada uno | Documento obligatorio y único por tienda; teléfono opcional. |
| Correo | Texto hasta 160 | Formato con usuario, @ y dominio. |
| Moneda | Enumeración de 3 caracteres | PEN o USD; no cambia con deuda pendiente. |
| Límite de crédito | Cadena decimal, hasta 2 decimales | 0.01–10000000; no puede quedar debajo del capital pendiente. |
| Tasa compensatoria / moratoria | Cadena decimal, hasta 6 decimales | 0–1000 % como límite técnico; no representa un máximo legal. |
| Tipo de cada tasa | effective / nominal | Obligatorio. |
| Período de cada tasa | Entero | 30 o 360 días. |
| Frecuencia de cada tasa | Entero | 1, 2, 4, 6, 12, 24 o 360; valor por defecto 12. Solo modifica una nominal anual. |
| Plazo máximo | Entero | 1–60 meses. |
| Día de corte / día de pago | Entero | 1–31; ajuste al último día disponible del mes. |
| Hora de corte | Entero | 0–23; valor de API por defecto 23, hora de Lima. |
| Consentimiento | Booleano | Debe ser true al registrar cliente; fecha y versión las determina el servidor. |
| Nombre de producto o servicio | Texto hasta 120 | Obligatorio. |
| Descripción / marca / proveedor | Texto hasta 500 / 100 / 120 | Opcionales, cadena vacía. |
| Unidad | Texto hasta 30 | Obligatoria; por ejemplo unidad, kg, corte o sesión. |
| Imagen | URL HTTPS hasta 1000 | Opcional; se muestra un identificador visual si falta. |
| Precio contado / lista | Cadena decimal, hasta 2 decimales | 0.01–10000000; la venta usa el precio de lista del servidor. |
| Permiso de cuotas / activo | Booleanos | Inicialmente true; baja lógica conserva historial. |
| Compra | Fecha-hora ISO local | No futura ni anterior a 10 años; no admite zona añadida. |
| Modalidad / meses | end o installments; entero 1–60 | Al cierre fuerza una sola obligación; cuotas no superan el pacto. |
| Productos de venta | Lista de 1–50 elementos | IDs propios, activos, sin duplicados, moneda coincidente. |
| Cantidad | Cadena decimal hasta 3 decimales | 0.001–10000. |
| Importe de cobro | Cadena decimal, 2 decimales | Debe coincidir exactamente con la liquidación del servidor. |
| Fecha de consulta / ciclo | ISO YYYY-MM-DD, 10 caracteres | Consulta validada; un ciclo debe existir en los cierres del cliente. |
| Identificadores | Enteros positivos hasta 2147483647 | Deben pertenecer a la tienda y perfil autorizado. |

Las listas del navegador contienen opciones por conveniencia, pero el servidor vuelve a validar. Consultar [API.md](API.md) para rutas y ejemplos.

## Datos intermedios

| Variable | Tipo / precisión | Significado y restricción |
| --- | --- | --- |
| factor | BigDecimal; precisión local 36 en conversión | Acumulación para d días; ≥ 1 para tasas admitidas. |
| monthly | BigDecimal | Factor de 30 días menos uno; se conserva precisión antes de redondear dinero. |
| cycle / anchor | date | Corte asignado y fecha base de pago. |
| grace_days / late_days | Entero no negativo | Días comerciales de gracia o mora. |
| financed | BigDecimal monetario, 2 decimales | Capital más gracia capitalizada en cuotas. |
| balance / amortization / interest | BigDecimal monetario, 2 decimales | Saldo, amortización e interés de una cuota. |
| outstanding / available | BigDecimal monetario | Capital pendiente y límite menos ese capital. |
| terms | Objeto JSON | Copia de tasa, moneda, calendario y plazo pactados en una compra. |
| csrf / token | Cadena aleatoria segura | Vinculados a la sesión; nunca se incluyen en exportaciones de negocio. |
| snapshot | JSON | Fotografía del estado de cuenta al corte; inmutable después de crearse. |

## Salidas

| Dato | Formato / tipo | Uso |
| --- | --- | --- |
| Cronograma | Lista de objetos | Número entero, fecha ISO y cadenas decimales: capital, interés, cuota. |
| Gracia y financiado | Cadenas decimales con 2 posiciones | Explican el incremento previo a cuotas. |
| Estado de cuenta | JSON, pantalla, CSV y PDF de impresión | Compras ordenadas, cuotas, vencimientos, totales por moneda y cierres disponibles. |
| Mora | Objeto por cuota | Base monetaria, días enteros, fecha de cálculo, tasa/tipo/base/frecuencia e importe. |
| Liquidación / recibo | Objeto con ID y fecha-hora | Total exigible, base, mora y cuotas canceladas. |
| Indicadores | Importes por moneda y recuentos enteros | Saldo por cobrar, vencidos, cobrado y vendido del mes. |
| Auditoría | Lista con fecha-hora, usuario, acción y entidad | Trazabilidad de operaciones, filtrada según permisos. |
| Error | JSON con mensaje y HTTP 4xx | Explica validaciones, falta de sesión/permisos o conflicto sin guardar cambios parciales. |

## Pantallas y asistencia

| Pantalla | Función principal | Ayuda y controles |
| --- | --- | --- |
| Acceso | Usuario, contraseña y verificación | Textos por campo, botón visible/oculta y búho que cubre los ojos cuando la clave se muestra. |
| Resumen | Estado de cartera por moneda | Filtros de vencimiento, acceso directo al cobro y explicación de saldos. |
| Clientes | Datos, condiciones y consentimiento | Botones de ayuda, límites de entrada y política accesible. |
| Catálogo | Productos/servicios y precios | Imagen, unidad, moneda y permiso de cuotas; búsqueda y estado. |
| Venta | Selección de cliente y productos | Vista previa, plazo habilitado solo en cuotas y confirmación. |
| Cobros | Liquidación completa | Desglose de mora antes de confirmar. |
| Estados | Consulta y cierre guardado | Explicación del tipo de reporte, CSV y formato de impresión. |
| Tiendas | Administración de plataforma | Datos del comercio y acceso de su responsable. |
| Movimientos / configuración | Auditoría y contraseña | Permisos por perfil; cambio de clave revoca otras sesiones. |

Las capturas reproducibles se generan con `python tests/browser_smoke.py` y quedan en `test-results/`. Se comprueban anchos de 1440, 390 y 320 píxeles. Los iconos son Lucide, las tipografías son Manrope y Public Sans locales y la ilustración del acceso usa SVG; no hay dependencias de CDN durante el acceso.

Seguridad y conectividad: [ARQUITECTURA.md](ARQUITECTURA.md). Diccionario físico: [MODELO_DE_DATOS.md](MODELO_DE_DATOS.md). Técnicas, casos y aceptación: [PRUEBAS.md](PRUEBAS.md).
