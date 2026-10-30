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

from playwright.sync_api import sync_playwright, expect

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
    expect(page.locator("#challenge-question")).to_contain_text("+")
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
    customer.locator('[name=cutoff_day]').fill('31')
    customer.locator('[name=pay_day]').fill('31')
    expect(customer.locator('[name=cutoff_day]')).to_have_attribute('max', '31')
    customer.locator('.field-help').first.locator('summary').click()
    expect(customer.locator('.field-help[open] p')).to_be_visible()
    customer.locator('.field-help[open] summary').click()
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
    product.locator(".image-library summary").click()
    product.locator('[data-imagen="/catalogo/producto.jpg"]').click()
    expect(product.locator("#product-image-preview img")).to_be_visible()
    result = mutate_with_ui(page, "/api/products",
        lambda: product.locator('button[type=submit]').click())
    product_id = result["id"]

    page.locator('[data-action="edit-product"][data-id="' + str(product_id) + '"]').click()
    page.locator("#product-form [name=credit_price]").fill("12.00")
    mutate_with_ui(page, "/api/products/" + str(product_id),
        lambda: page.locator("#product-form button[type=submit]").click(), method="PATCH")
    record = next(p for p in api_data(context, url)["products"] if p["id"] == product_id)
    assert float(record["credit_price"]) == 12
    assert record["image"] == "/catalogo/producto.jpg"
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
    expect(page.locator('#statement-output .late-breakdown')).to_be_visible()
    expect(page.locator('#statement-output .late-breakdown')).to_contain_text('Tasa pactada')
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
    expect(page.locator('#payment-preview .late-breakdown')).to_be_visible()
    assert all(d['late_days'] > 0 and 'late_rate' in d and 'late_base' in d for d in quote['dues'])
    page.locator('[data-action="confirm-payment"]').click()
    payment = mutate_with_ui(page, "/api/payments",
        lambda: page.locator('[data-action="save-payment"]').click())
    assert payment["id"]
    data = api_data(context, url)
    assert all(d["payment_id"] for d in data["dues"] if d["sale_id"] == sale_id)
    page.screenshot(path=str(ARTIFACTS / "07-pago-registrado.png"), full_page=True)


def dashboard_flows(page, context, url):
    page.locator('a[data-page="customers"]').click()
    expect(page).to_have_url(url + '/#customers')
    page.locator('a[data-page="products"]').click()
    page.go_back()
    expect(page.locator('a[data-page="customers"]')).to_have_attribute('aria-current', 'page')
    page.reload(wait_until='networkidle')
    expect(page.locator('a[data-page="customers"]')).to_have_attribute('aria-current', 'page')
    page.locator('.skip').focus()
    page.keyboard.press('Enter')
    expect(page.locator('a[data-page="customers"]')).to_have_attribute('aria-current', 'page')
    page.locator('a[data-page="dashboard"]').click()
    data = api_data(context, url)
    pending = [d for d in data["dues"] if not d["payment_id"] and d["currency"] == "PEN"]
    due = [d for d in pending if d["due_date"] <= data["today"]]
    page.locator('.ledger-tab[data-filter="due"]').click()
    expect(page.locator('.collection-table tbody tr')).to_have_count(min(8, len(due)))
    expect(page.locator('.ledger-tab[data-filter="due"]')).to_have_attribute("aria-pressed", "true")
    page.locator('.ledger-tab[data-filter="upcoming"]').click()
    expect(page.locator('.collection-table tbody tr')).to_have_count(min(8, len(pending) - len(due)))
    page.locator('#metric-currency').select_option('USD')
    expect(page.locator('.collection-table tbody tr')).to_have_count(0)
    expect(page.locator('.portfolio-balance > strong')).to_contain_text('0.00')
    page.locator('#metric-currency').select_option('PEN')
    page.locator('.ledger-tab[data-filter="due"]').click()
    customer_id = page.locator('[data-action="collect-customer"]').first.get_attribute('data-id')
    quote = mutate_with_ui(page, '/api/payments/preview',
        lambda: page.locator('[data-action="collect-customer"]').first.click())
    expect(page.locator('#payment-form [name=customer_id]')).to_have_value(customer_id)
    assert quote['dues'] and float(quote['amount']) > 0
    expect(page.locator('#payment-preview .payment-total')).to_be_visible()
    page.locator('[data-page="dashboard"]').first.click()
    page.locator('.ledger-tab[data-filter="all"]').click()


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
    customer_page.locator('[data-page="settings"]').first.click()
    customer_page.locator('[data-action="privacy-export"]').click()
    customer_page.locator('[data-action="privacy-request"]').click()
    customer_page.locator('#privacy-request-form [name=password]').fill('LuciaSegura2026!')
    mutate_with_ui(customer_page, '/api/privacy/request', lambda: customer_page.locator('#privacy-request-form button[type=submit]').click())
    expect(customer_page.locator('[data-action="privacy-cancel"]')).to_be_visible()
    customer_page.set_viewport_size({'width': 320, 'height': 800})
    check_mobile_layout(customer_page, 'privacidad-cliente-320')
    customer_page.locator('[data-action="privacy-cancel"]').click()
    customer_page.locator('#privacy-cancel-form [name=password]').fill('LuciaSegura2026!')
    mutate_with_ui(customer_page, '/api/privacy/cancel', lambda: customer_page.locator('#privacy-cancel-form button[type=submit]').click())
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



def check_mobile_layout(page, label, preview=False):
    import json
    page.wait_for_timeout(200)
    page.screenshot(path=str(ARTIFACTS / (label + ".png")), full_page=True)
    if preview:
        print("UI_MOBILE_PREVIEW:" + base64.b64encode(page.screenshot(type="jpeg", quality=40)).decode(), flush=True)
    overflow = page.evaluate("document.documentElement.scrollWidth > innerWidth + 2")
    if overflow:
        details = page.evaluate("""() => [...document.querySelectorAll('body *')]
            .map(el => ({tag:el.tagName, id:el.id, class:el.className?.baseVal ?? el.className,
                         right:Math.round(el.getBoundingClientRect().right),
                         width:Math.round(el.getBoundingClientRect().width),
                         scroll:el.scrollWidth, display:getComputedStyle(el).display}))
            .filter(el => el.right > innerWidth + 2 && el.width > 0)
            .slice(0, 60)""")
        print("MOBILE_OVERFLOW:" + label + ":" + json.dumps(details, ensure_ascii=False), flush=True)
    assert not overflow, "Desborde horizontal en " + label


def mobile_page(page, section):
    menu = page.locator('[data-action="toggle-menu"]')
    if menu.is_visible():
        menu.click()
    page.locator('[data-page="' + section + '"]').first.click()


def account_flows(page, context, url):
    import hashlib
    import hmac
    import struct

    page.locator('[data-page="settings"]').first.click()
    page.locator('[data-action="mfa-setup"]').click()
    page.locator('#mfa-setup-form [name=password]').fill('PruebaSegura2026!')
    mutate_with_ui(page, '/api/security/mfa/setup', lambda: page.locator('#mfa-setup-form button[type=submit]').click())
    expect(page.locator('.authenticator-setup img')).to_be_visible()
    secret = page.locator('.manual-secret').inner_text()
    digest = hmac.new(base64.b32decode(secret), struct.pack('>Q', int(time.time()) // 30), hashlib.sha1).digest()
    offset = digest[-1] & 15
    otp = str((struct.unpack('>I', digest[offset:offset + 4])[0] & 0x7fffffff) % 1000000).zfill(6)
    page.locator('#mfa-confirm-form [name=codigo]').fill(otp)
    result = mutate_with_ui(page, '/api/security/mfa/confirm', lambda: page.locator('#mfa-confirm-form button[type=submit]').click())
    codes = result['recovery_codes']
    expect(page.locator('.recovery-codes code')).to_have_count(8)
    with page.expect_download() as pending:
        page.locator('[data-action="mfa-download"]').click()
    downloaded = Path(pending.value.path()).read_text(encoding='utf8')
    assert all(code in downloaded for code in codes)
    page.locator('#modal .dialog-close').click()
    expect(page.locator('.recovery-codes code')).to_have_count(0)
    page.screenshot(path=str(ARTIFACTS / '10-configuracion.png'), full_page=True)
    page.locator('[data-action="logout"]').click()
    page.locator('#login-user').fill('tienda')
    page.locator('#login-password').fill('PruebaSegura2026!')
    expect(page.locator("#challenge-question")).to_contain_text("+")
    page.locator('#login-answer').fill(str(sum(int(n) for n in re.findall(r'\d+', page.locator('#challenge-question').inner_text()))))
    page.locator('#login-form button[type=submit]').click()
    page.locator('#mfa-login-form').wait_for()
    page.screenshot(path=str(ARTIFACTS / '11-segundo-factor.png'), full_page=True)
    assert context.request.get(url + '/api/data').status == 401
    page.set_viewport_size({'width': 320, 'height': 800})
    check_mobile_layout(page, 'segundo-factor-320')
    page.locator('[data-action="mfa-use-recovery"]').click()
    page.locator('#mfa-code').fill(codes[0])
    mutate_with_ui(page, '/api/login/mfa', lambda: page.locator('#mfa-login-form button[type=submit]').click())
    page.locator('#page-title').wait_for()
    page.set_viewport_size({'width': 1440, 'height': 1000})
    page.locator('[data-page="settings"]').first.click()
    expect(page.locator('#security-title')).to_be_visible()
    page.locator('[data-action="mfa-disable"]').click()
    page.locator('#mfa-disable-form [name=password]').fill('PruebaSegura2026!')
    page.locator('#mfa-disable-form [name=codigo]').fill(codes[1])
    mutate_with_ui(page, '/api/security/mfa/disable', lambda: page.locator('#mfa-disable-form button[type=submit]').click())
    expect(page.locator('[data-action="mfa-setup"]')).to_be_visible()
    policy = page.locator('#privacy-policy-form')
    policy.locator('[name=retention_days]').fill('90')
    policy.locator('[name=contact]').fill('privacidad@example.test')
    policy.locator('[name=password]').fill('PruebaSegura2026!')
    mutate_with_ui(page, '/api/privacy/policy', lambda: policy.locator('button[type=submit]').click())
    expect(page.locator('#privacy-policy-form [name=retention_days]')).to_have_value('90')
    page.locator('[data-page="products"]').first.click()
    page.locator('[data-action="edit-product"][data-id="1"]').click()
    uploaded = mutate_with_ui(page, '/api/product-images', lambda: page.locator('#product-image-file').set_input_files(ROOT / 'src/main/resources/static/catalogo/leche.jpg'))
    expect(page.locator('#product-form [name=image]')).to_have_value(uploaded['image'])
    page.screenshot(path=str(ARTIFACTS / '12-imagen-producto.png'), full_page=True)
    mutate_with_ui(page, '/api/products/1', lambda: page.locator('#product-form button[type=submit]').click(), method='PATCH')
    assert any(p['image'] == uploaded['image'] for p in api_data(context, url)['products'])


def main():
    with tempfile.TemporaryDirectory() as temp:
        # El sistema elige un puerto disponible para no chocar con otra prueba.
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        url = "http://127.0.0.1:" + str(port)
        env = dict(os.environ, DB_PATH=str(Path(temp) / "browser.db"),
                   HOST="127.0.0.1", PORT=str(port),
                   ADMIN_PASSWORD="AdminSegura2026!", DEMO_PASSWORD="PruebaSegura2026!", DEMO="true")
        with open(ARTIFACTS / "server.log", "w", encoding="utf-8") as logfile:
            server = subprocess.Popen([os.environ.get("JAVA_EXE", "java"), "-jar", str(ROOT / "target/cuenta-clara.jar")], cwd=ROOT,
                                      env=env, stdout=logfile, stderr=subprocess.STDOUT)
            try:
                for _ in range(200):
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
                        expect(page.locator('[data-mascot]')).to_have_attribute('data-eyes', 'open')
                        for width in (390, 320):
                            page.set_viewport_size({'width': width, 'height': 844})
                            expect(page.locator('[data-mascot]')).to_be_visible()
                            check_mobile_layout(page, 'acceso-celular-' + str(width))
                        page.set_viewport_size({'width': 1440, 'height': 1000})
                        page.screenshot(path=str(ARTIFACTS / "01-acceso.png"), full_page=True)
                        page.locator("#login-form input[name=username]").fill("tienda")
                        page.locator("#login-form input[name=password]").fill("PruebaSegura2026!")
                        page.locator('[data-action="toggle-password"]').click()
                        expect(page.locator('#login-password')).to_have_attribute('type', 'text')
                        expect(page.locator('[data-mascot]')).to_have_attribute('data-eyes', 'covered')
                        page.screenshot(path=str(ARTIFACTS / 'acceso-ojos-tapados.png'), full_page=True)
                        page.emulate_media(reduced_motion='reduce')
                        page.locator('[data-action="toggle-password"]').click()
                        expect(page.locator('#login-password')).to_have_attribute('type', 'password')
                        expect(page.locator('[data-mascot]')).to_have_attribute('data-eyes', 'open')
                        page.emulate_media(reduced_motion='no-preference')
                        question = page.locator("#challenge-question").inner_text()
                        answer = sum(int(x) for x in re.findall(r"\d+", question))
                        page.locator("#login-form input[name=answer]").fill(str(answer))
                        page.locator("#login-form button[type=submit]").click()
                        page.locator("#dashboard").wait_for()
                        page.screenshot(path=str(ARTIFACTS / "02-resumen-escritorio.png"), full_page=True)
                        print("UI_PREVIEW:" + base64.b64encode(page.screenshot(type="jpeg", quality=35)).decode(), flush=True)
                        assert "Cuenta" in page.title(), page.title()
                        assert page.locator("#page-title").inner_text().strip()

                        dashboard_flows(page, context, url)

                        for section in ("customers", "products", "sales", "payments", "statements", "audit", "settings"):
                            page.locator('[data-page="' + section + '"]').first.click()
                            page.wait_for_timeout(150)
                            assert page.locator("#page-title").inner_text().strip(), section
                            assert page.evaluate("document.documentElement.scrollWidth <= innerWidth + 2"), section

                        business_flows(page, context, url)
                        assert page.locator(".toast").count() <= 2, "Se acumularon las notificaciones."
                        platform_and_customer(browser, url)
                        account_flows(page, context, url)
                        expect(page.locator(".toast")).to_have_count(0, timeout=10000)
                        page.locator('[data-page="dashboard"]').first.click()
                        page.set_viewport_size({"width": 390, "height": 844})
                        check_mobile_layout(page, "03-resumen-celular", preview=True)
                        expect(page.locator('.sidebar')).to_have_attribute('inert', '')
                        page.locator('[data-action="toggle-menu"]').click()
                        expect(page.locator('button[data-action="toggle-menu"]')).to_have_attribute('aria-expanded', 'true')
                        page.keyboard.press('Escape')
                        expect(page.locator('.sidebar')).to_have_attribute('inert', '')
                        for section in ("customers", "products", "sales", "payments", "statements", "settings"):
                            mobile_page(page, section)
                            check_mobile_layout(page, "celular-390-" + section)
                        page.set_viewport_size({"width": 320, "height": 760})
                        mobile_page(page, "dashboard")
                        check_mobile_layout(page, "celular-320-resumen")
                        mobile_page(page, "products")
                        check_mobile_layout(page, "celular-320-productos")
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
