# Cuenta Clara

Una app para llevar el fiado sin perderse entre apuntes. Cada negocio tiene sus clientes, productos, compras y pagos; cada cliente puede entrar a revisar su propia cuenta.

[![Pruebas](https://github.com/Juan2246/Trabajo-Final-Finanzas-/actions/workflows/ci.yml/badge.svg)](https://github.com/Juan2246/Trabajo-Final-Finanzas-/actions/workflows/ci.yml)

Proyecto del curso **SI642 — Finanzas e Ingeniería Económica**.

## Pruébala en tu computadora

Necesitas **Python 3.12 o superior**. No hace falta instalar frameworks, Node ni una base de datos aparte para ejecutarla. La única dependencia declarada aporta las zonas horarias, necesarias en Windows.

```powershell
git clone https://github.com/Juan2246/Trabajo-Final-Finanzas-.git
cd Trabajo-Final-Finanzas-
python -m pip install -r requirements.txt
python app.py --demo
```

En Windows también puedes abrir **iniciar.bat** dentro de la carpeta del proyecto.\n\nAbre **http://127.0.0.1:8000**. La terminal muestra las contraseñas generadas para estos accesos:

| Usuario | Perfil |
| --- | --- |
| `admin` | Administrador de la plataforma |
| `tienda` | Dueño del negocio |
| `cliente` | Cliente de consulta |

Las claves se muestran solo al crear una base vacía. Los datos de ejemplo son ficticios. La base se conserva en `data/cuenta.db`: cerrar el navegador o reiniciar el servidor no borra las operaciones.

Si Windows indica que falta la zona horaria:

```powershell
python -m pip install tzdata
```

Para crear una instalación vacía, usa `python app.py --init` y elige la contraseña del administrador. Después inicia con `python app.py`.

## Qué incluye

- Tiendas de distintos rubros dentro de una misma plataforma, con datos separados.
- Inicio y cierre de sesión, contraseñas derivadas con PBKDF2, código de verificación aritmético, limitación de intentos y protección CSRF.
- Alta, edición y desactivación de clientes y productos; catálogo con imagen, unidad, proveedor, marca y precios.
- Crédito en soles o dólares, límite por cliente, tasa pactada, corte con hora y fecha mensual de pago.
- Ventas al cierre o en cuotas francesas, con vista previa del cronograma antes de registrar.
- Tasas nominales y efectivas mensuales o anuales; capitalización de la gracia y conciliación del último céntimo.
- Pago completo de las obligaciones exigibles, mora separada y recibos en el historial.
- Estados de cuenta por cliente, impresión, exportación y cierres guardados en la base.
- Registro de movimientos y cambios, ayuda dentro de la app y consentimiento para datos personales.
- Interfaz adaptable a computadora y celular.

No incluye stock ni impuestos: el enunciado los excluye del alcance obligatorio.

## Cómo usarla

1. El administrador de plataforma crea la tienda y su usuario administrador.
2. El dueño registra los productos y al cliente, junto con sus condiciones de crédito y consentimiento.
3. Al registrar una venta, revisa la simulación. El servidor valida precios, moneda y crédito disponible.
4. En pagos, selecciona el cliente y liquida el total exigible del día. Si no hay vencimientos actuales, puedes pagar completo el próximo vencimiento de forma anticipada; se mantiene el interés pactado hasta su vencimiento. No se aceptan abonos parciales.
5. En estados de cuenta puedes consultar el detalle de compras, cuotas, intereses y mora. El cliente ve solo su información.

La fecha de trabajo se toma del servidor en **America/Lima**. Las fechas de corte y pago admiten días 1 al 28 para que existan todos los meses. El sistema usa una base comercial de **30/360**, documentada en [el modelo financiero](docs/FINANZAS.md).

Las compras de períodos ya cerrados se rechazan para conservar el historial. Los cierres recuperados usan el calendario de corte vigente del cliente; los cronogramas de las compras conservan sus propias condiciones. Las consultas históricas reconstruyen pagos por fecha, sin distinguir su hora dentro del mismo día.\n\nEl cierre se ejecuta al iniciar el servidor y después cada minuto. Si el servidor estuvo apagado, recupera los cierres pendientes al volver a encenderlo. También puede ejecutarse con `python app.py --close-statements`. Los cierres guardados son fotografías del corte; las consultas del día añaden la mora vigente.

## Estructura

```text
app.py                 Servidor, permisos y operaciones
finance.py             Cálculos con Decimal
schema.sql             Tablas y relaciones de SQLite
static/                Interfaz web sin compilación
tests/                 Pruebas financieras, API y navegador
docs/                  Fórmulas y arquitectura
.github/workflows/     Comprobaciones automáticas
```

## Pruebas y aportes

```powershell
python -m unittest discover -s tests -v
```

GitHub Actions ejecuta los casos financieros, las pruebas de acceso y operaciones, la revisión de sintaxis y un recorrido con Chromium en computadora y celular. Las capturas quedan en el artefacto **pruebas-interfaz** de cada ejecución.

Para trabajar en grupo, cada integrante usa su cuenta y una rama para sus cambios. Los aportes se ven en [Contributors](https://github.com/Juan2246/Trabajo-Final-Finanzas-/graphs/contributors) cuando llegan a `main`. El correo del commit debe estar vinculado a la cuenta correcta. Evitemos subir bases reales, contraseñas o datos personales.

## Docker

```powershell
docker build -t cuenta-clara .
docker run --rm -p 8000:8000 -v cuenta-clara-data:/data -e ADMIN_PASSWORD="TuClaveSegura2026" cuenta-clara
```

Cambia esa clave por una propia. El volumen conserva la base. Abre http://localhost:8000 y entra como `admin`.

## Configuración y despliegue

Las variables disponibles están en [.env.example](.env.example). El servidor lee variables de entorno del sistema; el archivo `.env` no se carga automáticamente.

En un hosting con disco persistente, ejecuta el contenedor detrás de un proxy HTTPS y configura `SECURE_COOKIES=1`. Mantén `DB_PATH` en el volumen persistente y haz respaldos con la API de backup de SQLite. La base de datos no va en Git.

El servidor incluido sirve para la entrega académica y uso local. Para exponerlo a usuarios reales, el operador debe configurar HTTPS, copias de seguridad, gestión de accesos y revisar la política de datos con los responsables del negocio. La app no calcula límites legales de tasas ni sustituye asesoría legal.

Más detalles en [Arquitectura](docs/ARQUITECTURA.md) y [Finanzas](docs/FINANZAS.md).

## Referencias de interfaz

Diseño propio con referencias de organización de [shadcn/ui Dashboard](https://ui.shadcn.com/examples/dashboard): navegación lateral, indicadores, tablas y jerarquía visual. Los componentes de esta app están escritos en HTML, CSS y JavaScript.
