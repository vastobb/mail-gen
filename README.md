# mail-gen

Four parallel temporary mailboxes with inbox support, built for Android.

Generate a mailbox, read what lands in it, delete it. Each of the four slots
holds one live mailbox at a time; regenerating a slot destroys that slot's
previous mailbox first, so a one-mailbox-per-account ceiling never blocks you.

## Verified behaviour

The engine in `scripts/` is the reference implementation and was run end to end
against the live API before any Android code was written:

```
$ python3 scripts/mailgen_core.py
1. active domain      -> maxxspace.com
2. create account     -> gbokk8r9gd@maxxspace.com
3. account alive      -> True
4. inbox list         -> 0 messages
5. delete account     -> 204, gone
6. verify deleted     -> alive False
RESULT: PASS
```

```
$ python3 scripts/mailgen_slots.py
1. generate into all 4 slots        -> 4 addresses
2. all 4 alive simultaneously       -> True x4
3. regenerate slot 0 -> old dies, new lives -> old alive False, new alive True
4. delete everything                -> [True, True, True, True]
RESULT: PASS (inbox read verified separately)
```

Inbound delivery was verified separately: a real external message was sent to a
generated mailbox and read back through the same API the app uses.

## What it does

- **4 independent slots** — generate up to four mailboxes at once
- **Auto-rotate** — regenerating a slot deletes its old mailbox, then mints a new one
- **Inbox** — list messages, tap to read the body
- **Delete** — per slot, or all four at once
- **Persistent** — mailboxes survive app restarts

## Build

No local Android toolchain is used. Push to `main` and GitHub Actions compiles
the APK; tag `v*` to publish a release with the APK attached.

```
git tag v1.0.0 && git push origin v1.0.0
```

## Provider

Uses [mail.tm](https://mail.tm), a public temp-mail API with no key required.
Proton Mail has no public API for creating temporary addresses, so mail.tm is
the working equivalent. The client is isolated in `MailApi.kt` — swapping
providers means changing one file.

## Theme

Palette and typography taken from the supplied `tt.html` theme file: terminal
aesthetic, `#0D0D0D` app background, `#111111` sidebar, `#171717` panels,
`#292929` borders, `#F5F5F5` primary text, `#929292` secondary, JetBrains Mono
for UI chrome and Inter for body. A light scheme is included and follows the
system setting.

Design guidance from
[ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill):
OLED-dark base, monospace accents, restrained glow, 4.5:1 minimum contrast.
