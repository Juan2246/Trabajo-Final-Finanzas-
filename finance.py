"""Cálculos de Cuenta Clara: dinero en Decimal y meses comerciales de 30 días."""
from calendar import monthrange
from datetime import date, datetime
from decimal import Decimal, InvalidOperation, ROUND_HALF_UP, localcontext

CENT = Decimal("0.01")
ZERO = Decimal("0")


def number(value):
    """Convertimos el dato sin pasar por float ni aceptar infinito o NaN."""
    try:
        result = Decimal(str(value))
    except (InvalidOperation, ValueError, TypeError):
        raise ValueError("Ingresa un número válido.") from None
    if not result.is_finite():
        raise ValueError("El número debe ser finito.")
    return result


def money(value):
    try:
        return number(value).quantize(CENT, rounding=ROUND_HALF_UP)
    except InvalidOperation:
        raise ValueError("El monto tiene demasiados dígitos.") from None


def _integer(value, minimum, maximum, message):
    if isinstance(value, bool) or not isinstance(value, int) or not minimum <= value <= maximum:
        raise ValueError(message)
    return value


def commercial_days(start, end):
    """Base 30/360: los días 31 se tratan como 30."""
    if end < start:
        raise ValueError("La fecha final no puede ser anterior a la inicial.")
    return ((end.year - start.year) * 360 + (end.month - start.month) * 30
            + min(end.day, 30) - min(start.day, 30))


def month_date(reference, offset, day):
    _integer(day, 1, 31, "El día del mes no es válido.")
    if isinstance(offset, bool) or not isinstance(offset, int):
        raise ValueError("El número de meses debe ser entero.")
    index = reference.year * 12 + reference.month - 1 + offset
    year, month = divmod(index, 12)
    if not 1 <= year <= 9999:
        raise ValueError("La fecha queda fuera del rango permitido.")
    return date(year, month + 1, min(day, monthrange(year, month + 1)[1]))


def factor(rate, kind, period_days, days, frequency=12):
    """Devuelve el factor acumulado; la tasa recibida está en porcentaje."""
    rate = number(rate)
    if rate < ZERO or rate > 1000:
        raise ValueError("La tasa debe estar entre 0 y 1000 por ciento.")
    if kind not in ("effective", "nominal"):
        raise ValueError("Selecciona una tasa nominal o efectiva.")
    if isinstance(period_days, bool) or not isinstance(period_days, int) or period_days not in (30, 360):
        raise ValueError("El período de la tasa debe ser mensual o anual.")
    _integer(days, 0, 3600000, "El número de días no es válido.")
    if isinstance(frequency, bool) or not isinstance(frequency, int) or frequency not in (1, 2, 4, 6, 12, 24, 360):
        raise ValueError("La frecuencia de capitalización no es válida.")
    with localcontext() as ctx:
        ctx.prec = 36
        fraction = rate / 100
        if kind == "effective":
            return (1 + fraction) ** (Decimal(days) / period_days)
        if period_days == 30:
            # Para una nominal mensual, la capitalización es cada mes.
            return (1 + fraction) ** (Decimal(days) / 30)
        return (1 + fraction / frequency) ** (Decimal(days) * frequency / 360)


def due_anchor(purchased, cutoff_day, cutoff_hour, pay_day):
    """Ubica el corte y el pago base de una compra, incluyendo la hora."""
    if not isinstance(purchased, datetime):
        raise ValueError("La compra necesita fecha y hora.")
    _integer(cutoff_day, 1, 31, "El día de corte debe estar entre 1 y 31.")
    _integer(pay_day, 1, 31, "El día de pago debe estar entre 1 y 31.")
    _integer(cutoff_hour, 0, 23, "La hora de corte debe estar entre 0 y 23.")
    # En meses cortos se usa el último día disponible, conservando el día pactado.
    cutoff = datetime.combine(month_date(purchased.date(), 0, cutoff_day), datetime.min.time()).replace(hour=cutoff_hour)
    offset = 0 if purchased <= cutoff else 1
    cycle = month_date(purchased.date(), offset, cutoff_day)
    payment = month_date(cycle, 0 if pay_day >= cutoff_day else 1, pay_day)
    return cycle, payment


def make_plan(principal, purchased, terms, mode, months):
    principal = money(principal)
    if principal <= ZERO:
        raise ValueError("El importe debe ser mayor que cero.")
    if mode not in ("end", "installments"):
        raise ValueError("Selecciona pago al cierre o pago en cuotas.")
    _integer(months, 1, 60, "El plazo debe estar entre 1 y 60 meses.")
    max_months = _integer(terms["max_months"], 1, 60, "El plazo máximo no es válido.")
    if mode == "end" and months != 1:
        raise ValueError("El pago al cierre tiene una sola obligación.")
    if mode == "installments" and months > max_months:
        raise ValueError("El plazo supera los meses permitidos para este cliente.")
    cycle, anchor = due_anchor(
        purchased, terms["cutoff_day"], terms["cutoff_hour"], terms["pay_day"]
    )
    rate_args = (terms["rate"], terms["rate_kind"], terms["rate_period"])
    frequency = terms["frequency"]
    days = commercial_days(purchased.date(), anchor)
    financed = money(principal * factor(*rate_args, days, frequency))
    if mode == "end":
        return {
            "cycle": cycle.isoformat(), "grace_days": 0, "grace_interest": "0.00",
            "financed": str(principal),
            "dues": [{
                "number": 1, "date": anchor.isoformat(), "principal": str(principal),
                "interest": str(financed - principal), "amount": str(financed),
            }],
        }
    monthly = factor(*rate_args, 30, frequency) - 1
    with localcontext() as ctx:
        ctx.prec = 36
        payment = money(financed / months if monthly == 0 else
                        financed * monthly / (1 - (1 + monthly) ** (-months)))
    if payment <= ZERO:
        raise ValueError("El importe es demasiado pequeño para ese plazo.")
    balance, dues = financed, []
    for index in range(1, months + 1):
        interest = money(balance * monthly)
        amortization = balance if index == months else money(payment - interest)
        # No dejamos que el redondeo genere capital negativo o cuotas vacías.
        if amortization < ZERO or amortization > balance:
            raise ValueError("El monto y el plazo producen cuotas no válidas.")
        if index < months and amortization == balance:
            raise ValueError("El importe es demasiado pequeño para ese plazo.")
        amount = money(amortization + interest)
        dues.append({
            "number": index, "date": month_date(anchor, index, terms["pay_day"]).isoformat(),
            "principal": str(amortization), "interest": str(interest), "amount": str(amount),
        })
        balance = money(balance - amortization)
    return {
        "cycle": cycle.isoformat(), "grace_days": days,
        "grace_interest": str(financed - principal), "financed": str(financed), "dues": dues,
    }


def late_interest(amount, due, paid, terms):
    """La mora se calcula sobre la obligación vencida completa."""
    amount = money(amount)
    if amount < ZERO:
        raise ValueError("La deuda no puede ser negativa.")
    if paid <= due:
        return money(ZERO), 0
    days = commercial_days(due, paid)
    accrued = factor(terms["late_rate"], terms["late_kind"], terms["late_period"],
                     days, terms["late_frequency"])
    return money(amount * (accrued - 1)), days
