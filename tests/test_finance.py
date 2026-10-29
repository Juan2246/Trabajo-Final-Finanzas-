"""Casos numéricos y fechas límite del motor financiero."""
import unittest
from datetime import date, datetime
from decimal import Decimal

from finance import (
    commercial_days, due_anchor, factor, late_interest,
    make_plan, money, month_date, number,
)


def terms(**updates):
    result = {
        "rate": "12", "rate_kind": "nominal", "rate_period": 360, "frequency": 12,
        "late_rate": "24", "late_kind": "nominal", "late_period": 360,
        "late_frequency": 12, "max_months": 12,
        "cutoff_day": 20, "cutoff_hour": 18, "pay_day": 26,
    }
    result.update(updates)
    return result


class NumbersTests(unittest.TestCase):
    def test_decimal_without_binary_float(self):
        self.assertEqual(number("0.1") + number("0.2"), Decimal("0.3"))

    def test_round_half_up(self):
        self.assertEqual(money("1.005"), Decimal("1.01"))
        self.assertEqual(money("1.004"), Decimal("1.00"))

    def test_non_finite_and_bad_numbers(self):
        for value in ("NaN", "Infinity", "-Infinity", "abc", None, True):
            with self.subTest(value=value), self.assertRaises(ValueError):
                number(value)

    def test_overlarge_money_gives_validation_error(self):
        with self.assertRaises(ValueError):
            money("1e100")

    def test_effective_monthly(self):
        self.assertEqual(factor("1", "effective", 30, 30), Decimal("1.01"))

    def test_nominal_annual_compounding(self):
        self.assertEqual(factor("12", "nominal", 360, 30, 12), Decimal("1.01"))
        self.assertEqual(factor("12", "nominal", 360, 360, 12), Decimal("1.01") ** 12)

    def test_equivalent_nominal_and_effective_rates(self):
        annual_effective = ((Decimal("1.01") ** 12) - 1) * 100
        a = factor(annual_effective, "effective", 360, 45)
        b = factor("12", "nominal", 360, 45, 12)
        self.assertLess(abs(a - b), Decimal("1e-26"))

    def test_nominal_monthly_uses_monthly_compounding(self):
        self.assertEqual(factor("2", "nominal", 30, 60, 12), Decimal("1.0404"))

    def test_zero_interest_and_zero_days(self):
        self.assertEqual(factor("0", "nominal", 360, 300, 12), Decimal(1))
        self.assertEqual(factor("50", "effective", 360, 0), Decimal(1))

    def test_invalid_rate_inputs(self):
        cases = [
            ("-1", "effective", 360, 30, 12),
            ("1001", "effective", 360, 30, 12),
            ("NaN", "effective", 360, 30, 12),
            ("2", "bad", 360, 30, 12),
            ("2", "effective", 365, 30, 12),
            ("2", "nominal", 360, -1, 12),
            ("2", "nominal", 360, 30, 7),
            ("2", "nominal", 360, 1.5, 12),
        ]
        for args in cases:
            with self.subTest(args=args), self.assertRaises(ValueError):
                factor(*args)


class CalendarTests(unittest.TestCase):
    def test_eleven_grace_days(self):
        self.assertEqual(commercial_days(date(2026, 9, 15), date(2026, 9, 26)), 11)

    def test_months_are_thirty_days(self):
        self.assertEqual(commercial_days(date(2026, 1, 26), date(2026, 2, 26)), 30)
        self.assertEqual(commercial_days(date(2026, 2, 26), date(2026, 3, 26)), 30)
        self.assertEqual(commercial_days(date(2026, 1, 31), date(2026, 2, 1)), 1)

    def test_february_commercial_convention(self):
        self.assertEqual(commercial_days(date(2026, 2, 28), date(2026, 3, 1)), 3)

    def test_reverse_dates_rejected(self):
        with self.assertRaises(ValueError):
            commercial_days(date(2026, 9, 26), date(2026, 9, 15))

    def test_exact_cutoff_included_and_one_second_later_excluded(self):
        exact = datetime(2026, 9, 20, 18)
        later = datetime(2026, 9, 20, 18, 0, 1)
        self.assertEqual(due_anchor(exact, 20, 18, 26), (date(2026, 9, 20), date(2026, 9, 26)))
        self.assertEqual(due_anchor(later, 20, 18, 26), (date(2026, 10, 20), date(2026, 10, 26)))

    def test_payment_before_cutoff_moves_to_following_month(self):
        self.assertEqual(due_anchor(datetime(2026, 9, 15), 20, 18, 10),
                         (date(2026, 9, 20), date(2026, 10, 10)))

    def test_year_rollover(self):
        self.assertEqual(due_anchor(datetime(2026, 12, 21), 20, 18, 26),
                         (date(2027, 1, 20), date(2027, 1, 26)))

    def test_month_date_clamps_short_months(self):
        self.assertEqual(month_date(date(2026, 1, 31), 1, 31), date(2026, 2, 28))
        self.assertEqual(month_date(date(2028, 1, 31), 1, 31), date(2028, 2, 29))

    def test_invalid_schedule(self):
        for cutoff, hour, payment in [(32, 18, 26), (20, 24, 26), (20, 18, 32), (0, 18, 26)]:
            with self.subTest(cutoff=cutoff, hour=hour, payment=payment), self.assertRaises(ValueError):
                due_anchor(datetime(2026, 9, 15), cutoff, hour, payment)

    def test_cutoff_31_in_february_and_leap_year(self):
        for year, last in ((2026, 28), (2028, 29)):
            with self.subTest(year=year):
                self.assertEqual(due_anchor(datetime(year, 2, last, 18), 31, 18, 31),
                                 (date(year, 2, last), date(year, 2, last)))
                self.assertEqual(due_anchor(datetime(year, 2, last, 18, 0, 1), 31, 18, 31),
                                 (date(year, 3, 31), date(year, 3, 31)))

    def test_day_30_recovers_after_february(self):
        plan = make_plan("300", datetime(2026, 1, 20), terms(rate="0", pay_day=30), "installments", 3)
        self.assertEqual([d["date"] for d in plan["dues"]], ["2026-02-28", "2026-03-30", "2026-04-30"])

    def test_day_31_at_year_boundary(self):
        self.assertEqual(due_anchor(datetime(2026, 12, 31, 23, 1), 31, 23, 31),
                         (date(2027, 1, 31), date(2027, 1, 31)))


class PaymentPlanTests(unittest.TestCase):
    def test_course_example(self):
        plan = make_plan("1000", datetime(2026, 9, 15, 10), terms(), "installments", 3)
        self.assertEqual(plan["cycle"], "2026-09-20")
        self.assertEqual(plan["grace_days"], 11)
        expected_financed = money(Decimal("1000") * (Decimal("1.01") ** (Decimal(11) / 30)))
        self.assertEqual(Decimal(plan["financed"]), expected_financed)
        self.assertEqual(Decimal(plan["grace_interest"]), expected_financed - 1000)
        self.assertEqual([d["date"] for d in plan["dues"]], ["2026-10-26", "2026-11-26", "2026-12-26"])
        self.assertEqual(sum(Decimal(d["principal"]) for d in plan["dues"]), expected_financed)
        self.assertEqual(plan["financed"], "1003.66")
        self.assertEqual([d["amount"] for d in plan["dues"]], ["341.27", "341.27", "341.26"])

    def test_documented_effective_purchase_with_nominal_mora(self):
        agreement = terms(rate="24", rate_kind="effective")
        plan = make_plan("500", datetime(2026, 9, 21, 12), agreement, "end", 1)
        due = plan["dues"][0]
        self.assertEqual(plan["cycle"], "2026-10-20")
        self.assertEqual(due["date"], "2026-10-26")
        self.assertEqual(due["interest"], "10.57")
        self.assertEqual(due["amount"], "510.57")
        mora, days = late_interest(due["amount"], date(2026, 10, 26), date(2026, 11, 5), agreement)
        self.assertEqual((mora, days), (Decimal("3.04"), 9))
        self.assertEqual(Decimal(due["amount"]) + mora, Decimal("513.61"))

    def test_known_french_installments_and_last_cent(self):
        plan = make_plan("1000", datetime(2026, 1, 26, 12),
                         terms(cutoff_day=26, cutoff_hour=12), "installments", 3)
        self.assertEqual(plan["grace_days"], 0)
        self.assertEqual([d["amount"] for d in plan["dues"]], ["340.02", "340.02", "340.03"])
        self.assertEqual([d["interest"] for d in plan["dues"]], ["10.00", "6.70", "3.37"])
        self.assertEqual(sum(Decimal(d["principal"]) for d in plan["dues"]), Decimal("1000.00"))

    def test_zero_rate_installments(self):
        plan = make_plan("100", datetime(2026, 9, 15), terms(rate="0"), "installments", 3)
        self.assertEqual([d["amount"] for d in plan["dues"]], ["33.33", "33.33", "33.34"])
        self.assertEqual(plan["grace_interest"], "0.00")

    def test_single_installment_after_grace(self):
        plan = make_plan("100", datetime(2026, 9, 15), terms(rate="0"), "installments", 1)
        self.assertEqual(plan["dues"][0]["date"], "2026-10-26")
        self.assertEqual(plan["dues"][0]["amount"], "100.00")

    def test_end_of_cycle_interest(self):
        plan = make_plan("100", datetime(2026, 9, 15), terms(rate="3", rate_kind="effective", rate_period=30), "end", 1)
        expected = money(100 * (Decimal("1.03") ** (Decimal(11) / 30)))
        self.assertEqual(plan["dues"][0]["date"], "2026-09-26")
        self.assertEqual(Decimal(plan["dues"][0]["amount"]), expected)
        self.assertEqual(Decimal(plan["dues"][0]["interest"]), expected - 100)
        self.assertEqual(plan["financed"], "100.00")

    def test_purchase_after_cutoff_rolls_whole_cycle(self):
        plan = make_plan("100", datetime(2026, 9, 21), terms(), "end", 1)
        self.assertEqual(plan["dues"][0]["date"], "2026-10-26")

    def test_invalid_principal_mode_and_term(self):
        cases = [("0", "end", 1), ("-1", "end", 1), ("100", "bad", 1),
                 ("100", "end", 2), ("100", "installments", 0),
                 ("100", "installments", 13), ("100", "installments", True)]
        for principal, mode, months in cases:
            with self.subTest(principal=principal, mode=mode, months=months), self.assertRaises(ValueError):
                make_plan(principal, datetime(2026, 9, 15), terms(), mode, months)

    def test_too_small_payment_rejected(self):
        with self.assertRaises(ValueError):
            make_plan("0.01", datetime(2026, 9, 15), terms(rate="0"), "installments", 12)

    def test_many_terms_reconcile(self):
        for rate in ("0", "12", "45.25"):
            for months in (1, 3, 6, 12):
                with self.subTest(rate=rate, months=months):
                    plan = make_plan("1245.67", datetime(2026, 9, 15), terms(rate=rate), "installments", months)
                    self.assertEqual(sum(Decimal(d["principal"]) for d in plan["dues"]),
                                     Decimal(plan["financed"]))
                    self.assertTrue(all(Decimal(d["amount"]) > 0 for d in plan["dues"]))
                    for due in plan["dues"]:
                        self.assertEqual(Decimal(due["principal"]) + Decimal(due["interest"]),
                                         Decimal(due["amount"]))


class LateInterestTests(unittest.TestCase):
    def test_no_mora_before_or_on_due_date(self):
        for paid in (date(2026, 9, 20), date(2026, 9, 26)):
            self.assertEqual(late_interest("100", date(2026, 9, 26), paid, terms()), (Decimal("0.00"), 0))

    def test_mora_on_total_obligation(self):
        amount, days = late_interest("110", date(2026, 9, 26), date(2026, 10, 26), terms())
        self.assertEqual(days, 30)
        self.assertEqual(amount, Decimal("2.20"))

    def test_effective_mora_fractional_period(self):
        amount, days = late_interest("100", date(2026, 9, 26), date(2026, 10, 11),
                                    terms(late_rate="3", late_kind="effective", late_period=30))
        self.assertEqual(days, 15)
        self.assertEqual(amount, money(100 * (Decimal("1.03") ** Decimal("0.5") - 1)))

    def test_negative_debt_rejected(self):
        with self.assertRaises(ValueError):
            late_interest("-1", date(2026, 9, 26), date(2026, 10, 26), terms())


if __name__ == "__main__":
    unittest.main()
