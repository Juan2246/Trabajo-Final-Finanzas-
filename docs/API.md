# API de Cuenta Clara

Todas las rutas devuelven JSON. Los errores usan `{"error":"mensaje"}` y un estado HTTP 400, 401, 403, 404, 409 o 429 según el caso. La sesión viaja en una cookie HttpOnly; no se guarda en localStorage.

## Acceso

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | /api/health | Estado del servidor |
| GET | /api/challenge | Desafío de acceso: id y pregunta |
| POST | /api/login | username, password, challenge_id, answer |
| GET | /api/me | Usuario y token CSRF |
| POST | /api/logout | Terminar sesión |
| POST | /api/password | current_password y password |

Las operaciones POST y PATCH requieren `Content-Type: application/json`. Después del acceso, también requieren `X-CSRF-Token`. Las sesiones vencen a las 8 horas; cambiar la contraseña cierra las otras sesiones.

## Negocio

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | /api/data | Datos permitidos para el usuario |
| POST | /api/stores | Crear tienda y administrador; solo plataforma |
| PATCH | /api/stores/:id | Editar o desactivar una tienda |
| POST | /api/customers | Cliente, condiciones, consentimiento y acceso |
| PATCH | /api/customers/:id | Editar o desactivar cliente; password opcional |
| POST | /api/products | Crear producto o servicio |
| PATCH | /api/products/:id | Editar o desactivar producto |
| POST | /api/sales/preview | Simular sin guardar |
| POST | /api/sales | Registrar compra y cronograma |
| POST | /api/payments/preview | Liquidar obligaciones exigibles |
| POST | /api/payments | Registrar pago exacto de la liquidación |
| GET | /api/statement?customer_id=:id&date=YYYY-MM-DD | Consultar cuenta al día indicado |
| GET | /api/statement?customer_id=:id&cycle=YYYY-MM-DD | Consultar cierre inmutable guardado |

El administrador de plataforma agrega `?store_id=:id` para operar en una tienda. Los otros perfiles toman siempre la tienda de su sesión, aunque alguien altere ese parámetro. El cliente solo puede consultar su información y cambiar su propia contraseña.

## Ejemplo de venta

```json
{
  "customer_id": 1,
  "items": [{"product_id": 1, "quantity": "2"}],
  "mode": "installments",
  "months": 3,
  "purchased_at": "2026-09-15T10:00"
}
```

Los precios y las tasas definitivos se consultan en el servidor. En la respuesta, los importes son cadenas decimales; las fechas son ISO. La vista previa no reserva crédito: al confirmar se vuelve a validar dentro de una transacción.

La compra se rechaza si el cliente o producto está inactivo, pertenece a otra tienda, usa otra moneda, supera el límite o cae en un período cerrado. La baja de una tienda revoca sus sesiones. La baja de un cliente bloquea nuevas compras y su acceso; el dueño puede seguir consultando y cobrando su deuda.

## Liquidación y estado de cuenta

La liquidación suma todas las cuotas pendientes que vencen hoy o ya vencieron, con mora hasta hoy. Cuando no hay cuotas exigibles, permite cancelar por completo el siguiente vencimiento; conserva el interés acordado y no calcula un descuento por prepago.

El pago se recalcula en el servidor al confirmar. Si el importe cambió, se rechaza para que el usuario vuelva a revisar. No admite pagos parciales, doble cancelación ni pagos con fecha retroactiva.

El estado de cuenta incluye:

- `dues`: cronograma completo de las compras ya cerradas.
- `payable`: obligaciones pendientes hasta el próximo vencimiento informado.
- `amount_due` y `payment_date`: importe y fecha de ese pago.
- `future_balance`: importe de cuotas posteriores.
- `totals`: saldo total pendiente, separado por moneda.
- `closed_statements`: cierres disponibles.
- `is_closed`: distingue un cierre guardado de una consulta actual.
- Cada cuota consultada incluye `interest_days`, `late_days`, `late_base`, `late_as_of`, `late_amount`, `total` y las tasas originales (`rate`, `rate_kind`, `rate_period`, `frequency`, `late_rate`, `late_kind`, `late_period`, `late_frequency`). Los cierres antiguos pueden no contener los campos agregados después; la interfaz conserva su lectura y usa las condiciones históricas de la compra como respaldo.

Los cierres no se vuelven a escribir cuando cambia una tasa. La mora de una consulta actual se recalcula por separado; un cierre muestra la información que quedó registrada en su corte.
