"""Arranque, acceso y conservación de la sesión tras reiniciar el contenedor."""
import http.cookiejar
import json
import re
import subprocess
import time
import urllib.request

url = "http://127.0.0.1:8000"
client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def request(path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(url + path, data, headers={"Content-Type": "application/json"})
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
subprocess.run(["docker", "restart", "cuenta-clara-ci"], check=True)
ready()
assert request("/api/me")["user"]["id"] == login["user"]["id"]
print("Contenedor correcto: acceso y sesión conservados después del reinicio.")
