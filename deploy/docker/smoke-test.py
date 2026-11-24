"""Validate a fresh HTTPS deployment using synthetic users (CI or a dedicated test server)."""
import http.cookiejar
import json
import os
import secrets
import ssl
import urllib.error
import urllib.request

base = os.environ.get('SMOKE_BASE_URL', 'https://localhost:8443').rstrip('/')
context = ssl.create_default_context(cafile=os.environ.get('SMOKE_CA_FILE'))
checks = 0

class Client:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPSHandler(context=context), urllib.request.HTTPCookieProcessor(self.jar))

    def send(self, path, method='GET', body=None, csrf=True):
        headers = {'Content-Type': 'application/json'}
        token = next((c.value for c in self.jar if c.name == 'XSRF-TOKEN'), None)
        if token and csrf:
            headers['X-XSRF-TOKEN'] = token
        req = urllib.request.Request(base + path, method=method, headers=headers,
                                     data=json.dumps(body).encode() if body is not None else None)
        try:
            response = self.opener.open(req, timeout=30)
        except urllib.error.HTTPError as error:
            response = error
        data = response.read()
        return response.status, json.loads(data) if 'json' in response.headers.get('Content-Type', '') else data

def check(response, status, label):
    global checks
    assert response[0] == status, (label, response)
    checks += 1
    print('PASS', label)
    return response[1]

a, b = Client(), Client()
check(a.send('/login'), 200, 'web login served over HTTPS')
check(a.send('/register'), 200, 'registration deep link served')
assert check(a.send('/api/auth/registration'), 200, 'registration config')['data']['enabled'] is True
for client in (a, b):
    check(client.send('/api/auth/csrf'), 200, 'CSRF cookie')
    check(client.send('/api/goals'), 401, 'anonymous data access denied')
suffix = secrets.token_hex(5)
password = secrets.token_urlsafe(16)
for client, username in ((a, 'smoke_a_'+suffix), (b, 'smoke_b_'+suffix)):
    body = {'username': username, 'password': password, 'nickname': 'CI synthetic account'}
    if client is a:
        check(client.send('/api/auth/register', 'POST', body, csrf=False), 403, 'registration requires CSRF')
    check(client.send('/api/auth/register', 'POST', body), 200, 'self-registration')
    check(client.send('/api/auth/register', 'POST', body), 400, 'duplicate username rejected')
    check(client.send('/api/auth/login', 'POST', body), 200, 'new account login')
    assert any(c.name == 'ACCESS_TOKEN' and c.secure for c in client.jar), 'Login cookie must be Secure'
goal = check(a.send('/api/goals', 'POST', {'name': 'Private synthetic goal'}), 200, 'create private goal')['data']
assert check(b.send('/api/goals'), 200, 'other account list')['data'] == []
check(b.send('/api/goals/'+str(goal['id']), 'PUT', {'name': 'forbidden'}), 404, 'other account cannot edit goal')
check(a.send('/api/auth/logout', 'POST'), 200, 'logout')
check(a.send('/api/goals'), 401, 'logged-out session denied')
print(f'HTTPS smoke checks passed: {checks}')
