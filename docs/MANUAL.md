# Uso y demostración

## Preparar el acceso

Ejecuta `python app.py --demo` después de instalar `requirements.txt`. Abre http://127.0.0.1:8000. Una base nueva muestra las credenciales generadas en la terminal; no hay contraseñas universales publicadas. Reiniciar conserva la base existente.

Escribe usuario y clave y resuelve la suma. El ojo muestra u oculta la contraseña; el búho se cubre los ojos al mostrarla. La animación es una señal visual, no un control de seguridad. Cierra sesión al terminar en un equipo compartido.

## Administrador de plataforma

1. En **Tiendas**, registra nombre, rubro, dirección y acceso del administrador del negocio.
2. Selecciona la tienda que vas a revisar. El selector delimita los datos mostrados.
3. Para dar de baja, desactiva la tienda y confirma. Sus usuarios dejan de acceder; las operaciones permanecen guardadas.

## Dueño del comercio

1. **Catálogo:** registra productos o servicios. El precio de lista se usa para crédito; el contado se conserva como referencia. Puedes indicar una imagen por URL HTTPS.
2. **Clientes:** registra identidad, contacto, moneda, límite, tasas, plazo y calendario. Usa la ayuda de cada campo. Lee la política con el cliente y registra su autorización.
3. **Ventas a crédito:** selecciona cliente y productos, pago al cierre o cuotas. Calcula, revisa gracia, capital y cronograma; confirma para guardar.
4. **Cobros y pagos:** consulta el total. Revisa días, base y tasa de cada mora. Confirma cuando recibas el pago completo.
5. **Estados de cuenta:** selecciona cliente y fecha. Puedes elegir un cierre guardado, exportar CSV o usar Imprimir/PDF.
6. **Movimientos:** revisa quién registró o modificó operaciones. **Configuración** permite cambiar tu contraseña.

Editar tasas o plazos cambia compras futuras. Las existentes conservan sus condiciones. Desactivar productos o clientes conserva su historial. El comercio puede seguir cobrando la deuda de un cliente desactivado.

## Cliente

El acceso que entrega el negocio permite revisar el resumen, cronograma e información propia. No permite consultar otros clientes ni registrar ventas o cobros. El cliente puede cambiar su contraseña.

## Dudas frecuentes

| Situación | Qué hacer |
| --- | --- |
| Pago pactado día 31 | Se usa el último día en meses cortos y se vuelve al 31 cuando existe. |
| Compra por encima del límite | Revisa capital pendiente y gracia capitalizada. Modifica el límite solo con un nuevo acuerdo. |
| Compra antigua rechazada | El ciclo ya se cerró; su fotografía no admite ventas retroactivas. |
| Total cambiado al confirmar cobro | Consulta de nuevo: el servidor recalcula la liquidación. |
| Diferencia entre cierre y consulta actual | El cierre conserva su fecha; la consulta actual incluye mora posterior. |
| Cambio de moneda bloqueado | Primero debe liquidarse la deuda; no se convierten monedas. |
| Demasiados intentos de acceso | Espera el bloqueo de 15 minutos y genera otra comprobación. |
| Cambios descargados no visibles | Reinicia el servidor si cambió Python y recarga el navegador. |

## Guion de exposición: seis minutos

| Tiempo | Demostración |
| --- | --- |
| 0:00–0:40 | Problema del cuaderno de fiados, alcance y tres perfiles. |
| 0:40–1:20 | Acceso, resumen por moneda y tienda de otro rubro. |
| 1:20–2:10 | Cliente, tasas, corte, pago, límite y consentimiento. |
| 2:10–3:10 | Venta en tres cuotas: gracia, método francés y último céntimo. |
| 3:10–4:10 | Estado al corte y mora detallada. |
| 4:10–5:10 | Pago completo, intento parcial rechazado y recibo. |
| 5:10–6:00 | Consulta exclusiva del cliente, auditoría y pruebas. |

Usa [los dos juegos resueltos](DATOS_DE_PRUEBA.md) para explicar las cifras. Para repetir demostraciones, crea una base temporal; no borres la base del negocio. Capturas y PDF del recorrido están en el artefacto `pruebas-interfaz` de GitHub Actions.
