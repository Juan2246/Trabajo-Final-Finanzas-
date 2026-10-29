# Juegos de datos resueltos

Importes en soles, base comercial 30/360 y redondeo HALF_UP a dos decimales. Los resultados se contrastan con operaciones de Decimal y las pruebas automatizadas; no usan datos personales reales.

## A. Tres cuotas con gracia total

| Entrada | Valor |
| --- | --- |
| Compra | 15/09/2026 12:00 |
| Capital | 1000.00 |
| Corte | Día 20 a las 18:00 |
| Pago pactado | Día 26 |
| Modalidad y plazo | Francés vencido; 3 meses |
| Tasa compensatoria | TNA 12 %, capitalización mensual, m = 12 |
| Límite del cliente | 2000.00; saldo anterior 0.00 |

El corte es 20/09/2026; la fecha base es 26/09/2026. Los 11 días de gracia generan `1000 × (1.01^(11/30) − 1)`, redondeado a **3.66**. Capital financiado **1003.66**; tasa mensual **1 %**.

La cuota teórica redondeada es `1003.66 × 0.01 / (1 − 1.01^(-3)) = 341.27`.

| Cuota | Vencimiento | Saldo inicial | Interés | Amortización | Pago | Saldo final |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 26/10/2026 | 1003.66 | 10.04 | 331.23 | 341.27 | 672.43 |
| 2 | 26/11/2026 | 672.43 | 6.72 | 334.55 | 341.27 | 337.88 |
| 3 | 26/12/2026 | 337.88 | 3.38 | 337.88 | 341.26 | 0.00 |

Comprobaciones: amortización total **1003.66**, interés de cuotas **20.14**, pagos **1023.80**. El costo sobre los 1000 originales es **23.80**, formado por 3.66 de gracia y 20.14 de cuotas. No se suma la gracia nuevamente al total del cronograma. Disponible después de la compra: **996.34**.

## B. Compra después del corte, tasa efectiva y pago con mora

| Entrada | Valor |
| --- | --- |
| Compra | 21/09/2026 12:00 |
| Capital | 500.00 |
| Corte y pago | Día 20, 18:00; día 26 |
| Modalidad | Pago al cierre |
| Tasa compensatoria | TEA 24 % |
| Tasa moratoria | TNA 24 %, capitalización mensual |
| Pago real | 05/11/2026 |

La compra pasa al corte **20/10/2026** y vence **26/10/2026**. Desde el 21/09 hasta el 26/10 hay **35 días comerciales**.

| Resultado | Cálculo | Importe |
| --- | --- | --- |
| Interés compensatorio | 500 × (1.24^(35/360) − 1), redondeado | 10.57 |
| Obligación vencida | 500 + 10.57 | 510.57 |
| Días de mora | 30 + 5 − 26 | 9 |
| Interés moratorio | 510.57 × (1.02^(9/30) − 1), redondeado | 3.04 |
| Pago completo | 510.57 + 3.04 | 513.61 |

El reporte debe mostrar por separado base vencida **510.57**, **9 días** y tasa nominal anual **24 %**, capitalización **12 veces al año**. Un pago de 513.60 o de 500.00 debe rechazarse, sin registrar recibo. La mora se calcula sobre toda la obligación vencida, no solo sobre los 500 originales.

## Fronteras y errores

| Entrada | Resultado esperado |
| --- | --- |
| Compra 20/09 18:00:00 | Corte del 20/09 |
| Compra 20/09 18:00:01 | Corte del 20/10 |
| Corte 31 en febrero de 2026/2028 | 28/02 y 29/02, respectivamente |
| Pago pactado 30; cuotas febrero, marzo, abril | 28/02, 30/03, 30/04 en 2026 |
| Tasa cero, 100.00 en 3 meses | 33.33, 33.33 y 33.34 |
| Día 32, tasa negativa, NaN o infinito | Rechazo de validación |
| Cambiar tasa del cliente después de vender | La compra conserva su tasa original |
| Dos compras simultáneas que juntas superan el límite | Solo la operación que cabe se confirma |
| Dos cobros de las mismas cuotas | Un solo recibo válido |

Pruebas relacionadas: `test_finance.py`, `test_app.py`, `test_concurrency.py`, `test_statements.py` y `test_preview.py`. [PRUEBAS.md](PRUEBAS.md) explica la ejecución y las evidencias de navegador.
