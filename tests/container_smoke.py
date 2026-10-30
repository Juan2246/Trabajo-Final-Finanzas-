"""Arranque, acceso y conservación de la sesión tras reiniciar el contenedor."""
import http.cookiejar
import base64
import hashlib
import hmac
import struct
import json
import re
import subprocess
import time
import urllib.request

url = "http://127.0.0.1:8000"
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
csrf = ""


def codigo_autenticador(secret, adelanto=0):
    digest = hmac.new(base64.b32decode(secret), struct.pack('>Q', (int(time.time()) + adelanto) // 30), hashlib.sha1).digest()
    offset = digest[-1] & 15
    return str((struct.unpack('>I', digest[offset:offset + 4])[0] & 0x7fffffff) % 1000000).zfill(6)


def request(path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url + path, data, headers={"Content-Type": "application/json", "X-CSRF-Token": csrf})
    with client.open(req, timeout=5) as response:
        return json.load(response)


def ready():
    for _ in range(90):
        try:
            if request("/api/health")["ok"]:
                return
        except Exception:
            time.sleep(1)
    raise AssertionError("El servidor no respondió a tiempo.")


ready()
challenge = request("/api/challenge")
login = request("/api/login", {
    "username": "admin", "password": "PruebaSegura2026",
    "challenge_id": challenge["id"],
    "answer": str(sum(map(int, re.findall(r"\d+", challenge["question"])))),
})
assert login["user"]["role"] == "platform"
csrf = login["csrf"]
setup = request('/api/security/mfa/setup', {'password': 'PruebaSegura2026'})
otp = codigo_autenticador(setup['secret'])
codes = request('/api/security/mfa/confirm', {'codigo': otp})['recovery_codes']
subprocess.run(["docker", "restart", "cuenta-clara-ci"], check=True)
ready()
assert request("/api/me")["user"]["id"] == login["user"]["id"]
assert request('/api/security/mfa')['enabled']
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
csrf = ""
challenge = request('/api/challenge')
step = request('/api/login', {'username': 'admin', 'password': 'PruebaSegura2026',
    'challenge_id': challenge['id'], 'answer': str(sum(map(int, re.findall(r'\d+', challenge['question']))))})
assert step['mfa_required']
verified = request('/api/login/mfa', {'token': step['mfa_token'], 'codigo': codigo_autenticador(setup['secret'], 30)})
assert verified['user']['id'] == login['user']['id']
csrf = verified['csrf']
request('/api/logout', {})
csrf = ""
challenge = request('/api/challenge')
step = request('/api/login', {'username': 'admin', 'password': 'PruebaSegura2026',
    'challenge_id': challenge['id'], 'answer': str(sum(map(int, re.findall(r'\d+', challenge['question']))))})
verified = request('/api/login/mfa', {'token': step['mfa_token'], 'codigo': codes[0]})
assert verified['user']['id'] == login['user']['id']
assert request('/api/security/mfa')['recovery_remaining'] == 7
print("Contenedor correcto: sesión, segundo factor y recuperación conservados después del reinicio.")
