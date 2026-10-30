# Cuenta Clara

Una app para llevar el fiado sin perderse entre apuntes. Cada negocio tiene sus clientes, productos, compras y pagos; cada cliente puede entrar a revisar su propia cuenta.

[![Pruebas](https://github.com/Juan2246/Trabajo-Final-Finanzas-/actions/workflows/ci.yml/badge.svg)](https://github.com/Juan2246/Trabajo-Final-Finanzas-/actions/workflows/ci.yml)

Proyecto del curso **SI642 — Finanzas e Ingeniería Económica**.

## Pruébala en tu computadora

Necesitas **JDK 21**. El proyecto incluye Maven Wrapper, así que no tienes que instalar Maven ni un servidor de base de datos. La primera compilación descarga las dependencias.

```powershell
git clone https://github.com/Juan2246/Trabajo-Final-Finanzas-.git
cd Trabajo-Final-Finanzas-
.\mvnw.cmd verify
$env:DEMO="true"
java -jar target/cuenta-clara.jar
```

En Windows también puedes abrir **iniciar.bat**. En Linux/macOS: `./mvnw verify`, luego `DEMO=true java -jar target/cuenta-clara.jar`.

Abre **http://127.0.0.1:8000**. Si la base está vacía y `DEMO=true`, se crean datos ficticios y estos accesos:

| Usuario | Perfil |
| --- | --- |
| `admin` | Administrador de plataforma |
| `tienda` | Dueño del negocio |
| `cliente` | Cliente de consulta |

Las claves se generan y se muestran **una sola vez en la terminal**. También puedes definir `ADMIN_PASSWORD` y `DEMO_PASSWORD` antes del primer arranque. No hay una contraseña universal publicada.

Sin `DEMO=true` se crea únicamente el administrador. Una base existente se conserva: la demostración no la reinicia ni añade registros. Los datos viven en `data/cuenta.db`.

**Si tienes la versión anterior:** Java abre la misma base y admite las contraseñas existentes. Lee la [guía de migración](docs/MIGRACION_JAVA.md) antes de cambiar de servidor.

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

La fecha de trabajo se toma del servidor en **America/Lima**. Las fechas de corte y pago admiten días 1 al 31; si el mes es más corto se usa su último día y después se recupera el día pactado. El sistema usa una base comercial de **30/360**, documentada en [el modelo financiero](docs/FINANZAS.md).

Las compras de períodos ya cerrados se rechazan para conservar el historial. Los cierres recuperados usan el calendario de corte vigente del cliente; los cronogramas de las compras conservan sus propias condiciones. Las consultas históricas reconstruyen pagos por fecha, sin distinguir su hora dentro del mismo día.

El cierre se ejecuta al iniciar el servidor y después cada minuto. Si el servidor estuvo apagado, recupera los cierres pendientes al volver a encenderlo. Los cierres guardados son fotografías del corte; las consultas del día añaden la mora vigente.

## Estructura Java

El backend usa **Spring Boot, Spring Security y Spring Data JPA**. Se organiza por módulo para encontrar juntas las piezas de cada función.

```text
src/main/java/pe/cuentaclara/
  acceso/      Acceso, usuarios, sesiones y contraseñas
  tiendas/     Tiendas y permisos por negocio
  clientes/    Clientes y condiciones de crédito
  catalogo/    Productos y servicios
  creditos/    Ventas, detalle y cuotas
  pagos/       Cobros completos y sus líneas
  estados/     Estados de cuenta y cierres
  finanzas/    Fórmulas con BigDecimal, sin dependencias de HTTP
  auditoria/   Registro de operaciones
  resumen/     Consultas del resumen
  config/      Seguridad, base de datos y serialización
  comun/       Errores, reloj y conversiones comunes
  inicio/      Datos iniciales opcionales
src/main/resources/
  application.yml   Variables de configuración
  db/schema.sql     14 tablas, claves e índices
  static/           HTML, CSS, JavaScript, SVG y fuentes locales
src/test/           Pruebas JUnit y casos financieros de referencia
tests/              Recorridos de navegador y contenedor
docs/               Diseño, fórmulas, API y manual
```

Ejemplo: en `clientes/`, **Cliente** es la entidad, **ClienteRepository** consulta la base, **ClienteService** aplica las reglas y **ClienteController** recibe HTTP. Los records **ClienteEntradaDTO** y **ClienteSalidaDTO** llevan datos entre la API y la interfaz. Las entidades no se devuelven directamente al navegador.

Los nombres del negocio y sus métodos están en español: `limiteCredito`, `crear`, `actualizar`, `simular` y `calcularPlanPagos`. Conservamos los términos técnicos `Controller`, `Service`, `Repository` y `DTO`, además de las convenciones de Java y Spring (`get`, `set`, `findBy`, etc.).

Para empezar a editar, revisa la [arquitectura y recorrido de una venta](docs/ARQUITECTURA.md). El frontend sigue siendo una aplicación web adaptable en HTML/CSS/JavaScript; se sirve desde Spring Boot y no requiere compilación propia.

## Pruebas y aportes

```powershell
.\mvnw.cmd verify
```

GitHub Actions ejecuta los casos financieros, las pruebas de acceso y operaciones, la revisión de sintaxis y un recorrido con Chromium en computadora y celular. Las capturas quedan en el artefacto **pruebas-aplicacion** de cada ejecución.

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

La configuración incluida usa SQLite con un solo escritor y una sola instancia de aplicación por archivo de base. Para exponerlo a usuarios reales, el operador debe configurar HTTPS, copias de seguridad, gestión de accesos y revisar la política de datos con los responsables del negocio. La app no calcula límites legales de tasas ni sustituye asesoría legal.

Más detalles en [Arquitectura](docs/ARQUITECTURA.md) y [Finanzas](docs/FINANZAS.md).

## Diseño y referencias

La navegación usa verde profundo; el resumen destaca el saldo y los vencimientos, seguido de una lista de cobros. En celular, cada pendiente mantiene a la vista su importe y su acción. El acceso conserva el búho que se tapa los ojos al mostrar la contraseña, con soporte de movimiento reducido.

Se revisaron [Square](https://squareup.com/us/en/point-of-sale), [Mercury](https://demo.mercury.com/) y [Pennylane](https://www.pennylane.com/fr/logiciel-tresorerie) como referencias de jerarquía, navegación y lectura de importes. La interacción del personaje toma como referencia los [avatares de Darin Senneff](https://codepen.io/dsenneff). No se utilizan sus marcas ni se copia su código.

Los controles usan [Lucide](https://github.com/lucide-icons/lucide), con [licencia incluida](src/main/resources/static/vendor/LUCIDE-LICENSE.txt). El símbolo de Cuenta Clara representa un cuaderno. Manrope y Public Sans se sirven localmente con sus licencias en `static/fonts/`.

## Documentación del proyecto

- [Requisitos y evidencias](docs/REQUISITOS.md).
- [Análisis de entradas, salidas e interfaces](docs/ANALISIS.md).
- [Algoritmos y diagramas](docs/ALGORITMOS.md).
- [Modelo de datos y diccionario físico](docs/MODELO_DE_DATOS.md).
- [Dos casos financieros resueltos](docs/DATOS_DE_PRUEBA.md).
- [Marco de referencia y metodología](docs/MARCO_Y_METODOLOGIA.md).
- [Manual y guion de demostración](docs/MANUAL.md).
- [Plan de pruebas](docs/PRUEBAS.md) y [contrato de API](docs/API.md).
