/* Acceso, privacidad y archivos del catálogo. */
function seguridadCuenta() {
  const activa = state.seguridad?.enabled;
  return `<section class="panel account-section" aria-labelledby="security-title"><div class="account-section-head"><div><h2 id="security-title">Un paso más para proteger tu cuenta</h2><p>Confirma cada inicio de sesión con un código de tu autenticador.</p></div>${badge(activa ? "Doble factor activo" : "Sin segundo factor", activa ? "" : "warning")}</div><div class="security-options"><div>${icon("shield")}<div><strong>${activa ? "Tu autenticador está conectado" : "Conecta tu app autenticadora"}</strong><p>${activa ? `Te quedan ${Number(state.seguridad.recovery_remaining)} códigos de recuperación. Guárdalos fuera de este dispositivo.` : "Puedes usar Google Authenticator, Microsoft Authenticator u otra app compatible. No necesitas recibir un correo."}</p></div></div><div class="filter-row">${activa ? btn("Nuevos códigos", "mfa-recovery", "secondary", "refresh") + btn("Desactivar", "mfa-disable", "text-button", "lock") : btn("Activar doble factor", "mfa-setup", "primary", "shield")}</div></div></section>`;
}

function conservacionCuenta() {
  const p = state.privacidad;
  if (!p) return "";
  const personal = state.user.role === "customer";
  const estados = {
    active: "Cuenta activa",
    debt: "Con deuda pendiente",
    waiting: "En plazo de conservación",
    ready: "Listo para retirar",
    removed: "Datos retirados",
  };
  const propio = p.customers[0];
  return `<section class="panel account-section" aria-labelledby="privacy-title"><div class="account-section-head"><div><h2 id="privacy-title">${personal ? "Tus datos personales" : "Conservación de datos"}</h2><p>${personal ? "Consulta, descarga o solicita el retiro de tu información." : "Un plazo definido para cada cliente que deja de operar con el negocio."}</p></div>${btn("Leer política", "privacy", "secondary", "shield")}</div>
    <div class="retention-description"><strong>${personal ? (propio?.retention_days ?? p.retention_days) : p.retention_days} días de conservación</strong><p>El plazo empieza con la baja o la solicitud de retiro. Si hay operaciones posteriores, se cuenta desde la última. Las cuotas pendientes bloquean el retiro; al liquidarlas, el plazo comienza desde ese pago.</p><p>Se retiran el nombre, documento y datos de contacto. Los importes, cronogramas y pagos permanecen sin esos datos personales.</p>${p.contact ? `<p>Contacto de la tienda: <strong>${esc(p.contact)}</strong></p>` : ""}</div>
    ${
      personal
        ? `<div class="privacy-personal"><p>${badge(estados[propio?.status] || "Cuenta activa", propio?.status === "debt" ? "warning" : "neutral")}${propio?.scheduled_at ? ` <span>Retiro previsto: ${esc(fmtDate(propio.scheduled_at))}</span>` : ""}</p><div class="filter-row">${btn("Descargar mis datos", "privacy-export", "secondary", "download")}${propio?.requested_at ? btn("Cancelar solicitud", "privacy-cancel", "secondary", "close") : btn("Solicitar retiro de datos", "privacy-request", "secondary", "shield")}</div>${propio?.requested_at ? '<p class="muted">Puedes consultar tu cuenta y pagar lo pendiente. Las nuevas compras están suspendidas mientras mantengas esta solicitud.</p>' : ""}</div>`
        : `<form id="privacy-policy-form" class="retention-form"><div class="form-grid">${inputField("retention_days", "Plazo en días", "number", p.retention_days, false, "Entre 30 y 3650 días. Un aumento solo se aplica a clientes nuevos.", 'min="30" max="3650" step="1"')}${inputField("contact", "Contacto de privacidad (opcional)", "text", p.contact, false, "Correo o teléfono para solicitudes y correcciones.", 'maxlength="120"')}${inputField("password", "Confirma tu contraseña", "password", "", true, "Para guardar la política de esta tienda.", 'autocomplete="current-password" maxlength="128"')}</div><div id="privacy-policy-error" class="error-text" role="alert"></div><button type="submit" class="btn secondary">Guardar política</button></form>
    <div class="privacy-queue"><div class="account-section-head"><div><h3>Bajas y solicitudes</h3><p>El sistema revisa los plazos cada hora.</p></div>${p.customers.some((c) => c.status === "ready") ? btn("Procesar retiros disponibles", "privacy-process", "secondary", "check") : ""}</div>${p.customers.length ? `<div class="table-wrap"><table><thead><tr><th>Cliente</th><th>Situación</th><th>Retiro previsto</th></tr></thead><tbody>${p.customers.map((c) => `<tr><td>${esc(c.name)}</td><td>${esc(estados[c.status])}${c.pending_dues ? `<small class="table-note">${Number(c.pending_dues)} cuotas pendientes</small>` : ""}</td><td>${c.scheduled_at ? esc(fmtDate(c.scheduled_at)) : c.removed_at ? esc(fmtDate(c.removed_at)) : "Después de liquidar la deuda"}</td></tr>`).join("")}</tbody></table></div>` : '<p class="muted">No hay bajas ni solicitudes pendientes.</p>'}</div>`
    }</section>`;
}

function campoConfirmacion(conCodigo = false) {
  return `<div class="form-grid">${inputField("password", "Contraseña actual", "password", "", true, "", 'autocomplete="current-password" maxlength="128"')}${conCodigo ? inputField("codigo", "Código del autenticador o de recuperación", "text", "", true, "Cada código se puede utilizar una sola vez.", 'autocomplete="one-time-code" maxlength="40" spellcheck="false" autocapitalize="characters"') : ""}</div>`;
}

function modalSeguridad(accion) {
  const datos = {
    "mfa-setup": [
      "Activa el doble factor",
      "Primero confirma tu contraseña. Después conectarás tu autenticador.",
      false,
      "Continuar",
    ],
    "mfa-recovery": [
      "Renovar códigos de recuperación",
      "Los códigos anteriores dejarán de funcionar. Guarda los nuevos en un lugar seguro.",
      true,
      "Generar nuevos códigos",
    ],
    "mfa-disable": [
      "Desactivar el doble factor",
      "Tu próximo inicio de sesión volverá a usar solo la contraseña y la comprobación de acceso.",
      true,
      "Desactivar doble factor",
    ],
    "privacy-request": [
      "Solicitar el retiro de tus datos",
      "Se suspenderán las nuevas compras. Podrás seguir consultando tu cuenta y pagando hasta que se complete el retiro según la política.",
      false,
      "Enviar solicitud",
    ],
    "privacy-cancel": [
      "Cancelar la solicitud de retiro",
      "Tu información seguirá disponible para administrar tu cuenta. La tienda mantiene el control sobre la activación del crédito.",
      false,
      "Cancelar solicitud",
    ],
    "privacy-process": [
      "Retirar los datos con plazo cumplido",
      "Se procesarán únicamente los clientes sin cuotas pendientes cuyo plazo ya venció. Este retiro de datos personales no se puede deshacer.",
      false,
      "Procesar retiros",
    ],
  }[accion];
  modal(
    datos[0],
    datos[1],
    campoConfirmacion(datos[2]),
    accion + "-form",
    datos[3],
  );
  $("#modal").dataset.seguridad = "true";
}

function mostrarCodigos(codigos) {
  modal(
    "Guarda tus códigos de recuperación",
    "Solo se muestran ahora. Cada uno permite entrar una vez si pierdes tu autenticador.",
    `<div class="recovery-codes">${codigos.map((c) => `<code>${esc(c)}</code>`).join("")}</div><p class="muted">Guárdalos en tu gestor de contraseñas o descarga una copia y consérvala en un lugar seguro.</p><button type="button" class="btn secondary" data-action="mfa-download">${icon("download")} Descargar códigos</button>`,
  );
  $("#modal").dataset.seguridad = "true";
}

function descargarTexto(nombre, contenido) {
  const url = URL.createObjectURL(
    new Blob([contenido], { type: "text/plain;charset=utf-8" }),
  );
  const enlace = document.createElement("a");
  enlace.href = url;
  enlace.download = nombre;
  document.body.append(enlace);
  enlace.click();
  enlace.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function enviarConfiguracion(form) {
  if (form.id === "mfa-login-form") {
    try {
      const respuesta = await api("/login/mfa", {
        method: "POST",
        body: JSON.stringify({
          token: state.mfaToken,
          codigo: form.elements.codigo.value,
        }),
      });
      state.mfaToken = null;
      state.user = respuesta.user;
      state.csrf = respuesta.csrf;
      await loadData();
      state.page = location.hash.slice(1) || "dashboard";
      renderShell();
    } catch (error) {
      $("#mfa-error").textContent = error.message;
      form.elements.codigo.select();
    }
    return true;
  }
  if (form.id === "privacy-policy-form") {
    const datos = formObject(form);
    datos.retention_days = Number(datos.retention_days);
    await api("/privacy/policy" + scope(), {
      method: "POST",
      body: JSON.stringify(datos),
    });
    await refresh();
    toast("Política de conservación guardada.");
    return true;
  }
  const acciones = [
    "mfa-setup",
    "mfa-confirm",
    "mfa-recovery",
    "mfa-disable",
    "privacy-request",
    "privacy-cancel",
    "privacy-process",
  ];
  const accion = form.id.replace(/-form$/, "");
  if (!acciones.includes(accion)) return false;
  const ruta = accion.startsWith("mfa-")
    ? "/security/mfa/" + accion.slice(4)
    : "/privacy/" + accion.slice(8) + scope();
  const respuesta = await api(ruta, {
    method: "POST",
    body: JSON.stringify(formObject(form)),
  });
  if (accion === "mfa-setup") {
    modal(
      "Conecta tu autenticador",
      "Escanea el QR desde tu app y escribe el código de seis dígitos para terminar. Tienes 10 minutos.",
      `<div class="authenticator-setup"><img src="${esc(respuesta.qr)}" width="240" height="240" alt="Código QR para conectar tu autenticador"><div><h3>¿Estás en el mismo celular?</h3><p>Agrega una cuenta manualmente en tu autenticador con esta clave.</p><code class="manual-secret">${esc(respuesta.secret)}</code><p class="muted">Nombre: Cuenta Clara · ${esc(state.user.username)}<br>Código basado en tiempo, 6 dígitos.</p></div></div>${inputField("codigo", "Código del autenticador", "text", "", true, "No compartas el QR ni esta clave.", 'inputmode="numeric" pattern="[0-9]{6}" maxlength="6" autocomplete="one-time-code"')}`,
      "mfa-confirm-form",
      "Activar doble factor",
    );
    $("#modal").dataset.seguridad = "true";
    return true;
  }
  await loadData();
  renderPage();
  if (respuesta.recovery_codes) mostrarCodigos(respuesta.recovery_codes);
  else {
    $("#modal").close();
    toast(
      accion === "privacy-process"
        ? `Se retiraron los datos de ${respuesta.processed} clientes.`
        : "El cambio quedó guardado.",
    );
  }
  return true;
}

function mostrarVerificacionMfa(token) {
  state.mfaToken = token;
  $(".login-form-box").innerHTML =
    `<div class="mfa-login-heading">${icon("shield")}<h2>Confirma que eres tú</h2></div><p>Abre tu autenticador e ingresa el código de Cuenta Clara.</p><form id="mfa-login-form"><div class="field"><label for="mfa-code">Código de verificación</label><input id="mfa-code" class="otp-input" name="codigo" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required aria-describedby="mfa-help"><small id="mfa-help">El código cambia cada 30 segundos. Usa uno que aún no hayas enviado.</small></div><div id="mfa-error" class="error-text" role="alert"></div><button type="submit" class="btn primary">Verificar y entrar</button></form><div class="login-recovery"><button type="button" class="text-button" data-action="mfa-use-recovery">Usar un código de recuperación</button><button type="button" class="text-button" data-action="mfa-back">Volver al inicio de sesión</button></div>`;
  $("#mascot-message").textContent = "Una última comprobación y entramos.";
  $("#mfa-code").focus();
}

async function accionCuenta(accion) {
  if (
    [
      "mfa-setup",
      "mfa-recovery",
      "mfa-disable",
      "privacy-request",
      "privacy-cancel",
      "privacy-process",
    ].includes(accion)
  ) {
    modalSeguridad(accion);
    return true;
  }
  if (accion === "mfa-back") {
    state.mfaToken = null;
    renderLogin();
    return true;
  }
  if (accion === "mfa-use-recovery") {
    const campo = $("#mfa-code");
    campo.value = "";
    campo.inputMode = "text";
    campo.maxLength = 40;
    campo.removeAttribute("pattern");
    campo.classList.remove("otp-input");
    $("label[for=mfa-code]").textContent = "Código de recuperación";
    $("#mfa-help").textContent =
      "Escribe uno de los códigos que guardaste al activar el doble factor. Después de usarlo, dejará de funcionar.";
    $("[data-action=mfa-use-recovery]").hidden = true;
    campo.focus();
    return true;
  }
  if (accion === "mfa-download") {
    descargarTexto(
      "cuenta-clara-recuperacion.txt",
      "Cuenta Clara - " +
        state.user.username +
        "\nCódigos de un solo uso. Guárdalos en un lugar seguro.\n\n" +
        [...document.querySelectorAll(".recovery-codes code")]
          .map((c) => c.textContent)
          .join("\n"),
    );
    return true;
  }
  if (accion === "privacy-export") {
    const datos = await api("/privacy/export");
    descargarTexto(
      "mis-datos-cuenta-clara.json",
      JSON.stringify(datos, null, 2),
    );
    return true;
  }
  return false;
}

function selectorImagen(valor = "") {
  return `<fieldset class="image-field"><legend>Imagen del producto</legend><input type="hidden" name="image" value="${esc(valor)}"><div class="image-upload-row"><div class="image-preview" id="product-image-preview">${valor ? `<img src="${esc(valor)}" alt="Imagen seleccionada">` : icon("box")}</div><div><label class="btn secondary" for="product-image-file">${icon("plus")} Subir foto</label><input id="product-image-file" type="file" accept="image/jpeg,image/png" class="screen-reader" aria-describedby="product-image-help"><p id="product-image-help">JPG o PNG, hasta 5 MB y 12 megapíxeles.</p><p id="image-upload-status" role="status">${valor ? "Imagen seleccionada." : "Selecciona una foto o una imagen de referencia."}</p></div></div><details class="image-library"><summary>Elegir una imagen de referencia</summary><p>Imágenes de muestra. Sube una foto propia para identificar tu producto.</p><div class="image-choices">${[
    ["arroz", "Arroz"],
    ["aceite", "Aceite"],
    ["leche", "Leche"],
    ["reparto", "Reparto"],
    ["producto", "Producto"],
  ]
    .map(
      ([nombre, etiqueta]) =>
        `<button type="button" data-imagen="/catalogo/${nombre}.jpg" aria-label="Seleccionar imagen de ${etiqueta}" aria-pressed="${valor === `/catalogo/${nombre}.jpg`}"><img src="/catalogo/${nombre}.jpg" width="64" height="64" alt=""><span>${etiqueta}</span></button>`,
    )
    .join(
      "",
    )}</div><a href="/catalogo/creditos.html" target="_blank" rel="noopener">Créditos de las fotografías</a></details></fieldset>`;
}

function elegirImagen(valor) {
  const form = $("#product-form");
  if (!form) return;
  form.elements.image.value = valor;
  $("#product-image-preview").innerHTML =
    `<img src="${esc(valor)}" alt="Imagen seleccionada">`;
  $("#image-upload-status").textContent = "Imagen seleccionada.";
  document
    .querySelectorAll("[data-imagen]")
    .forEach((b) =>
      b.setAttribute("aria-pressed", String(b.dataset.imagen === valor)),
    );
}

document.addEventListener("click", (event) => {
  const boton = event.target.closest("[data-imagen]");
  if (boton) elegirImagen(boton.dataset.imagen);
});
document.addEventListener("change", async (event) => {
  if (event.target.id !== "product-image-file") return;
  const archivo = event.target.files[0],
    form = $("#product-form");
  if (!archivo || !form) return;
  const estado = $("#image-upload-status"),
    enviar = $('[type="submit"]', form);
  if (
    !["image/jpeg", "image/png"].includes(archivo.type) ||
    archivo.size > 5 * 1024 * 1024
  ) {
    estado.textContent = "Selecciona un JPG o PNG de hasta 5 MB.";
    event.target.value = "";
    return;
  }
  enviar.disabled = true;
  form.dataset.uploading = "true";
  event.target.disabled = true;
  form.querySelectorAll("[data-imagen]").forEach((b) => (b.disabled = true));
  estado.textContent = "Subiendo imagen…";
  try {
    const datos = new FormData();
    datos.append("file", archivo);
    const resultado = await api("/product-images" + scope(), {
      method: "POST",
      body: datos,
    });
    if (document.contains(form)) elegirImagen(resultado.image);
  } catch (error) {
    if (document.contains(form)) estado.textContent = error.message;
  } finally {
    enviar.disabled = false;
    delete form.dataset.uploading;
    event.target.disabled = false;
    event.target.value = "";
    form.querySelectorAll("[data-imagen]").forEach((b) => (b.disabled = false));
  }
});
document.getElementById("modal").addEventListener("close", () => {
  const dialogo = document.getElementById("modal");
  if (dialogo.dataset.seguridad) {
    dialogo.innerHTML = "";
    delete dialogo.dataset.seguridad;
    const titulo = document.getElementById("page-title");
    if (titulo) {
      titulo.setAttribute("tabindex", "-1");
      titulo.focus({ preventScroll: true });
    }
  }
});
