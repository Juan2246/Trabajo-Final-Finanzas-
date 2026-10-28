"""Recorrido real en Chromium. Guarda capturas para revisar la interfaz."""
import base64
import os
from pathlib import Path
import re
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request

from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = ROOT / "test-results"
ARTIFACTS.mkdir(exist_ok=True)



def api_data(context, url):
    response = context.request.get(url + "/api/data")
    assert response.status == 200, response.text()
    return response.json()


def mutate_with_ui(page, path, click, method="POST"):
    from urllib.parse import urlsplit
    with page.expect_response(lambda r: urlsplit(r.url).path == path and r.request.method == method) as pending:
        click()
    response = pending.value
    assert response.status == 200, response.text()
    result = response.json()
    page.wait_for_load_state("networkidle")
    return result


def log_in(page, url, username, password):
    page.goto(url, wait_until="networkidle")
    page.locator("#login-form").wait_for()
    page.locator("#login-form [name=username]").fill(username)
    page.locator("#login-form [name=password]").fill(password)
    page.wait_for_function("document.querySelector('#challenge-question').textContent.includes('+')")
    question = page.locator("#challenge-question").inner_text()
    answer = sum(int(x) for x in re.findall(r"\d+", question))
    page.locator("#login-form [name=answer]").fill(str(answer))
    page.locator("#login-form button[type=submit]").click()
    page.locator("#page-title").wait_for()
    page.wait_for_load_state("networkidle")


def business_flows(page, context, url):
    from datetime import datetime, timedelta
    from zoneinfo import ZoneInfo

    page.locator('[data-page="customers"]').first.click()
    page.locator('[data-action="new-customer"]').first.click()
    customer = page.locator("#customer-form")
    for name, value in {
        "name": "Lucía Prueba", "document": "77777777",
        "email": "lucia@example.test", "phone": "999555666",
        "credit_limit": "1000", "username": "lucia.prueba",
        "password": "LuciaSegura2026!",
    }.items():
        customer.locator('[name="' + name + '"]').fill(value)
    customer.locator("[name=consent]").check()
    created = mutate_with_ui(page, "/api/customers",
        lambda: customer.locator('button[type=submit]').click())
    customer_id = created["id"]
    assert any(c["id"] == customer_id for c in api_data(context, url)["customers"])

    page.locator('[data-page="products"]').first.click()
    page.locator('[data-action="new-product"]').first.click()
    product = page.locator("#product-form")
    for name, value in {
        "name": "Galletas de avena", "description": "Producto de prueba del recorrido",
        "brand": "La Esquina", "unit": "bolsa",
        "cash_price": "8.00", "credit_price": "10.00",
    }.items():
        product.locator('[name="' + name + '"]').fill(value)
    result = mutate_with_ui(page, "/api/products",
        lambda: product.locator('button[type=submit]').click())
    product_id = result["id"]

    page.locator('[data-action="edit-product"][data-id="' + str(product_id) + '"]').click()
    page.locator("#product-form [name=credit_price]").fill("12.00")
    mutate_with_ui(page, "/api/products/" + str(product_id),
        lambda: page.locator("#product-form button[type=submit]").click(), method="PATCH")
    record = next(p for p in api_data(context, url)["products"] if p["id"] == product_id)
    assert float(record["credit_price"]) == 12
    for active in (False, True):
        page.locator('[data-action="toggle-product"][data-id="' + str(product_id) + '"]').click()
        mutate_with_ui(page, "/api/products/" + str(product_id),
            lambda: page.locator('[data-action="save-toggle"]').click(), method="PATCH")
        record = next(p for p in api_data(context, url)["products"] if p["id"] == product_id)
        assert bool(record["active"]) == active

    page.locator('[data-page="sales"]').first.click()
    sale = page.locator("#sale-form")
    sale.locator("[name=customer_id]").select_option(str(customer_id))
    old_purchase = (datetime.now(ZoneInfo("America/Lima")) - timedelta(days=60)).strftime("%Y-%m-%dT12:00")
    sale.locator("[name=purchased_at]").fill(old_purchase)
    sale.locator("[name=mode]").select_option("end")
    page.locator('[data-cart-product="0"]').select_option(str(product_id))
    page.locator('[data-cart-quantity="0"]').fill("2")
    plan = mutate_with_ui(page, "/api/sales/preview",
        lambda: sale.locator('button[type=submit]').click())
    assert float(plan["amount"]) == 24
    saved = mutate_with_ui(page, "/api/sales",
        lambda: page.locator('[data-action="confirm-sale"]').click())
    sale_id = saved["id"]
    assert any(s["id"] == sale_id for s in api_data(context, url)["sales"])
    page.screenshot(path=str(ARTIFACTS / "05-venta-registrada.png"), full_page=True)

    page.locator('[data-page="statements"]').first.click()
    page.locator("#statement-form [name=customer_id]").select_option(str(customer_id))
    with page.expect_response(lambda r: "/api/statement?" in r.url) as pending:
        page.locator("#statement-form button[type=submit]").click()
    assert pending.value.status == 200, pending.value.text()
    page.locator("#statement-output .statement").wait_for()
    assert "Lucía Prueba" in page.locator("#statement-output").inner_text()
    page.screenshot(path=str(ARTIFACTS / "06-estado-de-cuenta.png"), full_page=True)
    with page.expect_download() as downloaded:
        page.locator('[data-action="export-statement"]').click()
    downloaded.value.save_as(str(ARTIFACTS / "estado-de-cuenta.csv"))
    page.emulate_media(media="print")
    assert page.locator("#statement-output .statement").is_visible()
    page.pdf(path=str(ARTIFACTS / "estado-de-cuenta.pdf"), format="A4", print_background=True)
    page.emulate_media(media="screen")

    page.locator('[data-page="payments"]').first.click()
    page.locator("#payment-form [name=customer_id]").select_option(str(customer_id))
    quote = mutate_with_ui(page, "/api/payments/preview",
        lambda: page.locator("#payment-form button[type=submit]").click())
    assert float(quote["amount"]) > 24
    page.locator('[data-action="confirm-payment"]').click()
    payment = mutate_with_ui(page, "/api/payments",
        lambda: page.locator('[data-action="save-payment"]').click())
    assert payment["id"]
    data = api_data(context, url)
    assert all(d["payment_id"] for d in data["dues"] if d["sale_id"] == sale_id)
    page.screenshot(path=str(ARTIFACTS / "07-pago-registrado.png"), full_page=True)


def platform_and_customer(browser, url):
    customer_context = browser.new_context(viewport={"width": 1280, "height": 900}, locale="es-PE")
    customer_page = customer_context.new_page()
    log_in(customer_page, url, "lucia.prueba", "LuciaSegura2026!")
    assert customer_page.locator('[data-page="customers"]').count() == 0
    data = api_data(customer_context, url)
    assert len(data["customers"]) == 1
    assert data["customers"][0]["name"] == "Lucía Prueba"
    customer_page.locator('[data-page="statements"]').first.click()
    with customer_page.expect_response(lambda r: "/api/statement?" in r.url) as pending:
        customer_page.locator("#statement-form button[type=submit]").click()
    assert pending.value.status == 200
    customer_page.locator("#statement-output .statement").wait_for()
    customer_page.screenshot(path=str(ARTIFACTS / "08-vista-cliente.png"), full_page=True)
    customer_context.close()

    admin_context = browser.new_context(viewport={"width": 1440, "height": 1000}, locale="es-PE")
    admin_page = admin_context.new_page()
    log_in(admin_page, url, "admin", "AdminSegura2026!")
    admin_page.locator('[data-page="stores"]').first.click()
    admin_page.locator('[data-action="new-store"]').first.click()
    form = admin_page.locator("#store-form")
    for name, value in {
        "name": "Panadería del recorrido", "business": "Panadería",
        "address": "Lima", "admin_name": "Pedro Prueba",
        "username": "panaderia.prueba", "password": "PanaderiaSegura2026!",
    }.items():
        form.locator('[name="' + name + '"]').fill(value)
    result = mutate_with_ui(admin_page, "/api/stores",
        lambda: form.locator("button[type=submit]").click())
    assert any(s["id"] == result["id"] for s in api_data(admin_context, url)["stores"])
    admin_page.screenshot(path=str(ARTIFACTS / "09-vista-plataforma.png"), full_page=True)
    admin_context.close()


def main():
    with tempfile.TemporaryDirectory() as temp:
        # El sistema elige un puerto disponible para no chocar con otra prueba.
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        url = "http://127.0.0.1:" + str(port)
        env = dict(os.environ, DB_PATH=str(Path(temp) / "browser.db"),
                   HOST="127.0.0.1", PORT=str(port),
                   ADMIN_PASSWORD="AdminSegura2026!", DEMO_PASSWORD="PruebaSegura2026!")
        with open(ARTIFACTS / "server.log", "w", encoding="utf-8") as logfile:
            server = subprocess.Popen([sys.executable, "app.py", "--demo"], cwd=ROOT,
                                      env=env, stdout=logfile, stderr=subprocess.STDOUT)
            try:
                for _ in range(100):
                    if server.poll() is not None:
                        raise AssertionError("El servidor terminó antes de iniciar. Revisar server.log.")
                    try:
                        with urllib.request.urlopen(url + "/api/health", timeout=1) as response:
                            if response.status == 200:
                                break
                    except Exception:
                        time.sleep(0.2)
                else:
                    raise AssertionError("El servidor no respondió a /api/health.")

                with sync_playwright() as p:
                    browser = p.chromium.launch()
                    context = browser.new_context(viewport={"width": 1440, "height": 1000},
                                                  locale="es-PE", timezone_id="America/Lima")
                    page = context.new_page()
                    errors = []
                    page.on("pageerror", lambda error: errors.append(str(error)))
                    try:
                        page.goto(url, wait_until="networkidle")
                        page.locator("#login-form").wait_for()
                        page.screenshot(path=str(ARTIFACTS / "01-acceso.png"), full_page=True)
                        page.locator("#login-form input[name=username]").fill("tienda")
                        page.locator("#login-form input[name=password]").fill("PruebaSegura2026!")
                        question = page.locator("#challenge-question").inner_text()
                        answer = sum(int(x) for x in re.findall(r"\d+", question))
                        page.locator("#login-form input[name=answer]").fill(str(answer))
                        page.locator("#login-form button[type=submit]").click()
                        page.locator("#dashboard").wait_for()
                        page.screenshot(path=str(ARTIFACTS / "02-resumen-escritorio.png"), full_page=True)
                        print("UI_PREVIEW:" + base64.b64encode(page.screenshot(type="jpeg", quality=35)).decode(), flush=True)
                        assert "Cuenta" in page.title(), page.title()
                        assert page.locator("#page-title").inner_text().strip()

                        for section in ("customers", "products", "sales", "payments", "statements", "audit", "settings"):
                            page.locator('[data-page="' + section + '"]').first.click()
                            page.wait_for_timeout(150)
                            assert page.locator("#page-title").inner_text().strip(), section
                            assert page.evaluate("document.documentElement.scrollWidth <= innerWidth + 2"), section

                        business_flows(page, context, url)
                        platform_and_customer(browser, url)
                        page.locator('[data-page="dashboard"]').first.click()
                        page.set_viewport_size({"width": 390, "height": 844})
                        page.wait_for_timeout(200)
                        assert page.evaluate("document.documentElement.scrollWidth <= innerWidth + 2"), "Desborde horizontal en celular."
                        page.screenshot(path=str(ARTIFACTS / "03-resumen-celular.png"), full_page=True)
                        menu = page.locator('[data-action="toggle-menu"]')
                        if menu.is_visible():
                            menu.click()
                            page.locator('[data-page="customers"]').first.click()
                            page.screenshot(path=str(ARTIFACTS / "04-clientes-celular.png"), full_page=True)
                        assert not errors, "\n".join(errors)
                    except Exception:
                        page.screenshot(path=str(ARTIFACTS / "error.png"), full_page=True)
                        raise
                    finally:
                        context.close()
                        browser.close()
            finally:
                server.terminate()
                try:
                    server.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    server.kill()
                    server.wait()


if __name__ == "__main__":
    main()
