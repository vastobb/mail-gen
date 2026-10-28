#!/usr/bin/env python3
"""mail-gen slot manager: 4 independent slots, each holding at most one live
temp mailbox. Generating into a slot destroys that slot's previous mailbox
first, which is what makes the free-tier single-mailbox limit a non-issue.
"""
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mailgen_core as core

SLOTS = 4
STATE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mailgen_state.json")


def load():
    if os.path.exists(STATE):
        try:
            with open(STATE) as f:
                return json.load(f)
        except Exception:
            pass
    return {"slots": [None] * SLOTS}


def save(state):
    tmp = STATE + ".tmp"
    with open(tmp, "w") as f:
        json.dump(state, f, indent=2)
    os.replace(tmp, STATE)


def generate(state, idx, domain=None):
    """Destroy whatever is in slot idx, then mint a fresh mailbox there."""
    if not 0 <= idx < SLOTS:
        raise IndexError("slot out of range")
    old = state["slots"][idx]
    if old:
        try:
            core.delete_account(old)
        except Exception:
            pass  # already gone server-side; nothing to reclaim
    acc = core.create_account(domain)
    acc["created_at"] = int(time.time())
    state["slots"][idx] = acc
    save(state)
    return acc


def clear(state, idx):
    acc = state["slots"][idx]
    if not acc:
        return False
    ok, _ = core.delete_account(acc)
    state["slots"][idx] = None
    save(state)
    return ok


def clear_all(state):
    return [clear(state, i) for i in range(SLOTS)]


def inbox(state, idx):
    acc = state["slots"][idx]
    if not acc:
        return []
    return core.list_messages(acc["token"])


# ---------- self test ----------
if __name__ == "__main__":
    ok = True
    st = {"slots": [None] * SLOTS}
    save(st)

    print("1. generate into all 4 slots")
    for i in range(SLOTS):
        a = generate(st, i)
        print(f"   slot{i}: {a['address']}")
    ok &= all(st["slots"])

    print("2. all 4 alive simultaneously")
    for i in range(SLOTS):
        a = core.alive(st["slots"][i])
        print(f"   slot{i} alive: {a}")
        ok &= a

    print("3. regenerate slot 0 -> old mailbox must die, new one lives")
    old = dict(st["slots"][0])
    new = generate(st, 0)
    print("   old:", old["address"], "-> new:", new["address"])
    ok &= (old["address"] != new["address"])
    old_alive = core.alive(old)
    new_alive = core.alive(new)
    print("   old alive:", old_alive, "| new alive:", new_alive)
    ok &= (old_alive is False and new_alive is True)

    print("4. send a real email into slot 1, then read it")
    sender = core.create_account()
    print("   sender:", sender["address"])
    target = st["slots"][1]["address"]
    sst, sbody = core._req(
        "POST", "/messages", token=sender["token"],
        body={
            "from": {"address": sender["address"], "name": "MailGen Test"},
            "to": [{"address": target, "name": ""}],
            "subject": "mail-gen delivery test",
            "text": "If you can read this, inbox support works.",
        },
    )
    print("   send status:", sst, "" if sst in (200, 201) else sbody)
    got = []
    for _ in range(12):
        time.sleep(3)
        got = inbox(st, 1)
        if got:
            break
    print("   inbox count:", len(got))
    ok &= len(got) > 0
    if got:
        full = core.read_message(st["slots"][1]["token"], got[0]["id"])
        print("   subject:", full.get("subject"))
        print("   from:", (full.get("from") or {}).get("address"))
        print("   text:", (full.get("text") or "")[:80].replace("\n", " "))
        ok &= full.get("subject") == "mail-gen delivery test"

    print("5. delete everything")
    res = clear_all(st)
    print("   results:", res)
    ok &= all(res)
    print("6. state empty")
    empty = all(s is None for s in st["slots"])
    print("   all slots None:", empty)
    ok &= empty

    core.delete_account(sender)
    print()
    print("RESULT:", "PASS" if ok else "FAIL")
    sys.exit(0 if ok else 1)
