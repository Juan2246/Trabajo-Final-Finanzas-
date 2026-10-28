"""Cierres, consultas históricas y protección de las sesiones."""
import json
import re
import unittest
from datetime import datetime, timedelta
from decimal import Decimal
from zoneinfo import ZoneInfo

import test_app
from app import close_statements, connect

Client = test_app.Client
PASSWORD = test_app.PASSWORD


class StatementTests(unittest.TestCase):
    setUp = test_app.AppTests.setUp
    tearDown = test_app.AppTests.tearDown
    sale = test_app.AppTests.sale
    rows = test_app.AppTests.rows

    def old_sale(self, **changes):
        purchased = (datetime.now(ZoneInfo("America/Lima")) - timedelta(days=100)).strftime("%Y-%m-%dT12:00")
        payload = self.sale(purchased_at=purchased, **changes)
        status, result = self.merchant.request("POST", "/api/sales", payload)
        self.assertEqual(status, 200, result)
        return payload, result

    def close(self):
        with connect(self.db_path) as db:
            close_statements(db)

    def statement(self, suffix=""):
        return self.merchant.request("GET", "/api/statement?customer_id=" + str(self.customer_id) + suffix)

    def test_early_full_payment_cancels_only_next_installment(self):
        status, sale = self.merchant.request("POST", "/api/sales", self.sale(mode="installments", months=3))
        self.assertEqual(status, 200, sale)
        today = datetime.now(ZoneInfo("America/Lima")).date().isoformat()
        self.assertGreater(sale["dues"][0]["date"], today)
        status, quote = self.merchant.request("POST", "/api/payments/preview", {"customer_id": self.customer_id})
        self.assertEqual(status, 200, quote)
        self.assertEqual(len(quote["dues"]), 1)
        self.assertEqual(Decimal(quote["late_amount"]), Decimal(0))
        status, result = self.merchant.request("POST", "/api/payments", {
            "customer_id": self.customer_id, "amount": quote["amount"],
        })
        self.assertEqual(status, 200, result)
        with connect(self.db_path) as db:
            self.assertEqual(db.execute("SELECT COUNT(*) FROM dues WHERE payment_id IS NULL").fetchone()[0], 2)
            self.assertEqual(db.execute("SELECT COUNT(*) FROM dues WHERE payment_id IS NOT NULL").fetchone()[0], 1)

    def test_statement_distinguishes_current_payment_and_future_installments(self):
        self.old_sale(mode="installments", months=12)
        status, result = self.statement()
        self.assertEqual(status, 200, result)
        total = Decimal(result["totals"]["PEN"])
        self.assertGreater(Decimal(result["amount_due"]), Decimal(0))
        self.assertGreater(Decimal(result["future_balance"]), Decimal(0))
        self.assertEqual(Decimal(result["amount_due"]) + Decimal(result["future_balance"]), total)
        self.assertTrue(result["payment_date"])
        self.assertFalse(result["is_closed"])

    def test_saved_cutoff_is_immutable_and_retroactive_sale_is_rejected(self):
        payload, sale = self.old_sale()
        self.close()
        cycle = sale["cycle"]
        status, first = self.statement("&cycle=" + cycle)
        self.assertEqual(status, 200, first)
        self.assertTrue(first["is_closed"])
        self.assertEqual(first["customer"]["rate"], "24")
        status, result = self.merchant.request("PATCH", "/api/customers/" + str(self.customer_id), {
            "rate": "30", "name": "Nombre actualizado",
        })
        self.assertEqual(status, 200, result)
        status, second = self.statement("&cycle=" + cycle)
        self.assertEqual(status, 200, second)
        self.assertEqual(first, second)
        status, result = self.merchant.request("POST", "/api/sales", payload)
        self.assertEqual(status, 400, result)
        self.assertIn("cerrado", result["error"].lower())
        self.assertEqual(self.rows("sales"), 1)

    def test_closed_statement_never_crosses_tenant_boundary(self):
        _, sale = self.old_sale()
        self.close()
        status, result = self.admin.request("POST", "/api/stores", {
            "name": "Otra tienda", "business": "Panadería", "address": "Lima",
            "admin_name": "Otro", "username": "otra-tienda", "password": PASSWORD,
        })
        self.assertEqual(status, 200, result)
        stranger = Client(self.app)
        self.assertEqual(stranger.login("otra-tienda")[0], 200)
        status, _ = stranger.request("GET", "/api/statement?customer_id=" + str(self.customer_id) + "&cycle=" + sale["cycle"])
        self.assertEqual(status, 404)

    def test_historical_statement_keeps_debt_that_was_paid_later(self):
        self.old_sale()
        yesterday = (datetime.now(ZoneInfo("America/Lima")) - timedelta(days=1)).date().isoformat()
        status, before = self.statement("&date=" + yesterday)
        self.assertEqual(status, 200, before)
        self.assertGreater(Decimal(before["totals"]["PEN"]), Decimal(0))
        status, quote = self.merchant.request("POST", "/api/payments/preview", {"customer_id": self.customer_id})
        self.assertEqual(status, 200, quote)
        self.assertEqual(self.merchant.request("POST", "/api/payments", {
            "customer_id": self.customer_id, "amount": quote["amount"],
        })[0], 200)
        status, historical = self.statement("&date=" + yesterday)
        self.assertEqual(status, 200, historical)
        self.assertEqual(before["totals"], historical["totals"])
        self.assertTrue(all(row["payment_id"] is None for row in historical["dues"]))
        status, current = self.statement()
        self.assertEqual(status, 200, current)
        self.assertEqual(Decimal(current["totals"]["PEN"]), Decimal(0))

    def test_failed_login_consumes_challenge_and_persists_lockout(self):
        client = Client(self.app)
        status, challenge = client.request("GET", "/api/challenge")
        self.assertEqual(status, 200)
        answer = str(sum(int(n) for n in re.findall(r"\d+", challenge["question"])))
        request = {"username": "tienda", "password": "Equivocada2026!",
                   "challenge_id": challenge["id"], "answer": answer}
        self.assertEqual(client.request("POST", "/api/login", request)[0], 401)
        request["password"] = PASSWORD
        self.assertEqual(client.request("POST", "/api/login", request)[0], 401)
        for _ in range(3):
            self.assertEqual(client.login("tienda", "Equivocada2026!")[0], 401)
        self.assertEqual(client.login("tienda", PASSWORD)[0], 429)
        with connect(self.db_path) as db:
            row = db.execute("SELECT * FROM login_attempts WHERE key='user:tienda'").fetchone()
        self.assertEqual(row["failures"], 5)
        self.assertGreater(row["blocked_until"], 0)

    def test_password_change_invalidates_other_sessions(self):
        other = Client(self.app)
        self.assertEqual(other.login("tienda")[0], 200)
        replacement = "NuevaSegura2026!"
        status, result = self.merchant.request("POST", "/api/password", {
            "current_password": PASSWORD, "password": replacement,
        })
        self.assertEqual(status, 200, result)
        self.assertEqual(other.request("GET", "/api/me")[0], 401)
        self.assertEqual(self.merchant.request("GET", "/api/me")[0], 200)
        fresh = Client(self.app)
        self.assertEqual(fresh.login("tienda", PASSWORD)[0], 401)
        self.assertEqual(fresh.login("tienda", replacement)[0], 200)


if __name__ == "__main__":
    unittest.main()
