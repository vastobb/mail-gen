#!/usr/bin/env python3
"""mail-gen core engine: temp mail lifecycle against mail.tm (public API, no key).

Proton does not expose a public temp-mail API; mail.tm is the working equivalent
and is what this engine targets. Every operation is a plain HTTP call so the
same logic ports to Android unchanged.
"""
import json
import random
import string
import sys
import urllib.error
import urllib.request

BASE = "https://api.mail.tm"
UA = "mail-gen/1.0 (+https://github.com/vastobb/mail-gen)"


def _req(method, path, token=None, body=None, timeout=30):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("User-Agent", UA)
    req.add_header("Accept", "application/json")
    if data is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read().decode("utf-8", "replace")
            return r.status, (json.loads(raw) if raw.strip() else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            parsed = json.loads(raw) if raw.strip() else {}
        except json.JSONDecodeError:
            parsed = {"raw": raw}
        return e.code, parsed
    except Exception as e:  # network / timeout
        return 0, {"error": str(e)}


def _items(d):
    """mail.tm answers hydra:Collection for some clients and a plain array for
    others (it varies with Accept). Normalise both."""
    if isinstance(d, list):
        return d
    if isinstance(d, dict):
        return d.get("hydra:member") or d.get("member") or []
    return []


def active_domain():
    st, d = _req("GET", "/domains?page=1")
    if st != 200:
        raise RuntimeError(f"domains failed: {st} {d}")
    for m in _items(d):
        if m.get("isActive") and not m.get("isPrivate"):
            return m["domain"]
    raise RuntimeError("no active public domain")


def random_local(n=10):
    return "".join(random.choice(string.ascii_lowercase + string.digits) for _ in range(n))


def create_account(domain=None, password=None):
    domain = domain or active_domain()
    address = f"{random_local()}@{domain}"
    password = password or ("".join(random.choice(string.ascii_letters + string.digits) for _ in range(16)) + "!aA1")
    st, body = _req("POST", "/accounts", body={"address": address, "password": password})
    if st not in (200, 201):
        raise RuntimeError(f"create failed: {st} {body}")
    st2, tok = _req("POST", "/token", body={"address": address, "password": password})
    if st2 != 200 or "token" not in tok:
        raise RuntimeError(f"token failed: {st2} {tok}")
    return {
        "id": body.get("id"),
        "address": address,
        "password": password,
        "token": tok["token"],
    }


def list_messages(token):
    st, d = _req("GET", "/messages?page=1", token=token)
    if st != 200:
        raise RuntimeError(f"messages failed: {st} {d}")
    return _items(d)


def read_message(token, msg_id):
    st, d = _req("GET", f"/messages/{msg_id}", token=token)
    if st != 200:
        raise RuntimeError(f"read failed: {st} {d}")
    return d


def delete_account(acc):
    st, d = _req("DELETE", f"/accounts/{acc['id']}", token=acc["token"])
    return st in (200, 204), st


def alive(acc):
    st, _ = _req("GET", "/me", token=acc["token"])
    return st == 200


# ---------- self test ----------
if __name__ == "__main__":
    ok = True
    print("1. active domain")
    dom = active_domain()
    print("   domain:", dom)

    print("2. create account")
    acc = create_account(dom)
    print("   address:", acc["address"])
    print("   id:", acc["id"])
    print("   token_len:", len(acc["token"]))

    print("3. account alive (/me)")
    a = alive(acc)
    print("   alive:", a)
    ok &= a

    print("4. inbox list")
    msgs = list_messages(acc["token"])
    print("   messages:", len(msgs))
    ok &= isinstance(msgs, list)

    print("5. delete account")
    gone, st = delete_account(acc)
    print("   delete status:", st, "gone:", gone)
    ok &= gone

    print("6. verify deleted (token should 401)")
    a2 = alive(acc)
    print("   alive after delete:", a2)
    ok &= (a2 is False)

    print()
    print("RESULT:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)
