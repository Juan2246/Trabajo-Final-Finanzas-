"""Pruebas de permisos y operaciones usando la API WSGI, sin servidor externo."""
import io
import json
import re
import tempfile
import unittest
from datetime import datetime, timedelta
from decimal import Decimal
from urllib.parse import urlsplit
from zoneinfo import ZoneInfo

from app import connect, create_app, create_user

PASSWORD = "PruebaSegura2026!"
TODAY = lambda: datetime.now(ZoneInfo("America/Lima")).date()


class Client:
    def __init__(self, app):
        self.app = app
        self.cookie = ""
        self.csrf = ""

    def request(self, method, path, data=None, csrf=True):
        split = urlsplit(path)
        body = json.dumps(data).encode() if data is not None else b""
        environ = {
            "REQUEST_METHOD": method, "PATH_INFO": split.path,
            "QUERY_STRING": split.query, "SERVER_NAME": "localhost",
            "SERVER_PORT": "8000", "SERVER_PROTOCOL": "HTTP/1.1",
            "wsgi.version": (1, 0), "wsgi.url_scheme": "http",
            "wsgi.input": io.BytesIO(body), "wsgi.errors": io.StringIO(),
            "wsgi.multithread": False, "wsgi.multiprocess": False,
            "wsgi.run_once": False, "CONTENT_LENGTH": str(len(body)),
            "CONTENT_TYPE": "application/json", "REMOTE_ADDR": "127.0.0.1",
            "HTTP_HOST": "localhost:8000",
        }
        if self.cookie:
            environ["HTTP_COOKIE"] = self.cookie
        if csrf and self.csrf:
            environ["HTTP_X_CSRF_TOKEN"] = self.csrf
        response = {}

        def start_response(status, headers, exc_info=None):
            response["status"] = int(status.split()[0])
            response["headers"] = headers

        iterator = self.app(environ, start_response)
        try:
            raw = b"".join(iterator)
        finally:
            if hasattr(iterator, "close"):
                iterator.close()
        for key, value in response["headers"]:
            if key.lower() == "set-cookie":
                self.cookie = value.split(";", 1)[0]
        try:
            payload = json.loads(raw)
        except (ValueError, UnicodeDecodeError):
            payload = raw.decode(errors="replace")
        if isinstance(payload, dict) and payload.get("csrf"):
            self.csrf = payload["csrf"]
        return response["status"], payload

    def login(self, username, password=PASSWORD):
        status, challenge = self.request("GET", "/api/challenge")
        if status != 200:
            raise AssertionError((status, challenge))
        numbers = [int(n) for n in re.findall(r"\d+", challenge["question"])]
        answer = sum(numbers)
        return self.request("POST", "/api/login", {
            "username": username, "password": password,
            "challenge_id": challenge["id"], "answer": str(answer),
        })


class AppTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.db_path = self.tmp.name + "/test.db"
        self.app = create_app(self.db_path, testing=True)
        with connect(self.db_path) as db:
            create_user(db, "admin", "Administrador", PASSWORD, "platform")
        self.admin = Client(self.app)
        self.assertEqual(self.admin.login("admin")[0], 200)
        status, result = self.admin.request("POST", "/api/stores", {
            "name": "Bodega de prueba", "business": "Bodega",
            "address": "Lima", "admin_name": "Dueño",
            "username": "tienda", "password": PASSWORD,
        })
        self.assertIn(status, (200, 201), result)
        self.store_id = result["id"]
        self.merchant = Client(self.app)
        self.assertEqual(self.merchant.login("tienda")[0], 200)
        self.customer_data = {
            "name": "María Prueba", "document": "12345678",
            "email": "maria@example.test", "phone": "999111222",
            "currency": "PEN", "credit_limit": "500.00",
            "rate": "24", "rate_kind": "effective", "rate_period": 360,
            "frequency": 12, "late_rate": "36", "late_kind": "effective",
            "late_period": 360, "late_frequency": 12, "max_months": 12,
            "cutoff_day": 20, "cutoff_hour": 23, "pay_day": 26,
            "consent": True, "username": "cliente", "password": PASSWORD,
        }
        status, result = self.merchant.request("POST", "/api/customers", self.customer_data)
        self.assertIn(status, (200, 201), result)
        self.customer_id = result["id"]
        status, result = self.merchant.request("POST", "/api/products", {
            "name": "Arroz", "description": "Bolsa", "brand": "Prueba",
            "supplier": "", "unit": "bolsa", "image": "",
            "cash_price": "90.00", "credit_price": "100.00",
            "currency": "PEN", "installment_allowed": True,
        })
        self.assertIn(status, (200, 201), result)
        self.product_id = result["id"]

    def tearDown(self):
        self.tmp.cleanup()

    def sale(self, **changes):
        payload = {
            "customer_id": self.customer_id,
            "items": [{"product_id": self.product_id, "quantity": "1"}],
            "mode": "end", "months": 1,
            "purchased_at": (datetime.now(ZoneInfo("America/Lima")) - timedelta(days=2)).strftime("%Y-%m-%dT%H:%M"),
        }
        payload.update(changes)
        return payload

    def rows(self, table):
        with connect(self.db_path) as db:
            return db.execute("SELECT COUNT(*) FROM " + table).fetchone()[0]

    def test_authentication_and_logout(self):
        stranger = Client(self.app)
        self.assertEqual(stranger.request("GET", "/api/data")[0], 401)
        status, me = self.merchant.request("GET", "/api/me")
        self.assertEqual(status, 200)
        self.assertEqual(me["user"]["role"], "merchant")
        self.assertTrue(me["csrf"])
        self.assertIn(self.merchant.request("POST", "/api/logout", {})[0], (200, 204))
        self.assertEqual(self.merchant.request("GET", "/api/me")[0], 401)

    def test_mutations_require_csrf(self):
        before = self.rows("products")
        status, _ = self.merchant.request("POST", "/api/products", {
            "name": "No permitido", "cash_price": "1",
            "credit_price": "1", "currency": "PEN",
        }, csrf=False)
        self.assertEqual(status, 403)
        self.assertEqual(self.rows("products"), before)

    def test_customer_is_read_only(self):
        customer = Client(self.app)
        self.assertEqual(customer.login("cliente")[0], 200)
        status, data = customer.request("GET", "/api/data")
        self.assertEqual(status, 200)
        self.assertEqual(len(data["customers"]), 1)
        self.assertEqual(data["customers"][0]["id"], self.customer_id)
        self.assertEqual(data["audit"], [])
        self.assertEqual(customer.request("GET", "/api/statement?customer_id=999999")[0], 403)
        self.assertEqual(customer.request("POST", "/api/sales", self.sale())[0], 403)
        self.assertEqual(customer.request("POST", "/api/payments", {
            "customer_id": self.customer_id, "amount": "100",
        })[0], 403)

    def test_merchants_cannot_administer_platform(self):
        status, _ = self.merchant.request("POST", "/api/stores", {
            "name": "Otra", "business": "Bodega", "address": "",
            "admin_name": "Intruso", "username": "intruso", "password": PASSWORD,
        })
        self.assertEqual(status, 403)

    def test_tenant_ids_do_not_grant_access(self):
        status, result = self.admin.request("POST", "/api/stores", {
            "name": "Segunda tienda", "business": "Panadería", "address": "",
            "admin_name": "Otro dueño", "username": "tienda2", "password": PASSWORD,
        })
        self.assertIn(status, (200, 201))
        other = Client(self.app)
        self.assertEqual(other.login("tienda2")[0], 200)
        status, data = other.request("GET", "/api/data?store_id=" + str(self.store_id))
        if status == 200:
            self.assertEqual(data["customers"], [])
            self.assertEqual(data["sales"], [])
        else:
            self.assertEqual(status, 403)
        self.assertIn(other.request("POST", "/api/sales", self.sale())[0], (400, 403, 404, 422))
        self.assertIn(other.request("GET", "/api/statement?customer_id=" + str(self.customer_id))[0], (403, 404))

    def test_preview_does_not_write_and_sale_uses_catalog_price(self):
        status, plan = self.merchant.request("POST", "/api/sales/preview", self.sale())
        self.assertEqual(status, 200, plan)
        self.assertEqual(Decimal(plan["amount"]), Decimal("100.00"))
        self.assertEqual(self.rows("sales"), 0)
        payload = self.sale()
        payload["items"][0]["unit_price"] = "0.01"
        status, saved = self.merchant.request("POST", "/api/sales", payload)
        self.assertIn(status, (200, 201), saved)
        self.assertEqual(Decimal(saved["amount"]), Decimal("100.00"))
        self.assertEqual(self.rows("sales"), 1)
        self.assertEqual(self.rows("dues"), 1)

    def test_credit_limit_and_failed_sale_are_atomic(self):
        payload = self.sale(items=[{"product_id": self.product_id, "quantity": "6"}])
        self.assertIn(self.merchant.request("POST", "/api/sales", payload)[0], (400, 409, 422))
        self.assertEqual(self.rows("sales"), 0)
        self.assertEqual(self.rows("sale_items"), 0)
        self.assertEqual(self.rows("dues"), 0)
        payload = self.sale(items=[
            {"product_id": self.product_id, "quantity": "1"},
            {"product_id": 999999, "quantity": "1"},
        ])
        self.assertIn(self.merchant.request("POST", "/api/sales", payload)[0], (400, 403, 404, 422))
        self.assertEqual(self.rows("sales"), 0)

    def test_future_purchase_and_invalid_amounts_are_rejected(self):
        future = (TODAY() + timedelta(days=2)).isoformat() + "T12:00"
        self.assertIn(self.merchant.request("POST", "/api/sales", self.sale(purchased_at=future))[0], (400, 422))
        for quantity in ("0", "-1", "NaN", "Infinity"):
            payload = self.sale(items=[{"product_id": self.product_id, "quantity": quantity}])
            self.assertIn(self.merchant.request("POST", "/api/sales", payload)[0], (400, 422))
        self.assertEqual(self.rows("sales"), 0)

    def test_consent_and_strong_password_are_required(self):
        payload = dict(self.customer_data, document="87654321", username="cliente2", consent=False)
        self.assertIn(self.merchant.request("POST", "/api/customers", payload)[0], (400, 422))
        payload.update(consent=True, password="1234")
        self.assertIn(self.merchant.request("POST", "/api/customers", payload)[0], (400, 422))
        self.assertEqual(self.rows("customers"), 1)

    def test_installments_reconcile_and_keep_agreed_terms(self):
        status, plan = self.merchant.request("POST", "/api/sales", self.sale(mode="installments", months=3))
        self.assertIn(status, (200, 201), plan)
        self.assertEqual(len(plan["dues"]), 3)
        self.assertEqual(sum(Decimal(row["principal"]) for row in plan["dues"]), Decimal(plan["financed"]))
        with connect(self.db_path) as db:
            original = db.execute("SELECT terms_json FROM sales").fetchone()[0]
        self.merchant.request("PATCH", "/api/customers/" + str(self.customer_id), {"active": False})
        with connect(self.db_path) as db:
            self.assertEqual(db.execute("SELECT terms_json FROM sales").fetchone()[0], original)
        self.assertIn(self.merchant.request("POST", "/api/sales", self.sale())[0], (400, 403, 409, 422))

    def test_only_full_payment_of_due_balance_is_accepted(self):
        status, sale = self.merchant.request("POST", "/api/sales", self.sale())
        self.assertIn(status, (200, 201), sale)
        with connect(self.db_path) as db:
            db.execute("UPDATE dues SET due_date=?", ((TODAY() - timedelta(days=5)).isoformat(),))
        status, quote = self.merchant.request("POST", "/api/payments/preview", {"customer_id": self.customer_id})
        self.assertEqual(status, 200, quote)
        self.assertGreater(Decimal(quote["late_amount"]), Decimal(0))
        partial = str(Decimal(quote["amount"]) - Decimal("1"))
        self.assertIn(self.merchant.request("POST", "/api/payments", {
            "customer_id": self.customer_id, "amount": partial,
        })[0], (400, 409, 422))
        self.assertEqual(self.rows("payments"), 0)
        status, paid = self.merchant.request("POST", "/api/payments", {
            "customer_id": self.customer_id, "amount": quote["amount"],
        })
        self.assertIn(status, (200, 201), paid)
        self.assertEqual(self.rows("payments"), 1)
        self.assertEqual(self.rows("payment_lines"), 1)
        self.assertIn(self.merchant.request("POST", "/api/payments", {
            "customer_id": self.customer_id, "amount": quote["amount"],
        })[0], (400, 409, 422))
        self.assertEqual(self.rows("payments"), 1)

    def test_store_deactivation_blocks_existing_sessions(self):
        status, result = self.admin.request("PATCH", "/api/stores/" + str(self.store_id), {"active": False})
        self.assertEqual(status, 200, result)
        self.assertIn(self.merchant.request("GET", "/api/data")[0], (401, 403))

    def test_login_challenge_cannot_be_reused(self):
        client = Client(self.app)
        status, challenge = client.request("GET", "/api/challenge")
        self.assertEqual(status, 200)
        numbers = [int(n) for n in re.findall(r"\d+", challenge["question"])]
        body = {"username": "tienda", "password": PASSWORD, "challenge_id": challenge["id"], "answer": str(sum(numbers))}
        self.assertEqual(client.request("POST", "/api/login", body)[0], 200)
        self.assertIn(client.request("POST", "/api/login", body)[0], (400, 401, 403, 422))

    def test_passwords_are_never_returned_or_stored_in_plaintext(self):
        status, data = self.merchant.request("GET", "/api/data")
        self.assertEqual(status, 200)
        self.assertNotIn(PASSWORD, json.dumps(data))
        with connect(self.db_path) as db:
            passwords = [row[0] for row in db.execute("SELECT password FROM users")]
        self.assertTrue(passwords)
        self.assertTrue(all(value != PASSWORD and len(value) > 40 for value in passwords))


if __name__ == "__main__":
    unittest.main()
