# Plan de pruebas

Las pruebas usan cuentas y bases temporales, nunca datos reales. Cada caso de API crea su propia base y la elimina al terminar.

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

Se combinan pruebas de caja blanca del motor con pruebas de caja negra sobre la API WSGI y el navegador. Los controles de base de datos en las pruebas de API verifican la atomicidad de las operaciones.

```sh
python -m unittest discover -s tests -v
```

Para el recorrido visual:

```sh
python -m pip install playwright==1.51.1
python -m playwright install chromium
python tests/browser_smoke.py
```

GitHub Actions ejecuta las comprobaciones en una máquina limpia. Las capturas de escritorio y celular se guardan en `test-results/` como artefacto de la ejecución. El resultado válido es el de la ejecución correspondiente al commit revisado, no el de una versión anterior.
