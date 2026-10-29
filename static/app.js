"use strict";
const $ = (s, root = document) => root.querySelector(s);
const $$ = (s, root = document) => [...root.querySelectorAll(s)];
const state = {
  user: null,
  csrf: "",
  data: null,
  page: "dashboard",
  storeId: "",
  challenge: null,
  cart: [{ product_id: "", quantity: "1" }],
  plan: null,
  payment: null,
  statement: null,
  search: "",
  filter: "all",
  busy: false,
};
const icon = (name, cls = "") =>
  `<svg class="icon ${cls}" viewBox="0 0 24 24" aria-hidden="true" focusable="false">${iconShapes[name] || iconShapes.file}</svg>`;
const esc = (v) =>
  String(v ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const amt = (v, c = "PEN") =>
  new Intl.NumberFormat("es-PE", { style: "currency", currency: c }).format(
    Number(v || 0),
  );
const fmtDate = (v) =>
  v
    ? new Intl.DateTimeFormat("es-PE", {
        day: "2-digit",
        month: "short",
        year: "numeric",
      }).format(new Date(String(v).slice(0, 10) + "T12:00:00"))
    : "—";
const initials = (v) =>
  String(v || "?")
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((x) => x[0])
    .join("")
    .toUpperCase();
const today = () => state.data?.today || new Date().toLocaleDateString("en-CA");
const live = (v) => Number(v.active) !== 0;
const canManage = () => state.user?.role !== "customer";
const scope = () =>
  state.user?.role === "platform" && state.storeId
    ? "?store_id=" + encodeURIComponent(state.storeId)
    : "";
const getCustomer = (id) =>
  state.data.customers.find((c) => Number(c.id) === Number(id));
const outstanding = (id) =>
  state.data.dues
    .filter((d) => Number(d.customer_id) === Number(id) && !d.payment_id)
    .reduce((s, d) => s + Number(d.principal), 0);
const available = (c) =>
  c.available !== undefined
    ? Number(c.available)
    : Math.max(0, Number(c.credit_limit) - outstanding(c.id));
const person = (name, sub = "", i = 0) =>
  '<div class="person"><span class="avatar ' +
  ["", "alt", "blue", "pink"][i % 4] +
  '">' +
  esc(initials(name)) +
  "</span><div><strong>" +
  esc(name) +
  "</strong><small>" +
  esc(sub) +
  "</small></div></div>";
const badge = (label, kind = "") =>
  '<span class="badge ' + kind + '">' + esc(label) + "</span>";
const empty = (title, copy, action = "", compact = false) =>
  '<div class="empty ' +
  (compact ? "compact" : "") +
  '"><div class="empty-icon">' +
  icon("file") +
  "</div><h3>" +
  esc(title) +
  "</h3><p>" +
  esc(copy) +
  "</p>" +
  action +
  "</div>";
const btn = (text, action, kind = "primary", ico = "plus", extra = "") =>
  '<button class="btn ' +
  kind +
  '" data-action="' +
  action +
  '" ' +
  extra +
  ">" +
  icon(ico) +
  esc(text) +
  "</button>";
function toast(message, error = false) {
  const el = document.createElement("div");
  el.className = "toast" + (error ? " error" : "");
  el.innerHTML =
    icon(error ? "info" : "check") + "<span>" + esc(message) + "</span>";
  const container = $("#toasts");
  while (container.children.length >= 2) container.firstElementChild.remove();
  container.append(el);
  setTimeout(() => el.remove(), 4500);
}
async function api(path, options = {}) {
  const response = await fetch("/api" + path, {
    credentials: "same-origin",
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(state.csrf ? { "X-CSRF-Token": state.csrf } : {}),
      ...(options.headers || {}),
    },
  });
  let data;
  try {
    data = await response.json();
  } catch {
    throw Error("El servidor no respondió como esperábamos. Intenta otra vez.");
  }
  if (!response.ok) {
    if (response.status === 401 && state.user) {
      state.user = null;
      renderLogin();
    }
    throw Error(data.error || "No se pudo completar la operación.");
  }
  return data;
}
async function loadData() {
  state.data = await api("/data" + scope());
  state.user = state.data.user || state.user;
  if (state.user.role === "platform" && !state.storeId && state.data.store)
    state.storeId = String(state.data.store.id);
  return state.data;
}
async function refresh() {
  await loadData();
  renderShell();
}
function pageHead(title, description, action = "") {
  return `<header class="page-head"><div><h1 id="page-title">${esc(title)}</h1><p>${esc(description)}</p></div>${action}</header>`;
}
function brand() {
  return `<div class="brand" translate="no"><span class="brand-mark" aria-hidden="true">${icon("logo")}</span><span class="brand-wordmark">cuenta<span>clara</span><small>El crédito de tu negocio</small></span></div>`;
}
const navItems = [
  ["dashboard", "Resumen", "grid"],
  ["customers", "Clientes", "users"],
  ["products", "Catálogo", "box"],
  ["sales", "Ventas a crédito", "bag"],
  ["payments", "Cobros y pagos", "wallet"],
  ["statements", "Estados de cuenta", "file"],
  ["stores", "Tiendas", "store"],
  ["audit", "Movimientos", "clock"],
  ["settings", "Configuración", "settings"],
];
function allowedPages() {
  return navItems.filter(([p]) =>
    state.user.role === "customer"
      ? ["dashboard", "statements", "settings"].includes(p)
      : state.user.role === "platform"
        ? true
        : p !== "stores",
  );
}
function renderShell() {
  if (!allowedPages().some(([p]) => p === state.page)) state.page = "dashboard";
  const title = navItems.find(([p]) => p === state.page)?.[1] || "Resumen";
  const roles = {
    platform: "Administrador de plataforma",
    merchant: "Administrador del negocio",
    customer: "Cuenta personal",
  };
  const nav = allowedPages()
    .map(
      ([p, label, ico]) =>
        `<button class="nav-item ${state.page === p ? "active" : ""}" data-page="${p}" ${state.page === p ? 'aria-current="page"' : ""}>${icon(ico)}<span>${label}</span>${p === "customers" ? `<span class="count">${state.data.customers.filter(live).length}</span>` : ""}</button>`,
    )
    .join("");
  const store = state.data.store;
  const switcher =
    state.user.role === "platform"
      ? `<label class="screen-reader" for="store-switch">Tienda activa</label><select id="store-switch" class="store-select" title="Elige el negocio cuyos datos vas a administrar."><option value="">Selecciona una tienda</option>${state.data.stores.map((s) => `<option value="${s.id}" ${String(s.id) === String(state.storeId) ? "selected" : ""}>${esc(s.name)}${live(s) ? "" : " (inactiva)"}</option>`).join("")}</select>`
      : `<strong>${esc(store?.name || "Mi cuenta")}</strong><small>${esc(store?.business || "Consulta de crédito")}</small>`;
  $("#app").innerHTML = `<div class="app-shell">
    <aside class="sidebar" id="navigation" aria-label="Navegación principal">
      ${brand()}
      <div class="store-context">${icon("store")}<div>${switcher}</div></div>
      <nav>${nav}</nav>
      <div class="sidebar-bottom">
        <button class="nav-item" data-action="help">${icon("help")}<span>Ayuda y soporte</span></button>
        <div class="user-box"><span class="avatar">${esc(initials(state.user.name))}</span><div><strong>${esc(state.user.name)}</strong><small>${roles[state.user.role]}</small></div><button class="icon-button" data-action="logout" aria-label="Cerrar sesión" title="Cerrar sesión">${icon("logout")}</button></div>
      </div>
    </aside>
    <div class="workspace">
      <header class="topbar"><div class="breadcrumbs"><button class="icon-button mobile-menu" data-action="toggle-menu" aria-label="Abrir menú" aria-controls="navigation" aria-expanded="false">${icon("menu")}</button><span>Mi negocio</span><span class="breadcrumb-separator">/</span><strong>${esc(title)}</strong></div><div class="top-actions"><span class="top-date">${esc(fmtDate(today()))}</span><button class="icon-button" data-action="refresh" aria-label="Actualizar datos" title="Actualizar datos">${icon("refresh")}</button></div></header>
      <main class="main" id="main" tabindex="-1"><div id="page"></div><footer class="footer"><span>Cuenta Clara</span><span>Importes en la moneda de cada crédito</span></footer></main>
    </div>
  </div>`;
  renderPage();
  syncNavigation();
}
function syncNavigation() {
  const sidebar = $(".sidebar");
  if (!sidebar) return;
  const mobile = window.matchMedia("(max-width: 760px)").matches;
  sidebar.inert = mobile && !sidebar.classList.contains("open");
  if (!mobile) {
    sidebar.classList.remove("open");
    $(".sidebar-mask")?.remove();
  }
  $("[data-action=toggle-menu]")?.setAttribute(
    "aria-expanded",
    String(sidebar.classList.contains("open")),
  );
}
window.addEventListener("resize", syncNavigation);
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && $(".sidebar.open") && !$("#modal").open) {
    $(".sidebar").classList.remove("open");
    $(".sidebar-mask")?.remove();
    syncNavigation();
    $("[data-action=toggle-menu]")?.focus();
  }
});
function renderPage() {
  const el = $("#page");
  if (!el) return;
  const pages = {
    dashboard: dashboard,
    customers: customersPage,
    products: productsPage,
    sales: salesPage,
    payments: paymentsPage,
    statements: statementsPage,
    stores: storesPage,
    audit: auditPage,
    settings: settingsPage,
  };
  el.innerHTML = (pages[state.page] || dashboard)();
  if (state.page === "sales") {
    renderCart();
    $("#sale-form [name=months]")?.toggleAttribute(
      "disabled",
      $("#sale-form [name=mode]")?.value === "end",
    );
  }
  if (state.page === "statements" && state.statement) renderStatement();
  if (state.page === "payments" && state.payment) renderPayment();
}
function navigate(page) {
  state.page = page;
  state.search = "";
  state.filter = "all";
  state.statement = null;
  state.payment = null;
  state.plan = null;
  renderShell();
  window.scrollTo(0, 0);
  $("#main")?.focus({ preventScroll: true });
}
function noStore() {
  return state.user.role === "platform" && !state.data.store
    ? '<div class="panel">' +
        empty(
          "Primero, tu tienda",
          "Registra un negocio para empezar con sus clientes y ventas.",
          btn("Registrar tienda", "new-store"),
        ) +
        "</div>"
    : "";
}
function dashboard() {
  const d = state.data;
  const own = state.user.role === "customer";
  const currency =
    state.metricCurrency || d.customers.find(live)?.currency || "PEN";
  const pending = d.dues.filter(
    (x) => !x.payment_id && x.currency === currency,
  );
  const due = pending.filter((x) => x.due_date <= today());
  const paid = d.payments.filter(
    (x) =>
      x.currency === currency && x.paid_at.slice(0, 7) === today().slice(0, 7),
  );
  const sum = (rows) =>
    rows.reduce((s, x) => s + Number(x.total ?? x.amount), 0);
  const monthlySales = d.sales.filter(
    (x) =>
      x.currency === currency &&
      x.purchased_at.slice(0, 7) === today().slice(0, 7),
  );
  const count = new Set(due.map((x) => x.customer_id)).size;
  const mode = state.dueFilter || "all";
  const visible = pending
    .filter(
      (x) =>
        mode === "all" ||
        (mode === "due" ? x.due_date <= today() : x.due_date > today()),
    )
    .sort((a, b) => a.due_date.localeCompare(b.due_date));
  const filters = [
    ["all", "Todos", pending.length],
    ["due", "Vencidos y hoy", due.length],
    ["upcoming", "Próximos", pending.length - due.length],
  ];
  const month = new Date(today() + "T12:00:00").toLocaleDateString("es-PE", {
    month: "long",
  });
  return (
    pageHead(
      own ? "Mi cuenta" : "Resumen del negocio",
      own
        ? "Tus compras y fechas de pago, siempre a la vista."
        : "Revisa tus créditos y continúa con el día.",
      canManage()
        ? `<div class="filter-row">${btn("Registrar cobro", "go-payments", "secondary", "wallet")}${btn("Nueva venta", "go-sales")}</div>`
        : "",
    ) +
    `
    <section id="dashboard">
      ${noStore()}
      <div class="overview">
        <div class="overview-heading"><h2>${own ? "Tu crédito" : "Estado de la cartera"}</h2><label class="currency-picker" for="metric-currency">Moneda<select id="metric-currency" title="Muestra los saldos de una moneda; no convierte importes."><option value="PEN" ${currency === "PEN" ? "selected" : ""}>Soles (PEN)</option><option value="USD" ${currency === "USD" ? "selected" : ""}>Dólares (USD)</option></select></label></div>
        <div class="metrics">
          <div class="metric main-metric"><span>${own ? "Total pendiente" : "Por cobrar"}</span><strong>${esc(amt(sum(pending), currency))}</strong><small>${pending.length} cuotas pendientes</small></div>
          <div class="metric"><span>Vencido y por pagar hoy</span><strong class="${due.length ? "bad" : ""}">${esc(amt(sum(due), currency))}</strong><small>${count} ${count === 1 ? "cliente" : "clientes"} con pagos exigibles</small></div>
          <div class="metric"><span>${own ? "Pagado" : "Cobrado"} en ${esc(month)}</span><strong>${esc(amt(sum(paid), currency))}</strong><small>${paid.length} ${paid.length === 1 ? "pago registrado" : "pagos registrados"}</small></div>
          <div class="metric"><span>${own ? "Compras" : "Ventas"} en ${esc(month)}</span><strong>${esc(amt(sum(monthlySales), currency))}</strong><small>${monthlySales.length} ${monthlySales.length === 1 ? "venta a crédito" : "ventas a crédito"}</small></div>
        </div>
      </div>
      <div class="dashboard-grid">
        <section class="panel collection-panel">
          <div class="panel-head"><div><h2>${own ? "Mis próximos pagos" : "Pendientes de cobro"}</h2><p>Cuotas ordenadas por fecha de vencimiento.</p></div><span class="ledger-symbol" aria-hidden="true">${icon("file")}</span></div>
          <div class="ledger-tabs" role="group" aria-label="Filtrar cuotas">${filters.map(([key, label, n]) => `<button data-action="filter-dues" data-filter="${key}" aria-pressed="${key === mode}" class="ledger-tab ${key === mode ? "selected" : ""}">${label}<span>${n}</span></button>`).join("")}</div>
          ${collectionTable(visible.slice(0, 8), own)}
          <div class="panel-bottom"><span>${visible.length > 8 ? "8 de " : ""}${visible.length} cuotas en ${currency}</span><button class="text-button" data-page="statements">Estados de cuenta ${icon("chevron")}</button></div>
        </section>
        <aside class="dashboard-aside">
          <section class="activity-panel"><div class="aside-head"><h2>Actividad reciente</h2>${icon("clock")}</div><div class="activity">${recentActivity()}</div></section>
          <section class="sales-chart"><div class="aside-head"><h2>Ventas a crédito</h2><span>${currency}</span></div>${chart(d.sales, currency)}</section>
        </aside>
      </div>
      <div class="dashboard-note">${icon("info")}<p>Los saldos incluyen los intereses por mora calculados a hoy. Abre un estado de cuenta para revisar el detalle de cada compra.</p></div>
    </section>`
  );
}
function collectionTable(rows, own) {
  if (!rows.length)
    return empty(
      "No hay cuotas en esta vista",
      "Elige otro filtro o revisa el estado de cuenta del cliente.",
      "",
      true,
    );
  return `<div class="table-wrap"><table class="collection-table"><thead><tr><th>${own ? "Compra" : "Cliente"}</th><th>Vencimiento</th><th class="text-right">Importe</th><th class="text-right">Acción</th></tr></thead><tbody>${rows
    .map((d) => {
      const overdue = d.due_date < today();
      const dueToday = d.due_date === today();
      return `<tr><td>${person(d.customer_name || getCustomer(d.customer_id)?.name || state.user.name, "Venta #" + d.sale_id + " · Cuota " + d.number)}</td><td><span class="due-date">${esc(fmtDate(d.due_date))}</span>${badge(overdue ? "Vencido" : dueToday ? "Vence hoy" : "Por vencer", overdue ? "danger" : dueToday ? "warn" : "neutral")}</td><td class="money text-right">${esc(amt(d.total ?? d.amount, d.currency))}</td><td class="text-right"><button class="row-action" data-action="${own || (!overdue && !dueToday) ? "customer-statement" : "collect-customer"}" data-id="${d.customer_id}" aria-label="${own || (!overdue && !dueToday) ? "Ver estado de cuenta" : "Consultar cobro de " + esc(d.customer_name || getCustomer(d.customer_id)?.name || "cliente")}">${own || (!overdue && !dueToday) ? "Ver cuenta" : "Ver cobro"}${icon("chevron")}</button></td></tr>`;
    })
    .join("")}</tbody></table></div>`;
}
function chart(sales, currency) {
  const now = new Date(today() + "T12:00:00");
  const columns = [];
  for (let i = 5; i >= 0; i--) {
    const dt = new Date(now.getFullYear(), now.getMonth() - i, 1);
    const key =
      dt.getFullYear() + "-" + String(dt.getMonth() + 1).padStart(2, "0");
    columns.push({
      label: dt.toLocaleDateString("es-PE", { month: "short" }),
      value: sales
        .filter(
          (s) => s.currency === currency && s.purchased_at.slice(0, 7) === key,
        )
        .reduce((a, s) => a + Number(s.amount), 0),
    });
  }
  const max = Math.max(1, ...columns.map((c) => c.value));
  return `<p class="chart-caption">Últimos 6 meses</p><div class="chart" role="img" aria-label="${esc(columns.map((c) => c.label + ": " + amt(c.value, currency)).join("; "))}">${columns.map((c) => `<div class="chart-column"><span class="chart-value">${esc(amt(c.value, currency))}</span><div class="chart-bar" style="height:${(c.value / max) * 94}px" title="${esc(amt(c.value, currency))}"></div><small>${esc(c.label)}</small></div>`).join("")}</div><p class="chart-label">Importe de compras, sin intereses.</p>`;
}
function recentActivity() {
  const sales = state.data.sales.map((s) => ({
    type: "bag",
    name: s.customer_name,
    label: "Venta registrada",
    date: s.purchased_at,
    amount: s.amount,
    currency: s.currency,
  }));
  const payments = state.data.payments.map((p) => ({
    type: "check",
    name: p.customer_name || getCustomer(p.customer_id)?.name || "Cliente",
    label: "Pago recibido",
    date: p.paid_at,
    amount: p.amount,
    currency: p.currency,
  }));
  const rows = [...sales, ...payments]
    .sort((a, b) => b.date.localeCompare(a.date))
    .slice(0, 4);
  return rows.length
    ? rows
        .map(
          (a) =>
            `<div class="activity-row"><span class="activity-icon ${a.type === "check" ? "received" : ""}">${icon(a.type)}</span><div><strong>${esc(a.label)}</strong><p>${esc(a.name)}</p><small>${esc(fmtDate(a.date))}</small></div><span class="amount">${esc(amt(a.amount, a.currency))}</span></div>`,
        )
        .join("")
    : empty("Sin movimientos", "Tus ventas y pagos aparecerán aquí.", "", true);
}

function rateLabel(terms, late = false) {
  const rate = terms[late ? "late_rate" : "rate"];
  if (rate === undefined) return "Ver acuerdo original";
  const kind = terms[late ? "late_kind" : "rate_kind"];
  const period = Number(terms[late ? "late_period" : "rate_period"]);
  const frequency = Number(terms[late ? "late_frequency" : "frequency"]);
  const cadence = {
    1: "anual",
    2: "semestral",
    4: "trimestral",
    6: "bimestral",
    12: "mensual",
    24: "quincenal",
    360: "diaria",
  };
  return `${rate}% ${kind === "effective" ? "efectiva" : "nominal"} ${period === 360 ? "anual" : "mensual"}${kind === "nominal" ? ", capitalización " + (period === 30 ? "mensual" : cadence[frequency]) : ""}`;
}
function agreedTerms(row) {
  return row.late_rate !== undefined
    ? row
    : (state.statement?.sales || state.data?.sales || []).find(
        (s) => Number(s.id) === Number(row.sale_id),
      )?.terms || {};
}
function lateBreakdown(rows) {
  const late = rows.filter((d) => Number(d.late_days) > 0);
  if (!late.length) return "";
  return `<section class="late-breakdown"><h3>Intereses por mora</h3><p class="muted">Desglose del cargo ya incluido en cada cuota. Se aplica sobre la obligación vencida completa.</p>${late.map((d) => `<article class="late-entry"><header><strong>Venta #${esc(d.sale_id)} · Cuota ${esc(d.number)}</strong><span class="money">${esc(amt(d.late_amount, d.currency))}</span></header><dl><div><dt>Base vencida</dt><dd>${esc(amt(d.late_base ?? d.amount, d.currency))}</dd></div><div><dt>Vencimiento</dt><dd>${esc(fmtDate(d.due_date))}</dd></div><div><dt>Calculado hasta</dt><dd>${esc(fmtDate(d.late_as_of || d.paid_at || state.statement?.date || today()))}</dd></div><div><dt>Días de mora (30/360)</dt><dd>${esc(d.late_days)} días</dd></div><div class="late-rate"><dt>Tasa pactada en esta compra</dt><dd>${esc(rateLabel(agreedTerms(d), true))}</dd></div></dl></article>`).join("")}</section>`;
}

function duesTable(rows, short = false, asOf = today()) {
  if (!rows.length)
    return empty(
      "Estás al día",
      "No hay cuotas para mostrar en esta vista.",
      "",
      true,
    );
  return (
    '<div class="table-wrap"><table><thead><tr><th>Cliente / cuota</th><th>Vencimiento</th><th>Estado</th>' +
    (!short ? "<th>Capital</th><th>Interés</th><th>Mora</th>" : "") +
    '<th class="text-right">Total</th></tr></thead><tbody>' +
    rows
      .map(
        (d, i) =>
          "<tr><td>" +
          person(
            d.customer_name ||
              getCustomer(d.customer_id)?.name ||
              state.user.name,
            "Venta #" + d.sale_id + " · Cuota " + d.number,
            i,
          ) +
          "</td><td>" +
          esc(fmtDate(d.due_date)) +
          "</td><td>" +
          badge(
            d.payment_id
              ? "Pagado"
              : d.due_date < asOf
                ? "Vencido"
                : d.due_date === asOf
                  ? "Vence hoy"
                  : "Pendiente",
            d.payment_id
              ? ""
              : d.due_date < asOf
                ? "danger"
                : d.due_date === asOf
                  ? "warn"
                  : "neutral",
          ) +
          "</td>" +
          (!short
            ? '<td class="money">' +
              esc(amt(d.principal, d.currency)) +
              "</td><td>" +
              esc(amt(d.interest, d.currency)) +
              '<small class="interest-detail">' +
              esc(d.interest_days ?? (d.number > 1 ? 30 : "—")) +
              ' días</small><small class="interest-detail">' +
              esc(rateLabel(agreedTerms(d))) +
              "</small>" +
              "</td><td>" +
              esc(amt(d.late_amount, d.currency)) +
              "</td>"
            : "") +
          '<td class="money text-right">' +
          esc(
            amt(
              d.total || Number(d.amount) + Number(d.late_amount || 0),
              d.currency,
            ),
          ) +
          "</td></tr>",
      )
      .join("") +
    "</tbody></table></div>"
  );
}
function searchTools(placeholder, filters = true) {
  return (
    '<div class="section-tools"><div class="search-box">' +
    icon("search") +
    '<input id="search" type="search" title="Escribe parte del nombre, documento, correo, descripción o marca para filtrar." placeholder="' +
    esc(placeholder) +
    '" aria-label="' +
    esc(placeholder) +
    '" value="' +
    esc(state.search) +
    '"></div><div class="filter-row">' +
    (filters
      ? '<label class="screen-reader" for="status-filter">Estado</label><select id="status-filter" title="Filtra los registros activos o desactivados."><option value="all">Todos los estados</option><option value="active" ' +
        (state.filter === "active" ? "selected" : "") +
        '>Activos</option><option value="inactive" ' +
        (state.filter === "inactive" ? "selected" : "") +
        ">Inactivos</option></select>"
      : "") +
    btn("Exportar CSV", "export", "secondary", "down") +
    "</div></div>"
  );
}
function filtered(rows) {
  return rows.filter(
    (x) =>
      (state.filter === "all" ||
        (state.filter === "active" && live(x)) ||
        (state.filter === "inactive" && !live(x))) &&
      [x.name, x.document, x.email, x.description, x.brand].some((v) =>
        String(v || "")
          .toLowerCase()
          .includes(state.search.toLowerCase()),
      ),
  );
}
function customersPage() {
  if (noStore())
    return (
      pageHead("Clientes", "Los acuerdos empiezan con las personas.") +
      noStore()
    );
  const rows = filtered(state.data.customers);
  return (
    pageHead(
      "Clientes",
      "Administra sus datos, límites y condiciones de crédito.",
      btn("Nuevo cliente", "new-customer"),
    ) +
    searchTools("Buscar por nombre, documento o correo") +
    '<div class="panel">' +
    (rows.length
      ? '<div class="table-wrap"><table><thead><tr><th>Cliente</th><th>Límite</th><th>Disponible</th><th>Tasa pactada</th><th>Corte / pago</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>' +
        rows
          .map(
            (c, i) =>
              "<tr><td>" +
              person(c.name, c.document + " · " + c.email, i) +
              '</td><td class="money">' +
              esc(amt(c.credit_limit, c.currency)) +
              '</td><td class="money">' +
              esc(amt(available(c), c.currency)) +
              "</td><td>" +
              esc(c.rate) +
              "% " +
              (c.rate_kind === "effective" ? "efectiva" : "nominal") +
              '<small style="display:block;margin-top:4px">' +
              (Number(c.rate_period) === 360 ? "anual" : "mensual") +
              "</small></td><td>" +
              c.cutoff_day +
              " / " +
              c.pay_day +
              "</td><td>" +
              badge(live(c) ? "Activo" : "Inactivo", live(c) ? "" : "neutral") +
              '</td><td><div class="inline-actions"><button class="icon-button" data-action="edit-customer" data-id="' +
              c.id +
              '" aria-label="Editar ' +
              esc(c.name) +
              '" title="Editar">' +
              icon("edit") +
              '</button><button class="text-button" data-action="customer-statement" data-id="' +
              c.id +
              '">Estado de cuenta</button><button class="text-button" data-action="toggle-customer" data-id="' +
              c.id +
              '">' +
              (live(c) ? "Desactivar" : "Activar") +
              "</button></div></td></tr>",
          )
          .join("") +
        "</tbody></table></div>"
      : empty(
          state.search
            ? "No encontramos coincidencias"
            : "Dale la bienvenida a tu primer cliente",
          state.search
            ? "Prueba con otro nombre o documento."
            : "Registra sus datos y acuerda su límite, tasa y fechas de pago.",
          state.search ? "" : btn("Registrar cliente", "new-customer"),
        )) +
    "</div>"
  );
}
function productsPage() {
  if (noStore())
    return (
      pageHead("Catálogo", "Productos y servicios del negocio.") + noStore()
    );
  const rows = filtered(state.data.products);
  return (
    pageHead(
      "Catálogo",
      "Consulta precios y administra lo que vendes.",
      btn("Nuevo producto", "new-product"),
    ) +
    searchTools("Buscar producto, servicio o marca") +
    `<section class="panel">${rows.length ? `<div class="table-wrap"><table class="catalog-table"><thead><tr><th>Producto o servicio</th><th>Al contado</th><th>Al crédito</th><th>Forma de pago</th><th>Estado</th><th class="text-right">Acciones</th></tr></thead><tbody>${rows.map((p) => `<tr><td><div class="catalog-product"><span class="product-thumb">${p.image ? `<img src="${esc(p.image)}" alt="" width="44" height="44" loading="lazy" referrerpolicy="no-referrer">` : icon(p.unit === "servicio" ? "store" : "box")}</span><div><strong>${esc(p.name)}</strong><small>${esc([p.brand, p.unit].filter(Boolean).join(" · "))}</small></div></div></td><td class="money">${esc(amt(p.cash_price, p.currency))}</td><td class="money">${esc(amt(p.credit_price, p.currency))}</td><td>${Number(p.installment_allowed) ? "Al cierre o en cuotas" : "Pago al cierre"}</td><td>${badge(live(p) ? "Activo" : "Inactivo", live(p) ? "" : "neutral")}</td><td><div class="inline-actions"><button class="icon-button" data-action="edit-product" data-id="${p.id}" title="Editar producto" aria-label="Editar ${esc(p.name)}">${icon("edit")}</button><button class="text-button" data-action="toggle-product" data-id="${p.id}">${live(p) ? "Desactivar" : "Activar"}</button></div></td></tr>`).join("")}</tbody></table></div><div class="panel-bottom"><span>${rows.length} productos y servicios</span><span>Precios en su moneda original</span></div>` : empty(state.search ? "Sin coincidencias" : "Agrega tu primer producto", state.search ? "Prueba con otro nombre o marca." : "Registra el precio al contado y al crédito para empezar a vender.", state.search ? "" : btn("Agregar producto", "new-product"))}</section>`
  );
}
function salesPage() {
  if (noStore())
    return (
      pageHead("Ventas a crédito", "Cada compra con sus condiciones claras.") +
      noStore()
    );
  if (!state.data.customers.some(live) || !state.data.products.some(live))
    return (
      pageHead("Ventas a crédito", "Cada compra con sus condiciones claras.") +
      '<div class="panel">' +
      empty(
        "Preparemos tu primera venta",
        "Necesitas al menos un cliente y un producto activos para registrar una compra.",
        '<div class="filter-row" style="justify-content:center">' +
          btn("Agregar cliente", "new-customer") +
          btn("Agregar producto", "new-product", "secondary") +
          "</div>",
      ) +
      "</div>"
    );
  return (
    pageHead(
      "Nueva venta a crédito",
      "Completa la compra y revisa el plan antes de guardarla.",
    ) +
    '<div class="two-columns"><section class="panel form-card"><form id="sale-form"><h2>Datos de la compra</h2><div class="form-grid" style="margin-top:23px">' +
    selectField(
      "customer_id",
      "Cliente",
      state.data.customers
        .filter(live)
        .map((c) => [c.id, c.name + " · " + c.currency]),
      "",
      true,
      "Las condiciones de crédito se toman de su ficha.",
    ) +
    inputField(
      "purchased_at",
      "Fecha y hora de compra",
      "datetime-local",
      new Date(Date.now() - 5 * 3600000 - 60000).toISOString().slice(0, 16),
      true,
      "Se usa para identificar el corte y calcular los días.",
    ) +
    selectField(
      "mode",
      "¿Cómo va a pagar?",
      [
        ["end", "Todo en la fecha de pago"],
        ["installments", "En cuotas mensuales"],
      ],
      "end",
    ) +
    inputField(
      "months",
      "Número de cuotas",
      "number",
      "3",
      false,
      "Solo se usa para pagos en cuotas.",
      'min="1" max="60" step="1"',
    ) +
    '</div><div class="form-section"><h3>Productos de la venta</h3><div id="cart"></div><div class="cart-actions">' +
    btn("Agregar producto", "add-item", "ghost", "plus") +
    '<span class="muted" style="font-size:10px">Se usa el precio al crédito</span></div></div><div class="notice">' +
    icon("info") +
    '<span>Primero revisa el cálculo. El crédito y los precios se validan de nuevo al guardar la venta.</span></div><div class="error-text" id="sale-error" role="alert"></div><button type="submit" class="btn primary">' +
    icon("trend") +
    'Calcular plan de pagos</button></form></section><section id="sale-preview" class="panel">' +
    empty(
      "Vista previa del crédito",
      "Completa la compra y selecciona Calcular plan de pagos para revisar las cuotas.",
    ) +
    '</section></div><section class="panel" style="margin-top:24px"><div class="panel-head"><div><h2>Ventas registradas</h2><p>Las condiciones de una venta guardada no cambian al editar al cliente.</p></div>' +
    btn("Exportar", "export", "secondary small", "down") +
    "</div>" +
    salesTable() +
    "</section>"
  );
}
function salesTable() {
  const rows = [...state.data.sales].sort((a, b) => b.id - a.id);
  if (!rows.length)
    return empty(
      "Aquí irá tu primera venta",
      "Cada compra registrada quedará disponible para consultarla.",
      "",
      true,
    );
  return (
    '<div class="table-wrap"><table><thead><tr><th>Venta / cliente</th><th>Fecha</th><th>Modalidad</th><th>Compra</th><th>Gracia</th><th>Detalle</th></tr></thead><tbody>' +
    rows
      .map(
        (s, i) =>
          "<tr><td>" +
          person(
            s.customer_name || getCustomer(s.customer_id)?.name,
            "Venta #" + s.id,
            i,
          ) +
          "</td><td>" +
          esc(fmtDate(s.purchased_at)) +
          "</td><td>" +
          badge(
            s.mode === "end" ? "Pago al cierre" : s.months + " cuotas",
            s.mode === "end" ? "neutral" : "",
          ) +
          '</td><td class="money">' +
          esc(amt(s.amount, s.currency)) +
          "</td><td>" +
          esc(amt(s.grace_interest, s.currency)) +
          '</td><td><button class="text-button" data-action="view-sale" data-id="' +
          s.id +
          '">Ver cronograma</button></td></tr>',
      )
      .join("") +
    "</tbody></table></div>"
  );
}
function renderCart() {
  const box = $("#cart");
  if (!box) return;
  const customer = getCustomer($("#sale-form [name=customer_id]")?.value);
  const products = state.data.products.filter(
    (p) => live(p) && (!customer || p.currency === customer.currency),
  );
  box.innerHTML = state.cart
    .map(
      (row, index) =>
        '<div class="cart-row"><div class="field"><label for="cart-product-' +
        index +
        '">Producto ' +
        (index + 1) +
        '</label><select id="cart-product-' +
        index +
        '" data-cart-product="' +
        index +
        '" required><option value="">Selecciona un producto</option>' +
        products
          .map(
            (p) =>
              '<option value="' +
              p.id +
              '" ' +
              (String(p.id) === String(row.product_id) ? "selected" : "") +
              ">" +
              esc(p.name) +
              " · " +
              esc(amt(p.credit_price, p.currency)) +
              "</option>",
          )
          .join("") +
        '</select><small>Precio de lista en la moneda del cliente.</small></div><div class="field"><label for="cart-qty-' +
        index +
        '">Cantidad</label><input id="cart-qty-' +
        index +
        '" data-cart-quantity="' +
        index +
        '" type="number" min="0.001" max="10000" step="0.001" value="' +
        esc(row.quantity) +
        '" required title="Cantidad de unidades; admite hasta tres decimales."><small>Hasta 3 decimales.</small></div><button type="button" class="icon-button" data-action="remove-item" data-index="' +
        index +
        '" aria-label="Quitar producto ' +
        (index + 1) +
        '" ' +
        (state.cart.length === 1 ? "disabled" : "") +
        ">" +
        icon("trash") +
        "</button></div>",
    )
    .join("");
}
function invalidatePlan() {
  state.plan = null;
  const box = $("#sale-preview");
  if (box)
    box.innerHTML = empty(
      "Revisa el nuevo cálculo",
      "Cambiaste los datos de la compra. Vuelve a calcular antes de guardarla.",
    );
}
function planHtml(p) {
  return (
    '<div class="panel-head"><div><h2>Tu plan de pagos</h2><p>Cuotas vencidas · Mes comercial de 30 días</p></div><span class="stat-icon">' +
    icon("file") +
    '</span></div><div class="panel-body"><div class="summary-row"><span>Importe de la compra</span><strong>' +
    esc(amt(p.amount, p.currency)) +
    '</strong></div><div class="summary-row"><span>Interés de gracia (' +
    esc(p.grace_days || 0) +
    " días)</span><strong>" +
    esc(amt(p.grace_interest, p.currency)) +
    '</strong></div><div class="summary-row"><span>Capital financiado</span><strong>' +
    esc(amt(p.financed, p.currency)) +
    '</strong></div><div class="summary-row total"><span>Total del cronograma</span><span>' +
    esc(
      amt(
        p.dues.reduce((s, d) => s + Number(d.amount), 0),
        p.currency,
      ),
    ) +
    '</span></div><div class="notice">' +
    icon("info") +
    "<span>Las cuotas no incluyen mora futura. La última cuota puede ajustar unos céntimos por redondeo.</span></div></div>" +
    planTable(p.dues, p.currency) +
    '<div class="panel-body" style="padding-top:20px">' +
    btn("Guardar venta", "confirm-sale", "primary", "check") +
    "</div>"
  );
}
function planTable(dues, currency) {
  return (
    '<div class="table-wrap"><table class="plan-table"><thead><tr><th>Cuota</th><th>Fecha</th><th>Capital</th><th>Interés</th><th>Total</th></tr></thead><tbody>' +
    dues
      .map(
        (d) =>
          "<tr><td>" +
          esc(d.number) +
          "</td><td>" +
          esc(fmtDate(d.date || d.due_date)) +
          "</td><td>" +
          esc(amt(d.principal, currency)) +
          "</td><td>" +
          esc(amt(d.interest, currency)) +
          '</td><td class="money">' +
          esc(amt(d.amount, currency)) +
          "</td></tr>",
      )
      .join("") +
    "</tbody></table></div>"
  );
}
function paymentsPage() {
  if (noStore())
    return (
      pageHead(
        "Cobros y pagos",
        "Registra el dinero recibido y mantén las cuentas al día.",
      ) + noStore()
    );
  return (
    pageHead(
      "Cobros y pagos",
      "Consulta el importe exigible y registra el dinero recibido.",
      btn("Exportar CSV", "export", "secondary", "down"),
    ) +
    '<div class="two-columns"><section class="panel form-card"><h2>Registrar un pago</h2><p class="muted" style="font-size:12px;margin:9px 0 22px">Consulta el total exigible del cliente antes de confirmar.</p><form id="payment-form">' +
    selectField(
      "customer_id",
      "Cliente",
      state.data.customers.map((c) => [c.id, c.name + " · " + c.currency]),
      "",
    ) +
    '<div class="notice">' +
    icon("info") +
    '<span>Se cobran todas las cuotas pendientes con vencimiento hasta hoy, incluida su mora. No se permiten pagos parciales. Si no hay cuotas vencidas, se puede pagar completo el siguiente vencimiento.</span></div><div class="error-text" id="payment-error" role="alert"></div><button class="btn primary" type="submit">' +
    icon("wallet") +
    'Consultar monto a pagar</button></form><div id="payment-preview"></div></section><section class="panel"><div class="panel-head"><div><h2>Pagos recibidos</h2><p>Comprobantes de las operaciones registradas</p></div>' +
    icon("check") +
    "</div>" +
    paymentHistory() +
    "</section></div>"
  );
}
function paymentHistory() {
  const rows = [...state.data.payments].sort((a, b) => b.id - a.id);
  return rows.length
    ? '<div class="table-wrap"><table><thead><tr><th>Cliente / comprobante</th><th>Fecha</th><th>Mora</th><th>Total</th></tr></thead><tbody>' +
        rows
          .map(
            (p, i) =>
              "<tr><td>" +
              person(
                p.customer_name || getCustomer(p.customer_id)?.name,
                "Pago #" + p.id,
                i,
              ) +
              "</td><td>" +
              esc(fmtDate(p.paid_at)) +
              "</td><td>" +
              esc(amt(p.late_amount, p.currency)) +
              '</td><td class="money">' +
              esc(amt(p.amount, p.currency)) +
              "</td></tr>",
          )
          .join("") +
        "</tbody></table></div>"
    : empty(
        "Todavía no hay pagos",
        "Al confirmar un cobro, su comprobante aparecerá aquí.",
        "",
        true,
      );
}
function renderPayment() {
  const p = state.payment;
  if (!$("#payment-preview")) return;
  $("#payment-preview").innerHTML = p.dues.length
    ? '<div class="payment-panel"><small class="overline">Importe a cobrar</small><div class="payment-total">' +
      esc(amt(p.amount, p.currency)) +
      '</div><p class="muted" style="font-size:11px">' +
      p.dues.length +
      " cuotas incluidas · Calculado al " +
      esc(fmtDate(today())) +
      "</p>" +
      (p.dues.every((d) => d.due_date > today())
        ? '<div class="notice">' +
          icon("info") +
          "<span>Pago anticipado completo del vencimiento del " +
          esc(fmtDate(p.dues[0].due_date)) +
          ". Se mantiene el importe acordado.</span></div>"
        : "") +
      '<div class="divider"></div><div class="summary-row"><span>Cuotas a pagar</span><strong>' +
      esc(amt(p.base_amount, p.currency)) +
      '</strong></div><div class="summary-row"><span>Intereses por mora</span><strong>' +
      esc(amt(p.late_amount, p.currency)) +
      "</strong></div>" +
      duesTable(p.dues) +
      lateBreakdown(p.dues) +
      '<div class="notice warn">' +
      icon("info") +
      "<span>Confirma el pago solo después de recibir el dinero. La operación queda registrada en movimientos.</span></div>" +
      btn("Confirmar pago recibido", "confirm-payment", "primary", "check") +
      "</div>"
    : empty(
        "No tiene pagos exigibles",
        "Este cliente no tiene cuotas pendientes por cobrar.",
        "",
        true,
      );
}
function statementsPage() {
  const own = state.user.role === "customer";
  return (
    pageHead(
      "Estados de cuenta",
      own
        ? "Revisa tus compras, intereses y fechas con todo el detalle."
        : "Consulta, imprime o exporta el detalle de un crédito.",
      '<div class="filter-row no-print">' +
        btn("Imprimir / PDF", "print", "secondary", "printer") +
        btn("Exportar CSV", "export-statement", "secondary", "down") +
        "</div>",
    ) +
    '<section class="panel no-print" style="margin-bottom:23px"><form id="statement-form" class="form-card"><div class="form-grid">' +
    selectField(
      "customer_id",
      "Cliente",
      state.data.customers.map((c) => [c.id, c.name + " · " + c.currency]),
      own ? state.user.customer_id : state.statementCustomer || "",
    ) +
    inputField(
      "date",
      "Fecha de consulta",
      "date",
      today(),
      false,
      "Consulta actual o un cierre de corte guardado.",
    ) +
    '</div><button class="btn primary" type="submit" style="margin-top:18px">' +
    icon("file") +
    'Generar estado de cuenta</button><div id="statement-error" class="error-text" role="alert"></div></form></section><section class="panel" id="statement-output">' +
    empty(
      "Genera un estado de cuenta",
      "Selecciona un cliente y una fecha para generar su estado de cuenta.",
    ) +
    "</section>"
  );
}
function renderClosures() {
  const box = $("#closure-picker");
  if (box) box.remove();
  const closures = state.statement?.closed_statements || [];
  if (!closures.length) return;
  const wrap = document.createElement("div");
  wrap.id = "closure-picker";
  wrap.className = "field";
  wrap.style.marginTop = "18px";
  wrap.innerHTML =
    '<label for="closure-select">Consultar un cierre guardado</label><select id="closure-select" title="Un cierre conserva el detalle original del corte; el estado actual incluye la mora de la fecha consultada."><option value="">Estado actual / fecha de consulta</option>' +
    closures
      .map(
        (x) =>
          '<option value="' +
          esc(x.cycle) +
          '"' +
          (state.statement.is_closed && state.statement.cycle === x.cycle
            ? " selected"
            : "") +
          ">Corte del " +
          esc(fmtDate(x.cycle)) +
          "</option>",
      )
      .join("") +
    "</select><small>Los cierres conservan el cálculo registrado al corte.</small>";
  $("#statement-form").append(wrap);
}
function renderStatement() {
  const s = state.statement;
  if (!s || !$("#statement-output")) return;
  const c = s.customer;
  const pending = s.dues.filter((d) => !d.payment_id);
  $("#statement-output").innerHTML =
    '<article class="statement"><header class="statement-brand"><div><h2>Estado de cuenta</h2><p class="muted" style="font-size:11px">' +
    esc(s.store?.name || state.data.store?.name) +
    "</p></div>" +
    brand() +
    '</header><div class="statement-meta"><div><small>Cliente</small><strong>' +
    esc(c.name) +
    '</strong><p style="font-size:11px">' +
    esc(c.document) +
    "</p></div><div><small>Fecha de consulta</small><strong>" +
    esc(fmtDate(s.date)) +
    '</strong><p style="font-size:11px">' +
    esc(c.currency) +
    "</p></div><div><small>Condiciones actuales</small><strong>Corte: día " +
    esc(c.cutoff_day) +
    " · Pago: día " +
    esc(c.pay_day) +
    '</strong><p style="font-size:11px">Tasa ' +
    esc(c.rate) +
    "% " +
    (c.rate_kind === "effective" ? "efectiva" : "nominal") +
    " " +
    (Number(c.rate_period) === 360 ? "anual" : "mensual") +
    '</p></div></div><div class="balance-card"><small class="overline">A pagar el ' +
    esc(fmtDate(s.payment_date || s.date)) +
    '</small><div class="payment-total">' +
    esc(amt(s.amount_due, c.currency)) +
    '</div><p>Cuotas del período e intereses por mora a la fecha de consulta.</p><div class="summary-row"><span>Saldo en cuotas futuras</span><strong>' +
    esc(amt(s.future_balance, c.currency)) +
    "</strong></div></div>" +
    (s.is_closed
      ? '<div class="notice">' +
        icon("lock") +
        "<span>Estado de cuenta de corte guardado. Este documento conserva los importes calculados al generarlo.</span></div>"
      : "") +
    '<h3 style="margin-bottom:15px">Detalle completo del crédito</h3>' +
    duesTable(s.dues, false, s.date) +
    lateBreakdown(s.dues) +
    '<div class="statement-totals">' +
    ["PEN", "USD"]
      .filter((cur) => s.dues.some((d) => d.currency === cur))
      .map(
        (cur) =>
          '<div class="summary-row"><span>Cuotas pendientes · ' +
          cur +
          "</span><strong>" +
          esc(
            amt(
              pending
                .filter((d) => d.currency === cur)
                .reduce((a, d) => a + Number(d.amount), 0),
              cur,
            ),
          ) +
          '</strong></div><div class="summary-row"><span>Intereses por mora · ' +
          cur +
          "</span><strong>" +
          esc(
            amt(
              pending
                .filter((d) => d.currency === cur)
                .reduce((a, d) => a + Number(d.late_amount || 0), 0),
              cur,
            ),
          ) +
          '</strong></div><div class="summary-row total"><span>Saldo total pendiente · ' +
          cur +
          "</span><strong>" +
          esc(
            amt(
              pending
                .filter((d) => d.currency === cur)
                .reduce(
                  (a, d) =>
                    a +
                    Number(
                      d.total || Number(d.amount) + Number(d.late_amount || 0),
                    ),
                  0,
                ),
              cur,
            ),
          ) +
          "</strong></div>",
      )
      .join("") +
    '</div><div class="form-section"><h3>Compras del estado de cuenta</h3>' +
    (s.sales.length
      ? '<div class="table-wrap"><table><thead><tr><th>Compra</th><th>Productos / servicios</th><th>Modalidad</th><th>Importe</th></tr></thead><tbody>' +
        [...s.sales]
          .sort((a, b) => a.purchased_at.localeCompare(b.purchased_at))
          .map(
            (v) =>
              "<tr><td>" +
              esc(fmtDate(v.purchased_at)) +
              " · #" +
              v.id +
              "</td><td>" +
              esc(
                (v.items || [])
                  .map((i) => i.name + " × " + i.quantity)
                  .join(", "),
              ) +
              "</td><td>" +
              (v.mode === "end" ? "Pago al cierre" : v.months + " cuotas") +
              '</td><td class="money">' +
              esc(amt(v.amount, v.currency)) +
              "</td></tr>",
          )
          .join("") +
        "</tbody></table></div>"
      : '<p class="muted" style="font-size:12px">Aún no hay compras con corte realizado para esta fecha.</p>') +
    '</div><div class="notice">' +
    icon("info") +
    '<span>La mora se detalla por cuota. Cada venta conserva sus condiciones originales. Los importes de monedas distintas se muestran por separado. Documento informativo, sin valor tributario.</span></div><p class="muted" style="font-size:10px;margin-top:20px">Generado el ' +
    esc(fmtDate(s.generated_at || today())) +
    " · Cuenta Clara</p></article>";
}
function storesPage() {
  const rows = state.data.stores;
  return (
    pageHead(
      "Tiendas",
      "Administra las tiendas que forman parte de la plataforma.",
      btn("Registrar tienda", "new-store"),
    ) +
    (rows.length
      ? '<div class="stores-grid">' +
        rows
          .map(
            (s) =>
              '<article class="store-card"><div class="store-symbol">' +
              icon("store") +
              "</div>" +
              badge(
                live(s) ? "Tienda activa" : "Tienda inactiva",
                live(s) ? "" : "neutral",
              ) +
              "<h3>" +
              esc(s.name) +
              "</h3><p>" +
              esc(s.business) +
              "</p><p>" +
              esc(s.address || "Sin dirección registrada") +
              '</p><div class="filter-row">' +
              btn(
                "Abrir tienda",
                "open-store",
                "secondary small",
                "arrow",
                'data-id="' + s.id + '"',
              ) +
              '<button class="icon-button" data-action="edit-store" data-id="' +
              s.id +
              '" title="Editar tienda" aria-label="Editar ' +
              esc(s.name) +
              '">' +
              icon("edit") +
              '</button><button class="text-button" data-action="toggle-store" data-id="' +
              s.id +
              '">' +
              (live(s) ? "Desactivar" : "Activar") +
              "</button></div></article>",
          )
          .join("") +
        "</div>"
      : '<div class="panel">' +
        empty(
          "Aquí empieza la plataforma",
          "Registra la primera tienda y crea el acceso de su administrador.",
          btn("Crear primera tienda", "new-store"),
        ) +
        "</div>")
  );
}
function auditPage() {
  const rows = [...state.data.audit].sort((a, b) => b.id - a.id);
  return (
    pageHead(
      "Movimientos",
      "Historial de operaciones y cambios del negocio.",
      btn("Exportar CSV", "export", "secondary", "down"),
    ) +
    '<div class="panel">' +
    (rows.length
      ? '<div class="table-wrap"><table><thead><tr><th>Fecha y hora</th><th>Usuario</th><th>Acción</th><th>Referencia</th><th>Detalle</th></tr></thead><tbody>' +
        rows
          .map(
            (a) =>
              "<tr><td>" +
              esc(a.created_at?.replace("T", " ").slice(0, 19)) +
              "</td><td>" +
              esc(a.user_name || a.username || "Usuario #" + a.user_id) +
              "</td><td>" +
              esc(a.action) +
              "</td><td>" +
              esc(a.entity_id || "—") +
              '</td><td style="white-space:normal;min-width:230px">' +
              esc(
                typeof a.detail === "object"
                  ? JSON.stringify(a.detail)
                  : a.detail,
              ) +
              "</td></tr>",
          )
          .join("") +
        "</tbody></table></div>"
      : empty(
          "Aún sin movimientos",
          "Las altas, ventas, pagos y cambios se registran automáticamente.",
        )) +
    "</div>"
  );
}
function settingsPage() {
  return (
    pageHead(
      "Configuración",
      "Revisa tu acceso y mantén tu cuenta protegida.",
    ) +
    '<div class="two-columns"><section class="panel form-card"><h2>Cambiar contraseña</h2><p class="muted" style="font-size:12px;margin:9px 0 20px">Usa una contraseña que solo tú conozcas.</p><form id="password-form"><div class="form-grid">' +
    inputField(
      "current_password",
      "Contraseña actual",
      "password",
      "",
      true,
      "",
      'autocomplete="current-password"',
    ) +
    inputField(
      "password",
      "Nueva contraseña",
      "password",
      "",
      true,
      "Mínimo 12 caracteres: mayúscula, minúscula y número.",
      'minlength="12" maxlength="128" autocomplete="new-password"',
    ) +
    '</div><div id="password-error" class="error-text" role="alert"></div><button class="btn primary" type="submit" style="margin-top:20px">' +
    icon("lock") +
    'Guardar contraseña</button></form></section><section class="panel form-card"><h2>Tu cuenta</h2><div class="divider"></div><div class="summary-row"><span>Nombre</span><strong>' +
    esc(state.user.name) +
    '</strong></div><div class="summary-row"><span>Usuario</span><strong>' +
    esc(state.user.username) +
    '</strong></div><div class="summary-row"><span>Perfil</span><strong>' +
    { platform: "Plataforma", merchant: "Negocio", customer: "Cliente" }[
      state.user.role
    ] +
    '</strong></div><div class="notice">' +
    icon("shield") +
    '<span>Cierra tu sesión cuando termines, sobre todo si compartes la computadora.</span></div><div class="filter-row">' +
    btn("Privacidad", "privacy", "secondary", "shield") +
    btn("Ayuda", "help", "secondary", "help") +
    "</div></section></div>"
  );
}

const fieldGuidance = {
  name: "Nombre con el que identificarás este registro. Hasta 120 caracteres.",
  document:
    "DNI, carné de extranjería u otro documento. Debe ser único dentro de la tienda.",
  email:
    "Correo del cliente, por ejemplo nombre@correo.com. Se utiliza como dato de contacto.",
  phone:
    "Teléfono de contacto. Incluye el código de país si corresponde. Es opcional.",
  currency:
    "Elige soles o dólares. Las ventas, límites y pagos mantienen esta moneda; no se realizan conversiones.",
  credit_limit:
    "Capital máximo que el cliente puede mantener pendiente, incluida la gracia capitalizada.",
  max_months:
    "Máximo de cuotas mensuales que se ofrecerá al cliente. Entre 1 y 60.",
  cutoff_day:
    "Día del mes del 1 al 31. Si el mes no tiene ese día, el corte será el último día de ese mes.",
  cutoff_hour:
    "Hora de corte en Lima, entre 0 y 23. Por ejemplo, 18 significa 18:00. Una compra posterior pasa al siguiente corte.",
  pay_day:
    "Día mensual del 1 al 31. En meses cortos se usa su último día y se recupera el día pactado el mes siguiente.",
  rate: "Porcentaje compensatorio del período elegido. Para una tasa del 24 %, escribe 24.",
  rate_kind:
    "Efectiva: incorpora la capitalización del período. Nominal: requiere la frecuencia de capitalización indicada.",
  rate_period:
    "Anual corresponde a 360 días y mensual a 30 días para los cálculos comerciales.",
  frequency:
    "Veces que capitaliza la tasa nominal anual. Una nominal mensual siempre capitaliza mensualmente.",
  late_rate:
    "Porcentaje por atraso, aplicado a la obligación vencida completa. Puede ser cero si no se cobra mora.",
  late_kind: "Indica si la tasa moratoria pactada es nominal o efectiva.",
  late_period:
    "Período al que corresponde el porcentaje de mora: mes de 30 días o año de 360 días.",
  late_frequency:
    "Capitalizaciones de la nominal anual de mora. No modifica una tasa efectiva ni una nominal mensual.",
  username:
    "Acceso único en la plataforma. Usa letras, números, punto, guion, guion bajo o @.",
  password:
    "Mínimo 12 caracteres, con mayúscula, minúscula y número. No compartas esta clave.",
  current_password: "Escribe tu contraseña vigente para autorizar el cambio.",
  description:
    "Detalles que permiten identificar el producto o servicio. Es opcional, hasta 500 caracteres.",
  brand:
    "Marca comercial, si corresponde. Puedes dejarla vacía en servicios o productos sin marca.",
  supplier: "Proveedor del producto o servicio. Es un dato opcional.",
  unit: "Unidad de venta: kg, unidad, litro, hora o servicio. La cantidad de la compra utiliza esta unidad.",
  cash_price:
    "Precio al contado en la moneda seleccionada, con hasta dos decimales.",
  credit_price:
    "Precio de lista que se utiliza al registrar una venta a crédito; sobre él se calculan los intereses pactados.",
  image:
    "Enlace HTTPS a una imagen que tengas permiso de usar. Si lo dejas vacío se muestra un icono del catálogo.",
  business:
    "Rubro del negocio, por ejemplo bodega, panadería, peluquería o bazar.",
  address:
    "Dirección del establecimiento para identificarlo en los estados de cuenta. Es opcional.",
  admin_name:
    "Nombre del responsable que administrará esta tienda y sus operaciones.",
  customer_id:
    "Selecciona al cliente. Se aplicarán su moneda, crédito disponible, tasas y fechas acordadas.",
  purchased_at:
    "Fecha y hora de la compra en Lima. Determina el corte; no admite fechas futuras ni períodos ya cerrados.",
  mode: "Pago al cierre: una obligación en la fecha pactada. En cuotas: método francés con gracia inicial capitalizada.",
  months:
    "Cuotas mensuales del crédito, hasta el máximo autorizado al cliente. Solo aplica al pago en cuotas.",
  date: "Fecha del estado de cuenta. No admite fechas futuras. Los cierres guardados conservan los datos de su emisión.",
};
function fieldLabel(name, label, id, description) {
  return `<div class="field-label"><label for="${id}">${esc(label)}</label><details class="field-help"><summary aria-label="Ayuda: ${esc(label)}" title="Ayuda sobre ${esc(label)}">${icon("help")}</summary><p id="${id}-help">${esc(description)}</p></details></div>`;
}

function inputField(
  name,
  label,
  type = "text",
  value = "",
  full = false,
  help = "",
  attrs = "",
) {
  const id = "field-" + name;
  const description =
    fieldGuidance[name] ||
    help ||
    "Completa este dato para identificar el registro.";
  const optional = [
    "phone",
    "brand",
    "supplier",
    "image",
    "description",
    "address",
  ].includes(name);
  const autocomplete = attrs.includes("autocomplete=")
    ? ""
    : `autocomplete="${{ email: "email", phone: "tel", current_password: "current-password" }[name] || "off"}"`;
  return `<div class="field ${full ? "full" : ""}">${fieldLabel(name, label, id, description)}<input id="${id}" name="${name}" type="${type}" value="${esc(value)}" ${attrs} ${optional ? "" : "required"} ${autocomplete} ${["email", "url"].includes(type) ? 'spellcheck="false"' : ""} aria-describedby="${id}-help">${help ? `<small>${esc(help)}</small>` : ""}</div>`;
}
function selectField(
  name,
  label,
  options,
  value = "",
  full = false,
  help = "",
) {
  const id = "field-" + name;
  const description =
    fieldGuidance[name] ||
    help ||
    "Selecciona la opción que corresponda a este registro.";
  return `<div class="field ${full ? "full" : ""}">${fieldLabel(name, label, id, description)}<select name="${name}" id="${id}" required aria-describedby="${id}-help">${!value ? '<option value="">Selecciona una opción</option>' : ""}${options.map(([v, t]) => `<option value="${esc(v)}" ${String(v) === String(value) ? "selected" : ""}>${esc(t)}</option>`).join("")}</select>${help ? `<small>${esc(help)}</small>` : ""}</div>`;
}
function modal(title, description, body, formId = "", submit = "Guardar") {
  const m = $("#modal");
  m.className = "modal";
  m.innerHTML =
    '<button class="icon-button dialog-close" data-action="close-modal" aria-label="Cerrar ventana">' +
    icon("close") +
    '</button><div class="modal-head"><h2 id="modal-title">' +
    esc(title) +
    "</h2><p>" +
    esc(description) +
    "</p></div>" +
    (formId ? '<form id="' + formId + '">' : "") +
    '<div class="modal-body">' +
    body +
    '</div><div class="modal-foot"><div id="modal-error" class="error-text" role="alert"></div><button class="btn secondary" type="button" data-action="close-modal">' +
    (formId ? "Cancelar" : "Entendido") +
    "</button>" +
    (formId
      ? '<button class="btn primary" type="submit">' +
        icon("check") +
        esc(submit) +
        "</button>"
      : "") +
    "</div>" +
    (formId ? "</form>" : "");
  if (!m.open) m.showModal();
}
function customerModal(id) {
  const c = id ? getCustomer(id) : {};
  const edit = Boolean(id);
  state.editId = id || null;
  const currency = [
    ["PEN", "Soles · PEN"],
    ["USD", "Dólares · USD"],
  ];
  const kinds = [
    ["effective", "Efectiva"],
    ["nominal", "Nominal"],
  ];
  const periods = [
    [360, "Anual · 360 días"],
    [30, "Mensual · 30 días"],
  ];
  const freqs = [
    [1, "Anual"],
    [2, "Semestral"],
    [4, "Trimestral"],
    [6, "Bimestral"],
    [12, "Mensual"],
    [24, "Quincenal"],
    [360, "Diaria"],
  ];
  modal(
    edit ? "Editar cliente" : "Nuevo cliente",
    edit
      ? "Los cambios aplican a las próximas ventas. Las compras anteriores conservan su acuerdo."
      : "Completa sus datos y deja claro cómo funcionará su crédito.",
    '<div class="form-grid">' +
      inputField(
        "name",
        "Nombre completo",
        "text",
        c.name || "",
        true,
        "",
        'maxlength="120"',
      ) +
      inputField(
        "document",
        "Documento de identidad",
        "text",
        c.document || "",
        false,
        "DNI, CE u otro documento.",
        'maxlength="30"',
      ) +
      inputField(
        "email",
        "Correo electrónico",
        "email",
        c.email || "",
        false,
        "",
        'maxlength="160"',
      ) +
      inputField(
        "phone",
        "Teléfono (opcional)",
        "tel",
        c.phone || "",
        true,
        "",
        'maxlength="30"',
      ) +
      '</div><div class="form-section"><h3>Su línea de crédito</h3><div class="form-grid">' +
      selectField("currency", "Moneda", currency, c.currency || "PEN") +
      inputField(
        "credit_limit",
        "Límite de crédito",
        "number",
        c.credit_limit || "500",
        false,
        "Se controla sobre el capital pendiente.",
        'min="0.01" max="10000000" step="0.01"',
      ) +
      inputField(
        "max_months",
        "Máximo de cuotas",
        "number",
        c.max_months || "12",
        false,
        "De 1 a 60 meses.",
        'min="1" max="60" step="1"',
      ) +
      inputField(
        "cutoff_day",
        "Día de corte",
        "number",
        c.cutoff_day || "20",
        false,
        "Día del 1 al 31; se ajusta en meses cortos.",
        'min="1" max="31" step="1"',
      ) +
      inputField(
        "cutoff_hour",
        "Hora del corte",
        "number",
        c.cutoff_hour ?? "23",
        false,
        "Formato de 24 horas. El corte ocurre a las HH:00.",
        'min="0" max="23" step="1"',
      ) +
      inputField(
        "pay_day",
        "Día de pago",
        "number",
        c.pay_day || "26",
        false,
        "Día del 1 al 31; se ajusta en meses cortos.",
        'min="1" max="31" step="1"',
      ) +
      '</div></div><div class="form-section"><h3>Tasa de interés pactada</h3><div class="form-grid">' +
      inputField(
        "rate",
        "Tasa (%)",
        "number",
        c.rate ?? "24",
        false,
        "Porcentaje del período elegido.",
        'min="0" max="1000" step="0.000001"',
      ) +
      selectField(
        "rate_kind",
        "Tipo de tasa",
        kinds,
        c.rate_kind || "effective",
      ) +
      selectField("rate_period", "Período", periods, c.rate_period || 360) +
      selectField(
        "frequency",
        "Capitalización de tasa nominal anual",
        freqs,
        c.frequency || 12,
        false,
        "Solo aplica a nominal anual; la nominal mensual capitaliza cada mes.",
      ) +
      '</div></div><div class="form-section"><h3>Interés por mora</h3><div class="form-grid">' +
      inputField(
        "late_rate",
        "Tasa moratoria (%)",
        "number",
        c.late_rate ?? "36",
        false,
        "Se aplica al importe vencido.",
        'min="0" max="1000" step="0.000001"',
      ) +
      selectField(
        "late_kind",
        "Tipo de tasa moratoria",
        kinds,
        c.late_kind || "effective",
      ) +
      selectField(
        "late_period",
        "Período de mora",
        periods,
        c.late_period || 360,
      ) +
      selectField(
        "late_frequency",
        "Capitalización de nominal anual",
        freqs,
        c.late_frequency || 12,
      ) +
      "</div></div>" +
      (!edit
        ? '<div class="form-section"><h3>Acceso del cliente</h3><div class="form-grid">' +
          inputField(
            "username",
            "Usuario",
            "text",
            "",
            false,
            "Debe ser único en la plataforma.",
            'minlength="3" maxlength="60" autocomplete="off"',
          ) +
          inputField(
            "password",
            "Contraseña inicial",
            "password",
            "",
            false,
            "Mínimo 12 caracteres: mayúscula, minúscula y número.",
            'minlength="12" maxlength="128" autocomplete="new-password"',
          ) +
          '<label class="check"><input type="checkbox" name="consent" required><span>El cliente leyó la política de privacidad versión 1 y autorizó el registro de sus datos para gestionar su crédito. <button type="button" class="text-button" data-action="privacy-inline">Leer política</button></span></label></div><div id="privacy-inline" hidden class="notice">Los datos se usan para administrar compras, crédito y pagos de esta tienda. El cliente puede consultar su información y solicitar corrección o baja al comercio. Los movimientos se conservan mientras exista deuda o una necesidad justificada de respaldo. No ingreses datos de terceros sin su autorización.</div></div>'
        : "") +
      "<div class='notice'>" +
      icon("info") +
      "<span>No cambies la moneda si el cliente tiene deuda pendiente. Para reactivar un cliente, usa la opción de su fila.</span></div>",
    "customer-form",
    edit ? "Guardar cambios" : "Registrar cliente",
  );
}
function productModal(id) {
  const p = id
    ? state.data.products.find((x) => Number(x.id) === Number(id))
    : {};
  state.editId = id || null;
  modal(
    id ? "Editar producto" : "Nuevo producto",
    "Completa los precios y los detalles que ayudan a reconocerlo.",
    '<div class="form-grid">' +
      inputField(
        "name",
        "Nombre del producto o servicio",
        "text",
        p.name || "",
        true,
        "",
        'maxlength="120"',
      ) +
      inputField(
        "description",
        "Descripción (opcional)",
        "text",
        p.description || "",
        true,
        "",
        'maxlength="500"',
      ) +
      inputField(
        "brand",
        "Marca (opcional)",
        "text",
        p.brand || "",
        false,
        "",
        'maxlength="80"',
      ) +
      inputField(
        "supplier",
        "Proveedor (opcional)",
        "text",
        p.supplier || "",
        false,
        "",
        'maxlength="120"',
      ) +
      inputField(
        "unit",
        "Unidad de medida",
        "text",
        p.unit || "unidad",
        false,
        "Por ejemplo: kg, unidad, litro o servicio.",
        'maxlength="30"',
      ) +
      selectField(
        "currency",
        "Moneda",
        [
          ["PEN", "Soles · PEN"],
          ["USD", "Dólares · USD"],
        ],
        p.currency || "PEN",
      ) +
      inputField(
        "cash_price",
        "Precio al contado",
        "number",
        p.cash_price || "",
        false,
        "",
        'min="0.01" max="10000000" step="0.01"',
      ) +
      inputField(
        "credit_price",
        "Precio de lista / crédito",
        "number",
        p.credit_price || "",
        false,
        "Sobre este precio se calculan los intereses pactados.",
        'min="0.01" max="10000000" step="0.01"',
      ) +
      inputField(
        "image",
        "URL de la imagen (opcional)",
        "url",
        p.image || "",
        true,
        "Usa una dirección HTTPS de una imagen que puedas utilizar.",
        'maxlength="500" placeholder="https://..."',
      ) +
      '<label class="check"><input type="checkbox" name="installment_allowed" ' +
      (!id || Number(p.installment_allowed) ? "checked" : "") +
      "><span>Permitir que este producto se venda en cuotas mensuales.</span></label></div>",
    "product-form",
    id ? "Guardar cambios" : "Agregar producto",
  );
}
function storeModal(id) {
  const s = id
    ? state.data.stores.find((x) => Number(x.id) === Number(id))
    : {};
  state.editId = id || null;
  modal(
    id ? "Editar tienda" : "Nueva tienda",
    "Cada tienda tiene sus propios clientes, productos y movimientos.",
    '<div class="form-grid">' +
      inputField(
        "name",
        "Nombre del negocio",
        "text",
        s.name || "",
        true,
        "",
        'maxlength="120"',
      ) +
      inputField(
        "business",
        "Giro del negocio",
        "text",
        s.business || "",
        false,
        "Bodega, panadería, peluquería, etc.",
        'maxlength="80"',
      ) +
      inputField(
        "address",
        "Dirección (opcional)",
        "text",
        s.address || "",
        true,
        "",
        'maxlength="200"',
      ) +
      "</div>" +
      (!id
        ? '<div class="form-section"><h3>Administrador del negocio</h3><div class="form-grid">' +
          inputField(
            "admin_name",
            "Nombre completo",
            "text",
            "",
            true,
            "",
            'maxlength="120"',
          ) +
          inputField(
            "username",
            "Usuario de acceso",
            "text",
            "",
            false,
            "Debe ser único en la plataforma.",
            'minlength="3" maxlength="60" autocomplete="off"',
          ) +
          inputField(
            "password",
            "Contraseña inicial",
            "password",
            "",
            false,
            "Mínimo 12 caracteres: mayúscula, minúscula y número.",
            'minlength="12" maxlength="128" autocomplete="new-password"',
          ) +
          "</div></div>"
        : ""),
    "store-form",
    id ? "Guardar cambios" : "Registrar tienda",
  );
}
function privacy() {
  modal(
    "Política de privacidad",
    "Versión 1 · Información para clientes",
    '<div class="prose"><h3>Para qué se registran tus datos</h3><p>La tienda utiliza tu nombre, documento, correo y teléfono para identificar tu cuenta, acordar tu crédito y administrar compras y pagos. Tu acceso personal permite consultar tus operaciones.</p><h3>Quién puede ver la información</h3><p>El personal autorizado de tu tienda y el administrador de la plataforma, dentro de sus funciones. Los clientes no pueden consultar las cuentas de otras personas.</p><h3>Conservación y solicitudes</h3><p>Los datos se mantienen mientras sean necesarios para gestionar el crédito, resolver saldos o conservar el respaldo de las operaciones. Puedes solicitar acceso, corrección o baja a la tienda. La desactivación conserva el historial de las deudas y pagos.</p><h3>Cuida tu acceso</h3><p>No compartas tu contraseña y cierra la sesión en computadoras compartidas. El comercio registra la fecha de tu consentimiento al crear la cuenta.</p></div>',
  );
}
function help() {
  modal(
    "Vamos paso a paso",
    "Una guía rápida para tener tus cuentas al día.",
    '<div class="prose"><h3>1. Prepara tu negocio</h3><p>El administrador de plataforma registra una tienda y su acceso. Dentro de la tienda, agrega productos y clientes con sus condiciones de crédito.</p><h3>2. Registra la compra</h3><p>En Ventas a crédito, selecciona al cliente y sus productos. Puedes cobrar todo en la fecha pactada o financiarlo en cuotas. Antes de guardar, revisa el cronograma.</p><h3>3. Entiende el corte y la gracia</h3><p>La fecha y hora de corte determinan el período de la compra. Para cuotas, el interés entre la compra y la fecha de pago inicial se capitaliza como gracia total. La primera cuota vence el mes siguiente.</p><h3>4. Recibe el pago completo</h3><p>En Cobros y pagos verás todas las cuotas que vencieron hasta hoy. El sistema suma los intereses por mora y registra el pago completo. No se aceptan pagos parciales. Si no hay cuotas vencidas, puedes pagar completo el siguiente vencimiento, manteniendo el importe pactado.</p><h3>5. Comparte el detalle</h3><p>En Estados de cuenta genera el reporte por cliente y fecha. Puedes imprimirlo, guardarlo como PDF desde el navegador o exportar los datos en CSV.</p><h3>¿Un dato cambió?</h3><p>Editar un cliente cambia las condiciones de sus próximas compras. Las ventas guardadas conservan sus condiciones. Desactivar un producto o cliente no borra su historial.</p><div class="notice">' +
      icon("info") +
      "Los importes de soles y dólares se mantienen separados. No se convierten monedas.</div></div>",
  );
}
function saleDetails(id) {
  const sale = state.data.sales.find((x) => Number(x.id) === Number(id));
  const dues = state.data.dues.filter((d) => Number(d.sale_id) === Number(id));
  modal(
    "Venta #" + id,
    "Compra del " +
      fmtDate(sale.purchased_at) +
      " · " +
      (sale.customer_name || getCustomer(sale.customer_id)?.name),
    '<div class="summary-row"><span>Importe de la compra</span><strong>' +
      esc(amt(sale.amount, sale.currency)) +
      '</strong></div><div class="summary-row"><span>Gracia (' +
      sale.grace_days +
      " días)</span><strong>" +
      esc(amt(sale.grace_interest, sale.currency)) +
      '</strong></div><div class="summary-row"><span>Capital financiado</span><strong>' +
      esc(amt(sale.financed, sale.currency)) +
      '</strong></div><div class="divider"></div>' +
      planTable(dues, sale.currency) +
      '<div class="notice">' +
      icon("info") +
      "<span>Los valores corresponden al acuerdo guardado en esta compra. La mora se consulta en el estado de cuenta.</span></div>",
  );
}
function formObject(form) {
  const raw = Object.fromEntries(new FormData(form));
  for (const key of [
    "max_months",
    "cutoff_day",
    "cutoff_hour",
    "pay_day",
    "rate_period",
    "frequency",
    "late_period",
    "late_frequency",
    "months",
    "customer_id",
  ])
    if (key in raw) raw[key] = Number(raw[key]);
  if (form.id === "customer-form" && !state.editId)
    raw.consent = form.elements.consent.checked;
  if (form.id === "product-form")
    raw.installment_allowed = form.elements.installment_allowed.checked;
  return raw;
}
function salePayload() {
  const raw = formObject($("#sale-form"));
  raw.items = state.cart.map((x) => ({
    product_id: Number(x.product_id),
    quantity: x.quantity,
  }));
  if (raw.mode === "end") raw.months = 1;
  return raw;
}
async function submitForm(form) {
  const target = {
    "customer-form": "#modal-error",
    "product-form": "#modal-error",
    "store-form": "#modal-error",
    "sale-form": "#sale-error",
    "payment-form": "#payment-error",
    "statement-form": "#statement-error",
    "password-form": "#password-error",
  }[form.id];
  const error = target ? $(target) : null;
  if (error) error.textContent = "";
  const submit = $('[type="submit"]', form);
  if (submit) submit.disabled = true;
  try {
    if (form.id === "login-form") {
      await doLogin(form);
      return;
    }
    if (["customer-form", "product-form", "store-form"].includes(form.id)) {
      const resource = {
        "customer-form": "customers",
        "product-form": "products",
        "store-form": "stores",
      }[form.id];
      await api(
        "/" + resource + (state.editId ? "/" + state.editId : "") + scope(),
        {
          method: state.editId ? "PATCH" : "POST",
          body: JSON.stringify(formObject(form)),
        },
      );
      $("#modal").close();
      state.editId = null;
      await refresh();
      toast("Listo, los datos quedaron guardados.");
      return;
    }
    if (form.id === "sale-form") {
      const payload = salePayload();
      const plan = await api("/sales/preview" + scope(), {
        method: "POST",
        body: JSON.stringify(payload),
      });
      state.plan = { ...plan, payload };
      $("#sale-preview").innerHTML = planHtml(plan);
      return;
    }
    if (form.id === "payment-form") {
      state.payment = await api("/payments/preview" + scope(), {
        method: "POST",
        body: JSON.stringify(formObject(form)),
      });
      state.payment.customer_id = Number(form.elements.customer_id.value);
      renderPayment();
      return;
    }
    if (form.id === "statement-form") {
      const raw = formObject(form);
      state.statement = await api(
        "/statement?customer_id=" +
          raw.customer_id +
          "&date=" +
          encodeURIComponent(raw.date) +
          (state.user.role === "platform" && state.storeId
            ? "&store_id=" + state.storeId
            : ""),
      );
      renderStatement();
      renderClosures();
      return;
    }
    if (form.id === "password-form") {
      await api("/password", {
        method: "POST",
        body: JSON.stringify(formObject(form)),
      });
      form.reset();
      toast("Contraseña actualizada. Tus otras sesiones se han cerrado.");
      return;
    }
  } catch (e) {
    if (error) error.textContent = e.message;
    else toast(e.message, true);
  } finally {
    if (submit && document.contains(submit)) submit.disabled = false;
  }
}
async function doLogin(form) {
  const error = $("#login-error");
  error.textContent = "";
  try {
    const payload = formObject(form);
    payload.challenge_id = state.challenge.id;
    const result = await api("/login", {
      method: "POST",
      body: JSON.stringify(payload),
    });
    state.user = result.user;
    state.csrf = result.csrf;
    await loadData();
    renderShell();
  } catch (e) {
    error.textContent = e.message;
    form.elements.answer.value = "";
    await newChallenge();
  }
}
async function newChallenge() {
  try {
    state.challenge = await api("/challenge");
    const question = $("#challenge-question");
    if (question) question.textContent = state.challenge.question;
    const hidden = $("#challenge-id");
    if (hidden) hidden.value = state.challenge.id;
  } catch (e) {
    const error = $("#login-error");
    if (error)
      error.textContent =
        "No se pudo conectar con el servidor. Revisa que la aplicación esté encendida.";
  }
}
function renderLogin() {
  if ($("#modal").open) $("#modal").close();
  state.csrf = "";
  state.data = null;
  state.page = "dashboard";
  state.storeId = "";
  state.metricCurrency = null;
  state.dueFilter = "all";
  $("#app").innerHTML = `<main class="login" id="main">
    <header class="login-header">${brand()}<button class="text-button" data-action="help">${icon("help")} Ayuda</button></header>
    <div class="login-card">
      <section class="login-story">
        <div class="mascot-intro"><span class="welcome-tag">Tu negocio, bien acompañado</span><h1>Cada cuenta<br>en su lugar.</h1></div>
        ${mascotIllustration()}
        <p id="mascot-message" class="mascot-message">Todo listo para poner tus cuentas al día.</p>
        <div class="login-story-bottom"><span>${icon("shield")} Acceso personal</span><span>${icon("store")} Para tu negocio</span></div>
      </section>
      <section class="login-form-side"><div class="login-form-box"><div class="login-heading-icon">${icon("wallet")}</div><h2>Bienvenido de nuevo</h2><p>Ingresa para revisar tus ventas, cobros y cuentas pendientes.</p>
        <form id="login-form">
          <div class="field"><label for="login-user">Usuario</label><input id="login-user" name="username" placeholder="Tu nombre de usuario…" required autocomplete="username" maxlength="80" autocapitalize="none" spellcheck="false" aria-describedby="login-user-help"><small id="login-user-help">Usa el acceso que te asignó el administrador.</small></div>
          <div class="field"><label for="login-password">Contraseña</label><div class="password-input"><input id="login-password" type="password" name="password" placeholder="Ingresa tu contraseña…" required autocomplete="current-password" maxlength="128" aria-describedby="login-password-help"><button type="button" class="password-toggle" data-action="toggle-password" aria-label="Mostrar contraseña" aria-pressed="false" title="Mostrar contraseña">${icon("eye")}</button></div><small id="login-password-help">El botón del ojo permite mostrar u ocultar lo escrito.</small></div>
          <div class="challenge-box"><label for="login-answer"><span>Verificación de acceso</span><strong id="challenge-question">Cargando…</strong></label><input id="login-answer" name="answer" inputmode="numeric" pattern="[0-9]+" autocomplete="off" placeholder="?" required aria-label="Respuesta de la comprobación" aria-describedby="challenge-help"><button type="button" class="icon-button" data-action="new-challenge" aria-label="Otra comprobación" title="Generar otra suma">${icon("refresh")}</button></div><small class="challenge-help" id="challenge-help">Escribe el resultado de la suma para continuar.</small>
          <div id="login-error" class="error-text" role="alert" tabindex="-1"></div><button type="submit" class="btn primary">Entrar a mi cuenta ${icon("arrow")}</button>
        </form>
        <p class="login-access-help">¿Aún no tienes acceso?<br><span>Solicítalo al administrador de tu tienda.</span></p>
      </div></section>
    </div>
    <footer class="login-footer"><span>Cuenta Clara © ${new Date().getFullYear()}</span><button class="text-button" data-action="privacy">Privacidad y datos personales</button></footer>
  </main>`;
  updateMascot();
  newChallenge();
}
async function runAction(button) {
  const action = button.dataset.action;
  const id = button.dataset.id;
  switch (action) {
    case "go-payments":
      navigate("payments");
      return;
    case "collect-customer":
      navigate("payments");
      $("#payment-form [name=customer_id]").value = id;
      await submitForm($("#payment-form"));
      return;
    case "filter-dues":
      state.dueFilter = button.dataset.filter;
      renderPage();
      $("[data-filter='" + state.dueFilter + "']")?.focus();
      return;
    case "toggle-password": {
      const input = $("#login-password");
      const visible = input.type === "password";
      input.type = visible ? "text" : "password";
      button.innerHTML = icon(visible ? "eyeOff" : "eye");
      button.title = visible ? "Ocultar contraseña" : "Mostrar contraseña";
      updateMascot();
      button.setAttribute(
        "aria-label",
        visible ? "Ocultar contraseña" : "Mostrar contraseña",
      );
      button.setAttribute("aria-pressed", String(visible));
      return;
    }
    case "toggle-menu": {
      $(".sidebar")?.classList.toggle("open");
      syncNavigation();
      $("[data-action=toggle-menu]")?.setAttribute(
        "aria-expanded",
        String($(".sidebar")?.classList.contains("open")),
      );
      let mask = $(".sidebar-mask");
      if (mask) mask.remove();
      else {
        mask = document.createElement("div");
        mask.className = "sidebar-mask";
        mask.dataset.action = "toggle-menu";
        $(".app-shell").append(mask);
      }
      return;
    }
    case "close-modal":
      $("#modal").close();
      return;
    case "privacy":
      privacy();
      return;
    case "privacy-inline": {
      $("#privacy-inline").hidden = !$("#privacy-inline").hidden;
      return;
    }
    case "help":
      help();
      return;
    case "new-challenge":
      await newChallenge();
      return;
    case "new-customer":
      customerModal();
      return;
    case "edit-customer":
      customerModal(id);
      return;
    case "new-product":
      productModal();
      return;
    case "edit-product":
      productModal(id);
      return;
    case "new-store":
      storeModal();
      return;
    case "edit-store":
      storeModal(id);
      return;
    case "go-sales":
      navigate("sales");
      return;
    case "view-sale":
      saleDetails(id);
      return;
    case "open-store":
      state.storeId = id;
      state.page = "dashboard";
      await refresh();
      return;
    case "customer-statement":
      state.statementCustomer = id;
      navigate("statements");
      return;
    case "refresh":
      await refresh();
      toast("Datos actualizados.");
      return;
    case "logout":
      await api("/logout", { method: "POST", body: "{}" });
      state.user = null;
      renderLogin();
      return;
    case "add-item":
      if (state.cart.length >= 30) {
        toast("Puedes incluir hasta 30 líneas por venta.", true);
        return;
      }
      state.cart.push({ product_id: "", quantity: "1" });
      invalidatePlan();
      renderCart();
      return;
    case "remove-item":
      if (state.cart.length > 1)
        state.cart.splice(Number(button.dataset.index), 1);
      invalidatePlan();
      renderCart();
      return;
    case "confirm-sale":
      if (!state.plan) return;
      button.disabled = true;
      try {
        await api("/sales" + scope(), {
          method: "POST",
          body: JSON.stringify({
            ...state.plan.payload,
            expected_plan: Object.fromEntries(
              [
                "amount",
                "currency",
                "financed",
                "grace_days",
                "grace_interest",
                "dues",
              ].map((k) => [k, state.plan[k]]),
            ),
          }),
        });
        state.plan = null;
        state.cart = [{ product_id: "", quantity: "1" }];
        await refresh();
        toast("Venta registrada. El cronograma ya está guardado.");
      } finally {
        button.disabled = false;
      }
      return;
    case "confirm-payment":
      if (!state.payment) return;
      modal(
        "Confirmar pago recibido",
        "Esta operación se guardará en el historial.",
        '<div class="summary-row"><span>Cliente</span><strong>' +
          esc(getCustomer(state.payment.customer_id)?.name) +
          '</strong></div><div class="summary-row total"><span>Dinero recibido</span><strong>' +
          esc(amt(state.payment.amount, state.payment.currency)) +
          '</strong></div><div class="notice warn">' +
          icon("info") +
          "<span>Confirma solo si recibiste el importe completo. Si aún no lo recibiste, cierra esta ventana.</span></div>" +
          btn(
            "Sí, ya recibí el pago completo",
            "save-payment",
            "primary",
            "check",
          ),
      );
      return;
    case "save-payment":
      button.disabled = true;
      try {
        await api("/payments" + scope(), {
          method: "POST",
          body: JSON.stringify({
            customer_id: state.payment.customer_id,
            amount: state.payment.amount,
          }),
        });
        $("#modal").close();
        state.payment = null;
        await refresh();
        toast("Pago registrado. La cuenta quedó actualizada.");
      } finally {
        button.disabled = false;
      }
      return;
    case "toggle-customer":
    case "toggle-product":
    case "toggle-store": {
      const resource = {
        "toggle-customer": "customers",
        "toggle-product": "products",
        "toggle-store": "stores",
      }[action];
      const record = state.data[resource].find(
        (x) => Number(x.id) === Number(id),
      );
      state.toggle = { resource, id, active: !live(record) };
      modal(
        live(record) ? "¿Desactivar este registro?" : "¿Activar este registro?",
        record.name,
        "<div class='prose'><p>" +
          (live(record)
            ? "El historial se conserva. Este registro dejará de estar disponible para nuevas operaciones."
            : "Este registro volverá a estar disponible para nuevas operaciones.") +
          "</p></div>" +
          btn(
            live(record) ? "Desactivar" : "Activar",
            "save-toggle",
            live(record) ? "danger" : "primary",
            "check",
          ),
      );
      return;
    }
    case "save-toggle": {
      const t = state.toggle;
      button.disabled = true;
      await api("/" + t.resource + "/" + t.id + scope(), {
        method: "PATCH",
        body: JSON.stringify({ active: t.active }),
      });
      $("#modal").close();
      if (
        t.resource === "stores" &&
        !t.active &&
        String(t.id) === String(state.storeId)
      )
        state.storeId = "";
      await refresh();
      toast("Estado actualizado.");
      return;
    }
    case "export":
      exportCurrent();
      return;
    case "export-statement":
      if (!state.statement) {
        toast("Primero genera un estado de cuenta.", true);
        return;
      }
      downloadCSV("estado-de-cuenta", state.statement.dues);
      return;
    case "print":
      if (!state.statement) {
        toast("Primero genera un estado de cuenta.", true);
        return;
      }
      window.print();
      return;
  }
}
function downloadCSV(name, rows) {
  if (!rows.length) {
    toast("No hay datos para exportar.", true);
    return;
  }
  const keys = Object.keys(rows[0]).filter(
    (k) => !["password", "terms", "terms_json", "items"].includes(k),
  );
  const cell = (v) => {
    let val = typeof v === "object" ? JSON.stringify(v) : String(v ?? "");
    if (/^(?:\s*[=+@\-]|[\t\r\n])/.test(val)) val = "'" + val;
    return '"' + val.replace(/"/g, '""') + '"';
  };
  const text =
    "\uFEFF" +
    keys.map(cell).join(";") +
    "\r\n" +
    rows.map((r) => keys.map((k) => cell(r[k])).join(";")).join("\r\n");
  const url = URL.createObjectURL(
    new Blob([text], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = "cuenta-clara-" + name + "-" + today() + ".csv";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function exportCurrent() {
  const rows =
    state.page === "payments"
      ? state.data.payments
      : state.page === "sales"
        ? state.data.sales
        : state.page === "audit"
          ? state.data.audit
          : state.page === "customers"
            ? filtered(state.data.customers)
            : filtered(state.data.products);
  downloadCSV(state.page, rows);
}
document.addEventListener("click", async (event) => {
  const nav = event.target.closest("[data-page]");
  if (nav) {
    navigate(nav.dataset.page);
    return;
  }
  const button = event.target.closest("[data-action]");
  if (!button || button.disabled) return;
  try {
    await runAction(button);
  } catch (e) {
    const error = $("#modal").open ? $("#modal-error") : null;
    if (error) error.textContent = e.message;
    else toast(e.message, true);
    button.disabled = false;
  }
});
document.addEventListener("submit", (event) => {
  if (!event.target.matches("form")) return;
  event.preventDefault();
  submitForm(event.target);
});
document.addEventListener("change", async (event) => {
  const el = event.target;
  try {
    if (el.id === "closure-select") {
      const cid = $("#statement-form [name=customer_id]").value;
      const suffix = el.value
        ? "&cycle=" + encodeURIComponent(el.value)
        : "&date=" + $("#statement-form [name=date]").value;
      state.statement = await api(
        "/statement?customer_id=" +
          cid +
          suffix +
          (state.user.role === "platform" && state.storeId
            ? "&store_id=" + state.storeId
            : ""),
      );
      renderStatement();
      renderClosures();
    }
    if (el.id === "metric-currency") {
      state.metricCurrency = el.value;
      renderPage();
    }
    if (el.id === "store-switch") {
      state.storeId = el.value;
      state.statement = null;
      state.payment = null;
      await refresh();
    }
    if (el.id === "status-filter") {
      state.filter = el.value;
      renderPage();
    }
    if (el.matches("[data-cart-product]")) {
      state.cart[Number(el.dataset.cartProduct)].product_id = el.value;
      invalidatePlan();
    }
    if (el.matches("[data-cart-quantity]")) {
      state.cart[Number(el.dataset.cartQuantity)].quantity = el.value;
      invalidatePlan();
    }
    if (
      el.closest("#sale-form") &&
      !el.matches("[data-cart-product],[data-cart-quantity]")
    ) {
      invalidatePlan();
      if (el.name === "customer_id") {
        state.cart = [{ product_id: "", quantity: "1" }];
        renderCart();
      }
      if (el.name === "mode")
        $("#sale-form [name=months]").disabled = el.value === "end";
    }
    if (el.closest("#payment-form")) {
      state.payment = null;
      $("#payment-preview").innerHTML = "";
    }
  } catch (e) {
    toast(e.message, true);
  }
});
document.addEventListener("input", (event) => {
  const el = event.target;
  if (el.id === "search") {
    const start = el.selectionStart;
    state.search = el.value;
    renderPage();
    const next = $("#search");
    if (next) {
      next.focus();
      try {
        next.setSelectionRange(start, start);
      } catch {}
    }
  }
  if (el.matches("[data-cart-quantity]")) {
    state.cart[Number(el.dataset.cartQuantity)].quantity = el.value;
    invalidatePlan();
  }
  if (el.closest("#sale-form") && el.name) invalidatePlan();
});
$("#modal").addEventListener("click", (event) => {
  if (event.target === $("#modal")) {
    const rect = $("#modal").getBoundingClientRect();
    if (
      event.clientX < rect.left ||
      event.clientX > rect.right ||
      event.clientY < rect.top ||
      event.clientY > rect.bottom
    )
      $("#modal").close();
  }
});
(async () => {
  try {
    const me = await api("/me");
    state.user = me.user;
    state.csrf = me.csrf;
    await loadData();
    renderShell();
  } catch {
    renderLogin();
  }
})();
