"""La venta confirmada debe coincidir con el cronograma que se mostró."""
import unittest
import test_app


class PreviewTests(unittest.TestCase):
    setUp = test_app.AppTests.setUp
    tearDown = test_app.AppTests.tearDown
    sale = test_app.AppTests.sale
    rows = test_app.AppTests.rows

    def preview_payload(self):
        payload = self.sale(mode="installments", months=3)
        status, plan = self.merchant.request("POST", "/api/sales/preview", payload)
        self.assertEqual(status, 200, plan)
        keys = ("amount", "currency", "financed", "grace_days", "grace_interest", "dues")
        payload["expected_plan"] = {k: plan[k] for k in keys}
        return payload

    def test_unchanged_preview_can_be_confirmed(self):
        payload = self.preview_payload()
        status, result = self.merchant.request("POST", "/api/sales", payload)
        self.assertEqual(status, 200, result)
        self.assertEqual(self.rows("sales"), 1)

    def test_price_change_requires_another_preview(self):
        payload = self.preview_payload()
        self.assertEqual(self.merchant.request("PATCH", "/api/products/" + str(self.product_id),
                                              {"credit_price": "101.00"})[0], 200)
        status, result = self.merchant.request("POST", "/api/sales", payload)
        self.assertEqual(status, 409, result)
        self.assertEqual(self.rows("sales"), 0)

    def test_rate_change_requires_another_preview(self):
        payload = self.preview_payload()
        self.assertEqual(self.merchant.request("PATCH", "/api/customers/" + str(self.customer_id),
                                              {"rate": "50"})[0], 200)
        status, result = self.merchant.request("POST", "/api/sales", payload)
        self.assertEqual(status, 409, result)
        self.assertEqual(self.rows("dues"), 0)
