"""Cuenta Clara: servidor web, cuentas de crédito y base de datos SQLite."""
import argparse
import hashlib
import hmac
import json
import logging
import os
import re
import secrets
import sqlite3
import threading
import time
from datetime import date, datetime, timedelta
from http.cookies import SimpleCookie
from pathlib import Path
from urllib.parse import parse_qs, urlparse
from wsgiref.simple_server import make_server, WSGIServer
from socketserver import ThreadingMixIn
from zoneinfo import ZoneInfo

from finance import number, money, factor, make_plan, late_interest, month_date

ROOT = Path(__file__).resolve().parent
ZONE = ZoneInfo("America/Lima")
LOG = logging.getLogger("cuentaclara")


def now():
    return datetime.now(ZONE).replace(tzinfo=None, microsecond=0)


def stamp():
    return now().isoformat()


class Problem(Exception):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.status = status


class Database(sqlite3.Connection):
    def __exit__(self, exc_type, exc, traceback):
        try:
            return super().__exit__(exc_type, exc, traceback)
        finally:
            self.close()


def connect(path):
    db = sqlite3.connect(str(path), timeout=15, factory=Database)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA foreign_keys=ON")
    return db


def init_db(path):
    Path(path).parent.mkdir(parents=True, exist_ok=True)
    with connect(path) as db:
        db.execute("PRAGMA journal_mode=WAL")
        db.executescript((ROOT / "schema.sql").read_text(encoding="utf-8"))


def text(data, key, maximum=200, required=True):
    value = str(data.get(key, "")).strip()
    if (required and not value) or len(value) > maximum:
        raise Problem(f"Revisa el campo {key}; máximo {maximum} caracteres.")
    return value


def integer(value, minimum=1, maximum=2147483647):
    if isinstance(value, bool) or not re.fullmatch(r"-?\d+", str(value)):
        raise Problem("Ingresa un número entero válido.")
    result = int(value)
    if not minimum <= result <= maximum:
        raise Problem(f"El valor debe estar entre {minimum} y {maximum}.")
    return result


def decimal_field(data, key, minimum="0", maximum="10000000", places=2):
    value = number(data.get(key, ""))
    if value < number(minimum) or value > number(maximum) or value.as_tuple().exponent < -places:
        raise Problem(f"Revisa {key}: de {minimum} a {maximum}, hasta {places} decimales.")
    return str(value)


def boolean(value):
    if value not in (True, False, 0, 1):
        raise Problem("El valor debe ser verdadero o falso.")
    return int(bool(value))


def password_hash(password):
    if not isinstance(password, str) or not 12 <= len(password) <= 128:
        raise Problem("La contraseña debe tener entre 12 y 128 caracteres.")
    if not (re.search(r"[A-Z]", password) and re.search(r"[a-z]", password) and re.search(r"\d", password)):
        raise Problem("La contraseña debe incluir mayúsculas, minúsculas y números.")
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), salt.encode(), 310000).hex()
    return f"{salt}:{digest}"


def password_matches(password, saved):
    try:
        salt, expected = saved.split(":")
        digest = hashlib.pbkdf2_hmac("sha256", str(password).encode(), salt.encode(), 310000).hex()
        return hmac.compare_digest(digest, expected)
    except (ValueError, AttributeError):
        return False


def create_user(db, username, name, password, role, store_id=None, customer_id=None):
    if not re.fullmatch(r"[A-Za-z0-9_.@-]{3,80}", username):
        raise Problem("Usuario: de 3 a 80 letras, números, puntos, @, guiones o guion bajo.")
    if role not in ("platform", "merchant", "customer"):
        raise Problem("Perfil inválido.")
    return db.execute("INSERT INTO users(username,name,password,role,store_id,customer_id) VALUES(?,?,?,?,?,?)",
                      (username, name, password_hash(password), role, store_id, customer_id)).lastrowid


def public_user(row):
    return {k: row[k] for k in ("id", "username", "name", "role", "store_id", "customer_id")}


def audit(db, user, action, entity=None, store_id=None, detail=""):
    db.execute("INSERT INTO audit(user_id,store_id,action,entity_id,detail,created_at) VALUES(?,?,?,?,?,?)",
               (user["id"], store_id if store_id is not None else user["store_id"], action, entity, detail, stamp()))


def insert(db, table, data):
    columns = list(data)
    return db.execute(f"INSERT INTO {table}({','.join(columns)}) VALUES({','.join('?' for _ in columns)})",
                      tuple(data[k] for k in columns)).lastrowid


def require_manager(user):
    if user["role"] not in ("platform", "merchant"):
        raise Problem("Tu perfil solo permite consultar tu cuenta.", 403)


def store_scope(db, user, query, required=True):
    if user["role"] == "platform":
        value = query.get("store_id", [None])[0]
        store_id = integer(value) if value else None
    else:
        store_id = user["store_id"]
    if store_id:
        row = db.execute("SELECT * FROM stores WHERE id=?", (store_id,)).fetchone()
        if not row or not row["active"]:
            raise Problem("La tienda no está disponible.", 403)
    elif required:
        raise Problem("Primero selecciona una tienda.")
    return store_id


def scoped(db, table, entity_id, store_id):
    row = db.execute(f"SELECT * FROM {table} WHERE id=? AND store_id=?", (integer(entity_id), store_id)).fetchone()
    if not row:
        raise Problem("No se encontró el registro en esta tienda.", 404)
    return dict(row)


def customer_values(data):
    currency = text(data, "currency")
    if currency not in ("PEN", "USD"):
        raise Problem("Selecciona soles o dólares.")
    email = text(data, "email", 160)
    if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
        raise Problem("Ingresa un correo válido.")
    values = {k: text(data, k, maximum, required) for k, maximum, required in (
        ("name", 120, True), ("document", 30, True), ("phone", 30, False))}
    values.update(email=email, currency=currency, credit_limit=decimal_field(data, "credit_limit", "0.01"),
                  max_months=integer(data.get("max_months"), 1, 60),
                  cutoff_day=integer(data.get("cutoff_day"), 1, 28),
                  cutoff_hour=integer(data.get("cutoff_hour", 23), 0, 23),
                  pay_day=integer(data.get("pay_day"), 1, 28))
    for prefix in ("", "late_"):
        values[prefix + "rate"] = decimal_field(data, prefix + "rate", "0", "1000", 6)
        kind = data.get(prefix + ("rate_kind" if not prefix else "kind"))
        kind_key = "rate_kind" if not prefix else "late_kind"
        period_key = "rate_period" if not prefix else "late_period"
        freq_key = "frequency" if not prefix else "late_frequency"
        if kind not in ("effective", "nominal"):
            raise Problem("Selecciona una tasa nominal o efectiva.")
        period = integer(data.get(period_key), 30, 360)
        frequency = integer(data.get(freq_key, 12), 1, 360)
        factor(values[prefix + "rate"], kind, period, 30, frequency)
        values[kind_key], values[period_key], values[freq_key] = kind, period, frequency
    return values


def product_values(data):
    values = {k: text(data, k, maximum, required) for k, maximum, required in (
        ("name", 120, True), ("description", 500, False), ("brand", 100, False),
        ("supplier", 120, False), ("unit", 30, True), ("image", 1000, False))}
    if values["image"]:
        parsed = urlparse(values["image"])
        if parsed.scheme != "https" or not parsed.netloc:
            raise Problem("La imagen debe ser un enlace HTTPS.")
    values.update(cash_price=decimal_field(data, "cash_price", "0.01"),
                  credit_price=decimal_field(data, "credit_price", "0.01"),
                  currency=text(data, "currency"),
                  installment_allowed=boolean(data.get("installment_allowed", True)))
    if values["currency"] not in ("PEN", "USD"):
        raise Problem("La moneda no es válida.")
    return values


def outstanding(db, customer_id):
    rows = db.execute("SELECT d.principal FROM dues d JOIN sales s ON s.id=d.sale_id WHERE s.customer_id=? AND d.payment_id IS NULL", (customer_id,))
    return sum((number(r["principal"]) for r in rows), number(0))


def sale_plan(db, store_id, data):
    customer = scoped(db, "customers", data.get("customer_id"), store_id)
    if not customer["active"]:
        raise Problem("El cliente está desactivado.")
    try:
        purchased = datetime.fromisoformat(str(data.get("purchased_at", "")))
    except ValueError:
        raise Problem("Ingresa la fecha y hora de la compra.")
    if purchased.tzinfo or purchased > now() or purchased < now() - timedelta(days=3650):
        raise Problem("La compra debe estar entre hoy y los últimos 10 años, en hora de Lima.")
    mode = data.get("mode")
    months = integer(data.get("months", 1), 1, 60) if mode == "installments" else 1
    items = data.get("items")
    if not isinstance(items, list) or not 1 <= len(items) <= 50:
        raise Problem("Agrega de 1 a 50 productos a la compra.")
    seen, lines, total = set(), [], number(0)
    for item in items:
        if not isinstance(item, dict):
            raise Problem("El producto no es válido.")
        product = scoped(db, "products", item.get("product_id"), store_id)
        if product["id"] in seen:
            raise Problem("Agrupa las cantidades de un mismo producto.")
        seen.add(product["id"])
        if not product["active"] or product["currency"] != customer["currency"]:
            raise Problem("El producto debe estar activo y tener la moneda del cliente.")
        if mode == "installments" and not product["installment_allowed"]:
            raise Problem("Este producto solo se puede pagar al cierre.")
        quantity = decimal_field(item, "quantity", "0.001", "10000", 3)
        subtotal = money(number(quantity) * number(product["credit_price"]))
        if subtotal <= 0:
            raise Problem("La cantidad produce un importe menor a un céntimo.")
        total += subtotal
        lines.append(dict(product_id=product["id"], name=product["name"], quantity=quantity,
                          unit_price=product["credit_price"], subtotal=str(subtotal)))
    if total > 10000000:
        raise Problem("El importe supera el máximo permitido.")
    plan = make_plan(total, purchased, customer, mode, months)
    available = money(number(customer["credit_limit"]) - outstanding(db, customer["id"]))
    # También cuenta el interés que se agregó al capital durante la gracia.
    if number(plan["financed"]) > available:
        raise Problem(f"La compra supera el crédito disponible ({available} {customer['currency']}).")
    if db.execute("SELECT 1 FROM statements WHERE customer_id=? AND cycle=?", (customer["id"], plan["cycle"])).fetchone():
        raise Problem("Ese período ya está cerrado. Registra la compra en un período abierto.")
    plan.update(amount=str(money(total)), currency=customer["currency"], available=str(available),
                items=lines, purchased_at=purchased.isoformat(), customer_id=customer["id"], mode=mode, months=months)
    return plan, customer


def save_sale(db, store_id, data, user):
    plan, customer = sale_plan(db, store_id, data)
    expected = data.get("expected_plan")
    if expected is not None:
        keys = ("amount", "currency", "financed", "grace_days", "grace_interest", "dues")
        if not isinstance(expected, dict) or expected != {k: plan[k] for k in keys}:
            raise Problem("El acuerdo cambió desde la simulación. Vuelve a calcular y revisar las cuotas.", 409)
    values = {k: plan[k] for k in ("customer_id", "purchased_at", "mode", "months", "amount",
                                   "financed", "grace_days", "grace_interest", "currency", "cycle")}
    # Guardamos las condiciones de esta compra para no recalcularla con otra tasa.
    values.update(store_id=store_id, terms_json=json.dumps(customer), created_at=stamp())
    sale_id = insert(db, "sales", values)
    for item in plan["items"]:
        insert(db, "sale_items", dict(item, sale_id=sale_id))
    for due in plan["dues"]:
        row = dict(due)
        row["due_date"] = row.pop("date")
        insert(db, "dues", dict(row, sale_id=sale_id))
    audit(db, user, "Compra registrada", sale_id, store_id, f"{plan['amount']} {plan['currency']}")
    plan["id"] = sale_id
    return plan


def due_rows(db, store_id, customer_id=None, as_of=None):
    as_of = as_of or now().date()
    sql = """SELECT d.*,s.customer_id,c.name AS customer_name,s.currency,s.terms_json,s.purchased_at,
             p.paid_at FROM dues d JOIN sales s ON s.id=d.sale_id
             JOIN customers c ON c.id=s.customer_id LEFT JOIN payments p ON p.id=d.payment_id
             WHERE s.store_id=?"""
    args = [store_id]
    if customer_id:
        sql += " AND s.customer_id=?"
        args.append(customer_id)
    rows = []
    for raw in db.execute(sql + " ORDER BY d.due_date,s.purchased_at,d.number", args):
        row = dict(raw)
        paid_date = date.fromisoformat(row["paid_at"][:10]) if row["paid_at"] else None
        effective_date = min(as_of, paid_date) if paid_date else as_of
        interest, days = late_interest(row["amount"], date.fromisoformat(row["due_date"]),
                                       effective_date, json.loads(row.pop("terms_json")))
        if paid_date and paid_date > as_of:
            row["payment_id"] = None
            row["paid_at"] = None
        row.update(late_amount=str(interest), late_days=days, total=str(money(number(row["amount"]) + interest)))
        rows.append(row)
    return rows


def payment_preview(db, store_id, customer_id):
    customer = scoped(db, "customers", customer_id, store_id)
    pending = [r for r in due_rows(db, store_id, customer["id"]) if not r["payment_id"]]
    dues = [r for r in pending if r["due_date"] <= now().date().isoformat()]
    if not dues and pending:
        next_date = min(r["due_date"] for r in pending)
        dues = [r for r in pending if r["due_date"] == next_date]
    base = sum((number(r["amount"]) for r in dues), number(0))
    late = sum((number(r["late_amount"]) for r in dues), number(0))
    return dict(dues=dues, base_amount=str(money(base)), late_amount=str(money(late)),
                amount=str(money(base + late)), currency=customer["currency"])


def sale_rows(db, store_id, customer_id=None):
    sql = "SELECT s.*,c.name AS customer_name FROM sales s JOIN customers c ON c.id=s.customer_id WHERE s.store_id=?"
    args = [store_id]
    if customer_id:
        sql += " AND s.customer_id=?"
        args.append(customer_id)
    rows = []
    for raw in db.execute(sql + " ORDER BY s.purchased_at DESC,s.id DESC", args):
        row = dict(raw)
        row["terms"] = json.loads(row.pop("terms_json"))
        # Los datos personales se consultan desde el registro del cliente.
        row["terms"] = {k: v for k, v in row["terms"].items() if k in
                        ("rate","rate_kind","rate_period","frequency","late_rate","late_kind",
                         "late_period","late_frequency","cutoff_day","cutoff_hour","pay_day","max_months")}
        row["items"] = [dict(r) for r in db.execute("SELECT * FROM sale_items WHERE sale_id=?", (row["id"],))]
        rows.append(row)
    return rows


def snapshot_statement(db, store_id, customer_id, when):
    customer = scoped(db, "customers", customer_id, store_id)
    sales = [r for r in sale_rows(db, store_id, customer_id) if r["purchased_at"][:10] <= when.isoformat()
             and datetime.fromisoformat(r["cycle"] + f"T{r['terms']['cutoff_hour']:02}:00:00")
             <= (now() if when == now().date() else datetime.combine(when, datetime.max.time()))]
    sale_ids = {r["id"] for r in sales}
    dues = [r for r in due_rows(db, store_id, customer_id, when) if r["sale_id"] in sale_ids]
    total = sum((number(r["total"]) for r in dues if not r["payment_id"]), number(0))
    future_dates = [r["due_date"] for r in dues if not r["payment_id"] and r["due_date"] >= when.isoformat()]
    anchor = date.fromisoformat(min(future_dates)) if future_dates else when
    payable = [r for r in dues if not r["payment_id"] and r["due_date"] <= anchor.isoformat()]
    amount_due = sum((number(r["total"]) for r in payable), number(0))
    return dict(customer=customer, store=dict(db.execute("SELECT * FROM stores WHERE id=?", (store_id,)).fetchone()),
                date=when.isoformat(), dues=dues, sales=sales, payable=payable,
                payment_date=anchor.isoformat(), amount_due=str(money(amount_due)),
                future_balance=str(money(total - amount_due)),
                totals={"PEN": str(money(total)) if customer["currency"] == "PEN" else "0.00",
                        "USD": str(money(total)) if customer["currency"] == "USD" else "0.00"},
                generated_at=stamp())


def close_statements(db):
    # El cierre queda guardado. La mora sigue cambiando en las consultas del día.
    for raw in db.execute("SELECT c.* FROM customers c JOIN stores s ON s.id=c.store_id WHERE c.active=1 AND s.active=1").fetchall():
        customer = dict(raw)
        first = db.execute("SELECT MIN(cycle) AS cycle FROM sales WHERE customer_id=?", (customer["id"],)).fetchone()["cycle"]
        if not first:
            continue
        cycle = date.fromisoformat(first)
        count = 0
        while cycle <= now().date() and count < 121:
            cutoff = datetime.combine(cycle, datetime.min.time()).replace(hour=customer["cutoff_hour"])
            if cutoff <= now() and not db.execute("SELECT 1 FROM statements WHERE customer_id=? AND cycle=?", (customer["id"], cycle.isoformat())).fetchone():
                result = snapshot_statement(db, customer["store_id"], customer["id"], cycle)
                insert(db, "statements", dict(store_id=customer["store_id"], customer_id=customer["id"],
                       cycle=cycle.isoformat(), snapshot=json.dumps(result), generated_at=stamp()))
            cycle = month_date(cycle, 1, customer["cutoff_day"])
            count += 1


class Application:
    def __init__(self, db_path, testing=False):
        self.db_path, self.testing = str(db_path), testing
        init_db(self.db_path)

    def __call__(self, environ, start_response):
        headers = [("Content-Type", "application/json; charset=utf-8"), ("Cache-Control", "no-store"),
                   ("X-Content-Type-Options", "nosniff"), ("X-Frame-Options", "DENY"),
                   ("Referrer-Policy", "same-origin"),
                   ("Content-Security-Policy", "default-src 'self'; img-src 'self' https: data:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; form-action 'self'")]
        status = 200
        db = None
        try:
            path = environ.get("PATH_INFO", "/")
            method = environ.get("REQUEST_METHOD", "GET")
            if not path.startswith("/api/"):
                if method != "GET":
                    raise Problem("Método no permitido.", 405)
                names = {"/": ("index.html", "text/html"), "/app.js": ("app.js", "text/javascript"),
                         "/styles.css": ("styles.css", "text/css"), "/favicon.svg": ("favicon.svg", "image/svg+xml")}
                if path not in names:
                    raise Problem("La página no existe.", 404)
                name, mime = names[path]
                body = (ROOT / "static" / name).read_bytes()
                headers[0] = ("Content-Type", mime + "; charset=utf-8")
                start_response("200 OK", headers)
                return [body]
            length = integer(environ.get("CONTENT_LENGTH") or "0", 0, 131072)
            body_raw = environ["wsgi.input"].read(length) if length else b""
            try:
                data = json.loads(body_raw) if body_raw else {}
            except (ValueError, UnicodeError):
                raise Problem("El contenido no es un JSON válido.")
            if not isinstance(data, dict):
                raise Problem("El contenido debe ser un objeto.")
            query = parse_qs(environ.get("QUERY_STRING", ""))
            db = connect(self.db_path)
            if method not in ("GET", "HEAD"):
                if environ.get("CONTENT_TYPE", "").split(";")[0] != "application/json":
                    raise Problem("Se requiere contenido JSON.", 415)
                db.execute("BEGIN IMMEDIATE")
            result, extra = self.route(db, environ, path, method, query, data)
            db.commit()
            headers.extend(extra)
            body = json.dumps(result, ensure_ascii=False).encode("utf-8")
        except Problem as exc:
            # Los intentos de acceso fallidos también deben quedar registrados.
            if db:
                db.commit() if getattr(exc, "keep_changes", False) else db.rollback()
            status, body = exc.status, json.dumps({"error": str(exc)}, ensure_ascii=False).encode()
        except sqlite3.IntegrityError:
            if db:
                db.rollback()
            status, body = 409, json.dumps({"error": "Ya existe un usuario o documento con esos datos."}).encode()
        except (ValueError, TypeError, KeyError) as exc:
            if db:
                db.rollback()
            status, body = 400, json.dumps({"error": str(exc) if isinstance(exc, ValueError) else "Revisa los campos enviados."}).encode()
        except Exception:
            if db:
                db.rollback()
            LOG.exception("No se pudo completar la operación")
            status, body = 500, b'{"error":"No se pudo completar la operacion. Vuelve a intentarlo."}'
        finally:
            if db:
                db.close()
        labels = {200:"OK",400:"Bad Request",401:"Unauthorized",403:"Forbidden",404:"Not Found",
                  405:"Method Not Allowed",409:"Conflict",415:"Unsupported Media Type",429:"Too Many Requests",500:"Internal Server Error"}
        headers.append(("Content-Length", str(len(body))))
        start_response(f"{status} {labels.get(status, 'Error')}", headers)
        return [body]

    def route(self, db, env, path, method, query, data):
        empty = []
        if path == "/api/health" and method == "GET":
            return {"ok": True, "service": "Cuenta Clara"}, empty
        if path == "/api/challenge" and method == "GET":
            db.execute("DELETE FROM challenges WHERE expires_at<?", (int(time.time()),))
            a, b, token = secrets.randbelow(9) + 1, secrets.randbelow(9) + 1, secrets.token_urlsafe(24)
            db.execute("INSERT INTO challenges VALUES(?,?,?)", (token, str(a+b), int(time.time())+300))
            return {"id": token, "question": f"¿Cuánto es {a} + {b}?"}, empty
        if path == "/api/login" and method == "POST":
            return self.login(db, env, data)
        user, session = self.authenticate(db, env)
        if method not in ("GET", "HEAD"):
            if not hmac.compare_digest(env.get("HTTP_X_CSRF_TOKEN", ""), session["csrf"]):
                raise Problem("La sesión cambió. Recarga la página.", 403)
        if path == "/api/me" and method == "GET":
            return dict(user=public_user(user), csrf=session["csrf"], today=now().date().isoformat()), empty
        if path == "/api/logout" and method == "POST":
            audit(db, user, "Cierre de sesión")
            db.execute("DELETE FROM sessions WHERE token=?", (session["token"],))
            return {"ok": True}, [("Set-Cookie", self.cookie("", 0))]
        if path == "/api/password" and method == "POST":
            if not password_matches(data.get("current_password", ""), user["password"]):
                raise Problem("La contraseña actual no coincide.")
            hashed = password_hash(data.get("password"))
            db.execute("UPDATE users SET password=? WHERE id=?", (hashed, user["id"]))
            db.execute("DELETE FROM sessions WHERE user_id=? AND token<>?", (user["id"], session["token"]))
            audit(db, user, "Contraseña actualizada")
            return {"ok": True}, empty
        if path == "/api/stores" and method == "POST":
            if user["role"] != "platform":
                raise Problem("Solo el administrador de la plataforma puede crear tiendas.", 403)
            values = {k: text(data, k, n, req) for k,n,req in
                      (("name",120,True),("business",100,True),("address",240,False))}
            sid = insert(db, "stores", values)
            create_user(db, text(data,"username",80), text(data,"admin_name",120), data.get("password"), "merchant", sid)
            audit(db, user, "Tienda creada", sid, sid, values["name"])
            return {"id": sid}, empty
        match = re.fullmatch(r"/api/stores/(\d+)", path)
        if match and method == "PATCH":
            if user["role"] != "platform":
                raise Problem("Solo el administrador de la plataforma puede editar tiendas.", 403)
            sid = integer(match[1])
            if not db.execute("SELECT 1 FROM stores WHERE id=?", (sid,)).fetchone():
                raise Problem("Tienda no encontrada.",404)
            for key, limit in (("name",120),("business",100),("address",240)):
                if key in data:
                    db.execute(f"UPDATE stores SET {key}=? WHERE id=?", (text(data,key,limit,key!="address"),sid))
            if "active" in data:
                db.execute("UPDATE stores SET active=? WHERE id=?", (boolean(data["active"]),sid))
                if not data["active"]:
                    db.execute("DELETE FROM sessions WHERE user_id IN (SELECT id FROM users WHERE store_id=?)",(sid,))
            audit(db,user,"Tienda actualizada",sid,sid)
            return {"ok":True},empty
        store_id = store_scope(db, user, query, required=path != "/api/data")
        if path == "/api/data" and method == "GET":
            return self.all_data(db, user, store_id), empty
        if path == "/api/statement" and method == "GET":
            customer_id = integer(query.get("customer_id", [user["customer_id"]])[0])
            if user["role"] == "customer" and customer_id != user["customer_id"]:
                raise Problem("Solo puedes consultar tu propia cuenta.",403)
            when = date.fromisoformat(query.get("date",[now().date().isoformat()])[0])
            if when > now().date():
                raise Problem("El estado de cuenta no puede tener una fecha futura.")
            scoped(db, "customers", customer_id, store_id)
            cycle = query.get("cycle", [None])[0]
            if cycle:
                date.fromisoformat(cycle)
                closed = db.execute("SELECT snapshot FROM statements WHERE customer_id=? AND store_id=? AND cycle=?",
                                    (customer_id, store_id, cycle)).fetchone()
                if not closed:
                    raise Problem("No existe un cierre guardado para ese período.", 404)
                result = json.loads(closed["snapshot"])
                result["is_closed"] = True
                result["cycle"] = cycle
            else:
                result = snapshot_statement(db,store_id,customer_id,when)
                result["is_closed"] = False
            result["closed_statements"] = [dict(id=r["id"],cycle=r["cycle"],generated_at=r["generated_at"])
                 for r in db.execute("SELECT id,cycle,generated_at FROM statements WHERE customer_id=? ORDER BY cycle DESC",(customer_id,))]
            return result,empty
        require_manager(user)
        if path == "/api/customers" and method == "POST":
            values = customer_values(data)
            if data.get("consent") is not True:
                raise Problem("Se necesita el consentimiento del cliente para registrar sus datos.")
            values.update(store_id=store_id,consent_at=stamp(),consent_version="1")
            cid=insert(db,"customers",values)
            create_user(db,text(data,"username",80),values["name"],data.get("password"),"customer",store_id,cid)
            audit(db,user,"Cliente registrado",cid,store_id,values["name"])
            return {"id":cid},empty
        if path == "/api/products" and method == "POST":
            pid=insert(db,"products",dict(product_values(data),store_id=store_id))
            audit(db,user,"Producto registrado",pid,store_id)
            return {"id":pid},empty
        match=re.fullmatch(r"/api/(customers|products)/(\d+)",path)
        if match and method=="PATCH":
            table, entity_id = match[1],integer(match[2])
            previous=scoped(db,table,entity_id,store_id)
            merged=dict(previous,**data)
            values = customer_values(merged) if table=="customers" else product_values(merged)
            if table=="customers":
                pending=outstanding(db,entity_id)
                if pending and values["currency"]!=previous["currency"]:
                    raise Problem("No puedes cambiar la moneda mientras exista deuda.")
                if number(values["credit_limit"])<pending:
                    raise Problem("El límite no puede ser menor que el capital pendiente.")
            if "active" in data:
                values["active"]=boolean(data["active"])
            db.execute(f"UPDATE {table} SET "+",".join(k+"=?" for k in values)+" WHERE id=?",tuple(values.values())+(entity_id,))
            if table=="customers":
                db.execute("UPDATE users SET name=?,active=? WHERE customer_id=?",
                           (values["name"],values.get("active",previous["active"]),entity_id))
                if not values.get("active",previous["active"]):
                    db.execute("DELETE FROM sessions WHERE user_id IN (SELECT id FROM users WHERE customer_id=?)",(entity_id,))
                if data.get("password"):
                    db.execute("UPDATE users SET password=? WHERE customer_id=?",(password_hash(data["password"]),entity_id))
                    db.execute("DELETE FROM sessions WHERE user_id IN (SELECT id FROM users WHERE customer_id=?)",(entity_id,))
            audit(db,user,"Cliente actualizado" if table=="customers" else "Producto actualizado",entity_id,store_id)
            return {"ok":True},empty
        if path=="/api/sales/preview" and method=="POST":
            return sale_plan(db,store_id,data)[0],empty
        if path=="/api/sales" and method=="POST":
            return save_sale(db,store_id,data,user),empty
        if path in ("/api/payments/preview","/api/payments") and method=="POST":
            cid=integer(data.get("customer_id"))
            preview=payment_preview(db,store_id,cid)
            if path.endswith("/preview"):
                return preview,empty
            if not preview["dues"]:
                raise Problem("El cliente no tiene obligaciones pendientes.")
            paid_amount=decimal_field(data,"amount","0.01","1000000000000000000000000000")
            if number(paid_amount)!=number(preview["amount"]):
                raise Problem(f"El pago debe ser completo: {preview['amount']} {preview['currency']}. Actualiza la liquidación.")
            values={k:preview[k] for k in ("base_amount","late_amount","amount","currency")}
            values.update(store_id=store_id,customer_id=cid,paid_at=stamp())
            pid=insert(db,"payments",values)
            for due in preview["dues"]:
                insert(db,"payment_lines",dict(payment_id=pid,due_id=due["id"],late_days=due["late_days"],late_amount=due["late_amount"]))
                db.execute("UPDATE dues SET payment_id=? WHERE id=? AND payment_id IS NULL",(pid,due["id"]))
            audit(db,user,"Pago registrado",pid,store_id,f"{preview['amount']} {preview['currency']}")
            return dict(preview,id=pid),empty
        raise Problem("La ruta no existe o no admite esta operación.",404)

    def cookie(self, token, seconds=28800):
        secure = "; Secure" if os.environ.get("SECURE_COOKIES")=="1" else ""
        return f"cc_session={token}; Path=/; HttpOnly; SameSite=Lax; Max-Age={seconds}{secure}"

    def authenticate(self,db,env):
        cookies=SimpleCookie()
        try:
            cookies.load(env.get("HTTP_COOKIE",""))
        except Exception:
            raise Problem("Inicia sesión para continuar.",401)
        token=cookies.get("cc_session")
        session=db.execute("SELECT * FROM sessions WHERE token=? AND expires_at>?",
                   (token.value if token else "",int(time.time()))).fetchone()
        if not session:
            raise Problem("Inicia sesión para continuar.",401)
        user=db.execute("SELECT * FROM users WHERE id=? AND active=1",(session["user_id"],)).fetchone()
        if not user:
            raise Problem("La cuenta está desactivada.",401)
        if user["store_id"] and not db.execute("SELECT 1 FROM stores WHERE id=? AND active=1",(user["store_id"],)).fetchone():
            raise Problem("La tienda está desactivada.",403)
        return dict(user),dict(session)

    def login(self,db,env,data):
        username=str(data.get("username",""))[:80].strip()
        current=int(time.time())
        ip=env.get("REMOTE_ADDR","local")
        keys=["user:"+username.lower(),"ip:"+ip]
        for key in keys:
            row=db.execute("SELECT * FROM login_attempts WHERE key=?",(key,)).fetchone()
            if row and row["blocked_until"]>current:
                raise Problem("Demasiados intentos. Espera 15 minutos.",429)
        challenge=db.execute("SELECT * FROM challenges WHERE id=?",(str(data.get("challenge_id","")),)).fetchone()
        db.execute("DELETE FROM challenges WHERE id=?",(str(data.get("challenge_id","")),))
        user=db.execute("SELECT * FROM users WHERE username=? COLLATE NOCASE",(username,)).fetchone()
        good_challenge=challenge and challenge["expires_at"]>=current and hmac.compare_digest(challenge["answer"],str(data.get("answer","")).strip())
        supplied_password = str(data.get("password",""))
        good_password = len(supplied_password) <= 128 and password_matches(supplied_password,user["password"] if user else "0:0")
        good_store=not user or not user["store_id"] or db.execute("SELECT 1 FROM stores WHERE id=? AND active=1",(user["store_id"],)).fetchone()
        if not (good_challenge and user and user["active"] and good_password and good_store):
            for key in keys:
                row=db.execute("SELECT * FROM login_attempts WHERE key=?",(key,)).fetchone()
                failures=(row["failures"] if row and row["updated_at"]>current-900 else 0)+1
                threshold=20 if key.startswith("ip:") else 5
                db.execute("INSERT OR REPLACE INTO login_attempts VALUES(?,?,?,?)",
                           (key,failures,current+900 if failures>=threshold else 0,current))
            error=Problem("Revisa usuario, contraseña y código de verificación.",401)
            error.keep_changes=True
            raise error
        db.execute("DELETE FROM login_attempts WHERE key=?",("user:"+username.lower(),))
        db.execute("DELETE FROM sessions WHERE expires_at<?",(current,))
        token,csrf=secrets.token_urlsafe(48),secrets.token_urlsafe(32)
        db.execute("INSERT INTO sessions VALUES(?,?,?,?)",(token,user["id"],csrf,current+28800))
        audit(db,user,"Inicio de sesión")
        return dict(user=public_user(user),csrf=csrf),[("Set-Cookie",self.cookie(token))]

    def all_data(self,db,user,store_id):
        stores=[dict(r) for r in db.execute("SELECT * FROM stores ORDER BY name")] if user["role"]=="platform" else []
        result=dict(user=public_user(user),store=None,stores=stores,customers=[],products=[],sales=[],dues=[],
                    payments=[],audit=[],today=now().date().isoformat())
        if not store_id:
            return result
        result["store"]=dict(db.execute("SELECT * FROM stores WHERE id=?",(store_id,)).fetchone())
        cid=user["customer_id"] if user["role"]=="customer" else None
        sql="SELECT * FROM customers WHERE store_id=?" + (" AND id=?" if cid else "")+" ORDER BY name"
        result["customers"]=[dict(r) for r in db.execute(sql,(store_id,cid) if cid else (store_id,))]
        for customer in result["customers"]:
            customer["outstanding"]=str(money(outstanding(db,customer["id"])))
            customer["available"]=str(money(number(customer["credit_limit"])-number(customer["outstanding"])))
        if not cid:
            result["products"]=[dict(r) for r in db.execute("SELECT * FROM products WHERE store_id=? ORDER BY name",(store_id,))]
            result["audit"]=[dict(r) for r in db.execute("""SELECT a.*,u.name AS user_name FROM audit a
                   LEFT JOIN users u ON u.id=a.user_id WHERE a.store_id=? ORDER BY a.id DESC LIMIT 200""",(store_id,))]
        result["sales"]=sale_rows(db,store_id,cid)
        result["dues"]=due_rows(db,store_id,cid)
        sql="SELECT p.*,c.name AS customer_name FROM payments p JOIN customers c ON c.id=p.customer_id WHERE p.store_id=?"
        result["payments"]=[dict(r) for r in db.execute(sql+(" AND p.customer_id=?" if cid else "")+" ORDER BY p.id DESC",
                                                    (store_id,cid) if cid else (store_id,))]
        return result


def create_app(db_path,testing=False):
    return Application(db_path,testing)


def seed_demo(path):
    with connect(path) as db:
        if db.execute("SELECT 1 FROM users LIMIT 1").fetchone():
            print("La base ya tiene usuarios; no se agregaron datos de ejemplo.")
            return
        password=os.environ.get("DEMO_PASSWORD") or "Demo"+secrets.token_urlsafe(14)+"7"
        admin_password=os.environ.get("ADMIN_PASSWORD") or password
        admin_id=create_user(db,"admin","Administrador",admin_password,"platform")
        sid=insert(db,"stores",dict(name="Bodega La Esquina",business="Bodega de barrio",address="Av. Los Olivos 245, Lima"))
        create_user(db,"tienda","Juan Torres",password,"merchant",sid)
        c=customer_values(dict(name="María Flores",document="00000001",email="maria@example.com",phone="999000111",
          currency="PEN",credit_limit="2500",rate="24",rate_kind="effective",rate_period=360,frequency=12,
          late_rate="36",late_kind="effective",late_period=360,late_frequency=12,
          max_months=12,cutoff_day=20,cutoff_hour=23,pay_day=26))
        cid=insert(db,"customers",dict(c,store_id=sid,consent_at=stamp(),consent_version="demo"))
        create_user(db,"cliente","María Flores",password,"customer",sid,cid)
        ids=[]
        for name,unit,cash,credit in (("Arroz extra","kg","4.50","4.80"),("Aceite vegetal","litro","9.00","9.50"),
                                     ("Leche evaporada","lata","4.00","4.30"),("Servicio de reparto","servicio","5.00","5.00")):
            ids.append(insert(db,"products",dict(store_id=sid,name=name,unit=unit,cash_price=cash,
                              credit_price=credit,currency="PEN",installment_allowed=1)))
        actor=dict(id=admin_id,store_id=sid)
        old=(now()-timedelta(days=70)).replace(hour=10,minute=0)
        save_sale(db,sid,dict(customer_id=cid,purchased_at=old.isoformat(),mode="end",months=1,
                             items=[dict(product_id=ids[0],quantity="10"),dict(product_id=ids[1],quantity="2")]),actor)
        save_sale(db,sid,dict(customer_id=cid,purchased_at=(now()-timedelta(days=10)).isoformat(),mode="installments",months=3,
                             items=[dict(product_id=ids[0],quantity="30")]),actor)
        close_statements(db)
        print("\nDatos ficticios listos. Usuarios: admin (plataforma), tienda (negocio), cliente (consulta).")
        print("Contraseña de tienda y cliente:",password)
        print("Contraseña de admin:",admin_password)
        print("Guarda estas claves; no se volverán a mostrar.\n")


def closing_worker(path):
    while True:
        try:
            with connect(path) as db:
                close_statements(db)
        except Exception:
            LOG.exception("Error al cerrar estados de cuenta")
        time.sleep(60)


class ThreadedServer(ThreadingMixIn,WSGIServer):
    daemon_threads=True


def main():
    parser=argparse.ArgumentParser(description="Cuenta Clara")
    parser.add_argument("--demo",action="store_true",help="Crear datos ficticios solo en una base vacía.")
    parser.add_argument("--init",action="store_true",help="Crear el administrador y salir.")
    parser.add_argument("--close-statements",action="store_true",help="Ejecutar cierres pendientes y salir.")
    args=parser.parse_args()
    path=os.environ.get("DB_PATH",str(ROOT/"data"/"cuenta.db"))
    application=create_app(path)
    if args.demo:
        seed_demo(path)
    if args.close_statements:
        with connect(path) as db:
            close_statements(db)
        return
    with connect(path) as db:
        if not db.execute("SELECT 1 FROM users LIMIT 1").fetchone():
            password=os.environ.get("ADMIN_PASSWORD")
            if not password:
                if args.init:
                    from getpass import getpass
                    password=getpass("Contraseña del administrador (12+ caracteres, mayúsculas, minúsculas y números): ")
                else:
                    raise SystemExit("Primero ejecuta python app.py --init o python app.py --demo.")
            create_user(db,os.environ.get("ADMIN_USER","admin"),"Administrador",password,"platform")
    if args.init:
        print("Administrador creado. Ejecuta python app.py.")
        return
    threading.Thread(target=closing_worker,args=(path,),daemon=True).start()
    host,port=os.environ.get("HOST","127.0.0.1"),int(os.environ.get("PORT","8000"))
    print(f"Cuenta Clara disponible en http://{host}:{port}",flush=True)
    with make_server(host,port,application,server_class=ThreadedServer) as server:
        server.serve_forever()


if __name__=="__main__":
    logging.basicConfig(level=logging.INFO)
    main()
