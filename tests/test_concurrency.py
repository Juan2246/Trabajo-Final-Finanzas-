"""Casos en paralelo para que un crédito no se gaste ni se cobre dos veces."""
from concurrent.futures import ThreadPoolExecutor
from decimal import Decimal
import threading
import unittest

import test_app
from app import connect


class ConcurrencyTests(unittest.TestCase):
    # Compartimos la preparación de la API, sin volver a heredar sus pruebas.
    setUp = test_app.AppTests.setUp
    tearDown = test_app.AppTests.tearDown
    sale = test_app.AppTests.sale
    rows = test_app.AppTests.rows

    def second_merchant(self):
        client = test_app.Client(self.app)
        status, payload = client.login("tienda", test_app.PASSWORD)
        self.assertEqual(status, 200, payload)
        return client

    def run_together(self, clients, path, payload):
        barrier = threading.Barrier(len(clients))

        def send(client):
            barrier.wait(timeout=10)
            return client.request("POST", path, payload)

        with ThreadPoolExecutor(max_workers=len(clients)) as pool:
            futures = [pool.submit(send, client) for client in clients]
            return [future.result(timeout=30) for future in futures]

    def limit_to_one_purchase(self):
        status, payload = self.merchant.request(
            "PATCH", "/api/customers/" + str(self.customer_id), {"credit_limit": "100.00"}
        )
        self.assertEqual(status, 200, payload)

    def test_two_sales_cannot_spend_the_same_available_credit(self):
        self.limit_to_one_purchase()
        other = self.second_merchant()
        responses = self.run_together([self.merchant, other], "/api/sales", self.sale())
        self.assertEqual(sorted(status for status, _ in responses), [200, 400], responses)
        self.assertEqual(self.rows("sales"), 1)
        self.assertEqual(self.rows("sale_items"), 1)
        self.assertEqual(self.rows("dues"), 1)
        status, result = self.merchant.request("GET", "/api/data")
        self.assertEqual(status, 200, result)
        self.assertEqual(Decimal(result["customers"][0]["outstanding"]), Decimal("100.00"))
        self.assertEqual(Decimal(result["customers"][0]["available"]), Decimal("0.00"))

    def test_two_payments_cannot_collect_the_same_debt(self):
        other = self.second_merchant()
        status, payload = self.merchant.request("POST", "/api/sales", self.sale())
        self.assertEqual(status, 200, payload)
        # La deuda vence hoy para que el caso no dependa del día en que corre CI.
        with connect(self.db_path) as db:
            db.execute("UPDATE dues SET due_date=?", (test_app.TODAY().isoformat(),))
        status, quote = self.merchant.request(
            "POST", "/api/payments/preview", {"customer_id": self.customer_id}
        )
        self.assertEqual(status, 200, quote)
        responses = self.run_together(
            [self.merchant, other], "/api/payments",
            {"customer_id": self.customer_id, "amount": quote["amount"]},
        )
        self.assertEqual(sorted(status for status, _ in responses), [200, 400], responses)
        self.assertEqual(self.rows("payments"), 1)
        self.assertEqual(self.rows("payment_lines"), 1)
        with connect(self.db_path) as db:
            paid, total = db.execute(
                "SELECT COUNT(payment_id), COUNT(*) FROM dues"
            ).fetchone()
        self.assertEqual((paid, total), (1, 1))

    def test_paid_principal_releases_the_credit_for_another_purchase(self):
        self.limit_to_one_purchase()
        status, payload = self.merchant.request("POST", "/api/sales", self.sale())
        self.assertEqual(status, 200, payload)
        status, _ = self.merchant.request("POST", "/api/sales", self.sale())
        self.assertEqual(status, 400)
        with connect(self.db_path) as db:
            db.execute("UPDATE dues SET due_date=?", (test_app.TODAY().isoformat(),))
        status, quote = self.merchant.request(
            "POST", "/api/payments/preview", {"customer_id": self.customer_id}
        )
        self.assertEqual(status, 200, quote)
        status, payload = self.merchant.request(
            "POST", "/api/payments",
            {"customer_id": self.customer_id, "amount": quote["amount"]},
        )
        self.assertEqual(status, 200, payload)
        status, data = self.merchant.request("GET", "/api/data")
        self.assertEqual(status, 200, data)
        self.assertEqual(Decimal(data["customers"][0]["available"]), Decimal("100.00"))
        status, payload = self.merchant.request("POST", "/api/sales", self.sale())
        self.assertEqual(status, 200, payload)
        self.assertEqual(self.rows("sales"), 2)
        self.assertEqual(self.rows("payments"), 1)

    def test_a_product_cannot_mix_currencies_with_the_customer(self):
        status, product = self.merchant.request("POST", "/api/products", {
            "name": "Servicio en dólares", "description": "", "brand": "",
            "supplier": "", "unit": "servicio", "image": "",
            "cash_price": "90.00", "credit_price": "100.00",
            "currency": "USD", "installment_allowed": True,
        })
        self.assertEqual(status, 200, product)
        status, payload = self.merchant.request("POST", "/api/sales", self.sale(
            items=[{"product_id": product["id"], "quantity": "1"}],
        ))
        self.assertEqual(status, 400, payload)
        self.assertEqual(self.rows("sales"), 0)
        self.assertEqual(self.rows("dues"), 0)

    def test_non_finite_or_extreme_limits_do_not_change_the_customer(self):
        for value in ("Infinity", "-Infinity", "NaN", "1e9999", "-1"):
            with self.subTest(value=value):
                status, payload = self.merchant.request(
                    "PATCH", "/api/customers/" + str(self.customer_id),
                    {"credit_limit": value},
                )
                self.assertEqual(status, 400, payload)
        status, result = self.merchant.request("GET", "/api/data")
        self.assertEqual(status, 200, result)
        self.assertEqual(Decimal(result["customers"][0]["credit_limit"]), Decimal("500.00"))


if __name__ == "__main__":
    unittest.main()
