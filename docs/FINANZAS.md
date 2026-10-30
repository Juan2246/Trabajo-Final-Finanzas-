# Cómo salen las cuotas

El motor está en [MotorFinancieroService.java](../src/main/java/pe/cuentaclara/finanzas/MotorFinancieroService.java). Los cálculos usan `BigDecimal`: el monto no pasa por números binarios de punto flotante. Se redondea a dos decimales con `RoundingMode.HALF_UP`.

Las reglas siguen el enunciado del curso SI642 compartido para este proyecto. Son decisiones del modelo académico, no una validación de condiciones legales de crédito.

## Fechas y períodos

- La base es **30/360**: un mes tiene 30 días y un año 360.
- Para contar días se usa `360 × diferencia de años + 30 × diferencia de meses + min(día final, 30) − min(día inicial, 30)`.
- Se conserva el calendario real para mostrar vencimientos. Los días de corte y pago se configuran entre 1 y 31. Si un mes es más corto, se usa su último día; el siguiente mes vuelve al día pactado. Por ejemplo, día 31: 28/02/2026, 31/03/2026, 30/04/2026.
- La hora de corte se interpreta en la zona horaria del negocio configurada por el servidor. Una compra exactamente a la hora de corte pertenece al período que cierra; un segundo después pasa al siguiente.
- Si el día de pago es menor que el de corte, el pago corresponde al mes siguiente al corte.
- El cálculo comercial puede diferir de los días naturales: del 28 de febrero al 1 de marzo son 3 días bajo esta convención. No se mezclan bases en un mismo crédito.

## Conversión de tasas

La tasa ingresada está expresada como porcentaje: para 12 % se escribe `12`, no `0.12`.

Para una tasa efectiva `r`, período base `B` (30 o 360 días) y plazo `d`:

```text
factor(d) = (1 + r/100)^(d/B)
```

Para una tasa nominal anual `j` con `m` capitalizaciones por año:

```text
factor(d) = (1 + j/(100 × m))^(m × d/360)
```

Se aceptan capitalizaciones anuales, semestrales, trimestrales, bimestrales, mensuales, quincenales y diarias: `m = 1, 2, 4, 6, 12, 24, 360`.

Una nominal mensual se interpreta con capitalización mensual:

```text
factor(d) = (1 + j/100)^(d/30)
```

En ese caso el campo de frecuencia anual no cambia el cálculo. Por ejemplo, una TNA de 12 % con capitalización mensual produce una tasa mensual de 1 %; una TEA de 12 % produce una tasa mensual distinta.

## Pago al cierre

Cada compra se calcula por separado, desde su fecha hasta la fecha de pago de su período:

```text
total de la compra = capital × factor(días)
interés compensatorio = total de la compra − capital
```

Una compra posterior al corte pasa al siguiente período y acumula los días correspondientes hasta su fecha de pago. El estado de cuenta agrupa las compras por cliente y moneda.

## Pago en cuotas

Se aplica el método francés vencido ordinario. Primero se capitaliza la gracia total y después se calcula la cuota mensual:

```text
capital financiado = capital original × factor(días de gracia)
interés de gracia = capital financiado − capital original
i = factor(30) − 1
cuota = capital financiado × i / (1 − (1+i)^(-n))
```

Cuando `i = 0`, la cuota es `capital financiado / n`.

Para cada cuota:

```text
interés del período = saldo de capital × i
amortización = cuota − interés del período
nuevo saldo = saldo anterior − amortización
```

La primera cuota vence un mes después de la fecha base de pago. El interés de gracia ya forma parte del capital financiado: no se vuelve a sumar como un cargo adicional. El último pago se ajusta por los centavos para cerrar el saldo exactamente en cero.

Para los límites de crédito se usa el capital pendiente, incluyendo el interés de gracia capitalizado, sin contar dos veces los intereses futuros de las cuotas.

## Mora y pagos

La mora se aplica a la obligación vencida completa, con su propia tasa pactada:

```text
interés moratorio = obligación vencida × (factor moratorio(días) − 1)
total a pagar = suma de obligaciones vencidas + suma de sus moras
```

El pago de una obligación al vencimiento no genera mora. Los pagos parciales no están permitidos: se cobra el total exigible del cliente a la fecha de pago. La mora se muestra separada del interés compensatorio y se guarda con el recibo.

Cambiar las tasas del cliente no cambia las compras existentes. Cada venta conserva sus condiciones originales.

## Dos juegos de prueba

### Caso 1: compra antes del corte, tres cuotas

- Compra: 15/09/2026.
- Capital: S/ 1 000.
- Corte: día 20, 18:00.
- Pago: día 26.
- Tasa: nominal anual de 12 %, capitalización mensual.
- Plazo: tres cuotas.

Resultado esperado:

- Gracia total de 11 días hasta el 26/09/2026.
- Capital financiado: `redondear(1000 × 1.01^(11/30))`.
- Tres vencimientos: 26/10, 26/11 y 26/12.
- La suma de las amortizaciones coincide con el capital financiado.

### Caso 2: pago al cierre y mora

- Compra: 26/08/2026 a las 12:00.
- Corte: día 26, 12:00.
- Pago: día 26.
- Capital: S/ 110.
- Tasa compensatoria: 0 %.
- Tasa moratoria: nominal anual de 24 %, capitalización mensual.
- Pago realizado: 26/09/2026.

Resultado esperado:

- La compra exactamente al corte pertenece al período de agosto.
- Obligación del 26/08: S/ 110.
- Mora: 30 días comerciales, `110 × 0.02 = S/ 2.20`.
- Total: S/ 112.20.

Las pruebas también comprueban límites, fechas entre años, febrero, tasas equivalentes, datos inválidos, cuota final y tasas cero. Para ejecutarlas:

```sh
./mvnw verify
```

## Referencias de implementación

Los dos juegos completos, con cifras de capital, cuota, interés, saldo y mora, están en [DATOS_DE_PRUEBA.md](DATOS_DE_PRUEBA.md).

- Enunciado del Trabajo Final SI642 Finanzas e Ingeniería Económica, proporcionado por el grupo.
- [Java: BigDecimal](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/math/BigDecimal.html).
- [Java: fechas y horas](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/time/package-summary.html).
- [OpenStax: amortización de préstamos](https://openstax.org/books/principles-finance-2e/pages/8-3-loan-amortization).

Las fórmulas se exponen para que el grupo pueda contrastarlas con la bibliografía y las convenciones acordadas con su profesor.
