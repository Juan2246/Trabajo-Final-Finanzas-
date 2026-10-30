# Plan de pruebas

Las pruebas usan cuentas y bases temporales, nunca datos reales. Los casos de API comparten una base temporal del contexto Spring y la vacían entre pruebas; se elimina al finalizar la clase.

## Casos financieros

| Caso | Resultado esperado |
| --- | --- |
| Compra 15/09, corte20/09, pago26, tres meses | 11 días de gracia; cuotas26/10,26/11 y26/12 |
| Compra después de la hora de corte | Pasa al período siguiente |
| Compra en el instante exacto del corte | Pertenece al corte actual |
| Tasa cero | Cuotas que reparten exactamente el capital |
| Tasa nominal anual capitalizable mensualmente | Conversión equivalente a tasa del mes |
| Última cuota | Ajusta los centavos para cerrar el saldo en cero |
| Pago atrasado | Mora separada por días comerciales |
| NaN, infinito, tasas negativas o plazo no permitido | Operación rechazada |

## Casos de servidor

| Área | Verificación |
| --- | --- |
| Acceso | Sin sesión no hay datos; cierre invalida la sesión |
| Contraseñas | Claves débiles rechazadas; cambio revoca otras sesiones |
| Desafío | Un solo uso; intentos fallidos quedan registrados |
| CSRF | Mutación sin token rechazada |
| Tiendas | Cambiar IDs no permite consultar otra tienda |
| Cliente | Consulta propia; no puede vender ni cobrar |
| Venta | Precios tomados del catálogo; simulación sin guardar |
| Crédito | Compra por encima del límite rechazada sin dejar filas parciales |
| Pagos | Solo liquidación completa; no se puede cancelar dos veces |
| Cierres | Copia guardada inmutable y accesible solo por su tienda |
| Historia | Un pago posterior no borra una deuda de una consulta anterior |
| Bajas | Desactivar tienda revoca accesos; cliente inactivo no puede comprar |

## Técnicas y ejecución

Se combinan pruebas de caja blanca del motor con pruebas de caja negra sobre la API HTTP mediante MockMvc y Spring Security y el navegador. Los controles de base de datos en las pruebas de API verifican la atomicidad de las operaciones.

```sh
./mvnw verify
```

Para el recorrido visual, primero compila el JAR con `./mvnw package` (Windows: `.\mvnw.cmd package`). Después:

```sh
python -m pip install -r requirements-dev.txt
python -m playwright install chromium
python tests/browser_smoke.py
```

GitHub Actions ejecuta las comprobaciones en una máquina limpia. Las capturas de escritorio y celular se guardan en `test-results/` como artefacto de la ejecución. El resultado válido es el de la ejecución correspondiente al commit revisado, no el de una versión anterior.

## Recorrido de la interfaz

El recorrido comprueba los filtros de cuotas (todos, vencidos y próximos), el cambio de moneda y la consulta de cobro desde el resumen con el cliente ya seleccionado. También verifica el control para mostrar la contraseña y el cierre del menú móvil con Escape; el menú oculto queda fuera de la navegación por teclado.

Se crean y editan clientes y productos, se registra una venta y su pago completo, y se exporta el estado de cuenta a CSV y PDF. Las vistas se revisan con anchos de 1440, 390 y 320 píxeles. Las tablas generales permiten desplazamiento interno; los pendientes del resumen se reorganizan en filas con su acción visible en celular.

Las pruebas de integración cierran el pool JDBC antes de eliminar su base temporal. El recorrido de navegador detiene el proceso Java al terminar, también en Windows.

## Ampliación de calendario y detalle financiero

- Corte día 31 en febrero normal y bisiesto; compra exactamente a la hora del corte y un segundo después.
- Calendario que vuelve al día 30/31 tras febrero, incluyendo cierres automáticos y cambio de año.
- Caso A con cuotas 341.27, 341.27 y 341.26; caso B con obligación 510.57, mora 3.04 y total 513.61.
- Desglose de mora que conserva tasa, base y días aunque cambie después el acuerdo del cliente.
- Búho con ojos abiertos al ocultar la clave y cubiertos al mostrarla, también con movimiento reducido y en celular.
- Alta del cliente con corte/pago 31, ayuda de campo y detalle de mora visible tanto al consultar como al cobrar.

## Plan de aceptación

| Fase | Técnica | Entrada | Criterio de salida |
| --- | --- | --- | --- |
| Motor | Caja blanca y valores frontera | Casos A/B, tasa cero, fechas y datos no finitos | Fechas, cuotas, amortización y mora coinciden con los resultados documentados. |
| API | Caja negra | Peticiones válidas, parciales, ajenas y sin sesión | Respuestas y permisos correctos; no hay operaciones parciales guardadas. |
| Persistencia/concurrencia | Caja gris | Peticiones simultáneas e inspección de BD temporal | No se excede el crédito ni se duplica un cobro; el cierre no se sobrescribe. |
| Navegador | Caja negra, recorrido funcional | Tres perfiles, compra y pago completos | Flujo completo sin errores JS; CSV/PDF generados y sin desborde horizontal. |
| Despliegue | Integración | Imagen Docker, volumen persistente y reinicio | Health responde y el usuario inicial permanece tras reiniciar. |

Precondiciones: JDK 21 instalado; Python y requirements-dev.txt solo para las pruebas de navegador, Chromium para el recorrido, bases temporales y datos ficticios. Responsabilidad de revisión: quien integra el cambio debe revisar el resultado y las capturas; el grupo asigna sus responsables académicos reales.

Una prueba fallida bloquea la integración hasta corregirla o justificar un cambio de alcance. Registrar caso, entrada, esperado, obtenido y corrección. Los nombres de casos en `src/test/` y `tests/` son el inventario ejecutable; el resultado de GitHub Actions acredita únicamente el commit al que pertenece. No se presenta este plan como prueba de carga, auditoría de seguridad completa ni estudio de usabilidad con personas.

## Inventario Java

| Suite | Casos | Comprobación |
| --- | --- | --- |
| MotorFinancieroServiceTest | 184 | 180 planes completos de referencia y cuatro casos adicionales de calendario, tasa cero, validación y mora |
| ContrasenaServiceTest | 3 | Formato anterior, sales aleatorias independientes y requisitos de contraseña |
| ApiIntegrationTest | 20 | Autenticación, CSRF, permisos, aislamiento, consentimiento, validaciones, simulación, atomicidad, pagos, concurrencia, historia y cierres |

Total: **207 pruebas JUnit**, además del recorrido en Chromium y la prueba del contenedor. Los resultados concretos se consultan en `target/surefire-reports/` y en GitHub Actions. El fixture `legacy-finance-cases.json` contiene resultados previos a la migración; no se regenera con el motor bajo prueba.
