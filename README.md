# PhantomKey

**A stateless password manager for Android.**

Nothing is stored. The same inputs always produce the same outputs.

PhantomKey is an **independent** free-software implementation of the
[Master Password algorithm (v3)](https://en.wikipedia.org/wiki/Master_Password_(algorithm))
originally designed by Maarten Billemont. Your site passwords are deterministically
derived from:

1. Your **full name** (public salt)
2. Your **master password** (secret)
3. The **site name** (e.g. `twitter.com`)
4. A **counter** (for password rotation)
5. A **password type** template (Maximum, Long, Medium, Basic, Short, PIN, Name, Phrase)

Because derivation is pure and deterministic, there is no vault to sync, back up, or breach.

> **Not affiliated.** PhantomKey is not the official Master Password or Spectre app,
> and is not endorsed by Maarten Billemont, Lyndir, or the Spectre project.

## Features

- **Instant generation** — type a site name; password & login name appear with no “Generate” button
- **Password types** — Maximum, Long, Medium, Basic, Short, PIN, Name, Phrase
- **Counter** — rotate a site password without changing your master key
- **Deterministic login names** — unique username per site via the identification namespace
- **Clipboard auto-clear** — copied secrets expire after a configurable delay
- **Session timeout** — auto-lock after inactivity
- **Biometric unlock** — optional fingerprint / face unlock (Android Keystore)
- **Site history** — local metadata only (site, counter, type) — never passwords; saved only after you copy a credential
- **Import / export** — JSON backup of site metadata

## Verification vector

| Input | Value |
| --- | --- |
| Full name | `Robert` |
| Master password | `password123` |
| Site | `twitter.com` |
| Type | Maximum |
| Counter | 1 |

| Output | Value |
| --- | --- |
| Maximum password | `S$YknOb*PVY(BfeO4&1^` |
| Login name | `meqcoloba` |

Unit tests assert these vectors on every CI run.

## Build

### Requirements

- JDK 17+
- Android SDK 34
- Gradle 8.7 (wrapper included)

```bash
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk

./gradlew testDebugUnitTest
python3 scripts/verify_vectors.py   # no Android SDK required
```

### GitHub Actions

Pushing to `main` / `master` runs [.github/workflows/android.yml](.github/workflows/android.yml), which:

1. Verifies algorithm vectors (Python)
2. Runs unit tests
3. Assembles the debug APK
4. Uploads the APK as a workflow artifact

## Security notes

- Master password is **never** written to disk in plaintext.
- A PBKDF2 verifier is stored only to reject wrong unlock attempts.
- The scrypt-derived master key lives **only in RAM** and is wiped on lock / timeout.
- Optional biometric unlock encrypts the master password under an Android Keystore AES-GCM key that requires user authentication.
- Site history stores names, counters, and types — not passwords — and only after you copy a result.

## Algorithm (summary)

```
salt      = "com.lyndir.masterpassword" || uint32be(len(name)) || name
masterKey = scrypt(masterPassword, salt, N=32768, r=8, p=2, dkLen=64)

seed      = HMAC-SHA-256(masterKey,
              ns || uint32be(len(site)) || site || int32be(counter))

password  = applyTemplate(seed, type)
```

Namespaces:

- Authentication (passwords): `com.lyndir.masterpassword`
- Identification (logins): `com.lyndir.masterpassword.login`

## Attribution

PhantomKey reimplements the **Master Password algorithm (v3)** from the public
specification published by **Maarten Billemont** (Lyndir). The original project
and its successor **Spectre** are Free Software; see:

- Algorithm overview: <https://en.wikipedia.org/wiki/Master_Password_(algorithm)>
- Historical project: <https://gitlab.com/MasterPassword/MasterPassword>
- Successor project: Spectre (same algorithm family)

This repository contains an original Android client and a clean-room style
implementation of the documented algorithm in Kotlin. It does **not** copy
proprietary assets, trademarks, or official app branding from Master Password
or Spectre.

If you redistribute PhantomKey, please keep this attribution section (or an
equivalent notice) so users understand the cryptographic lineage.

## License

Copyright (C) 2026 PhantomKey contributors

This program is free software: you can redistribute it and/or modify it under
the terms of the **GNU General Public License as published by the Free Software
Foundation, either version 3 of the License, or (at your option) any later
version**.

This program is distributed in the hope that it will be useful, but **WITHOUT
ANY WARRANTY**; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with
this program in the file [`LICENSE`](LICENSE). If not, see
<https://www.gnu.org/licenses/>.

**SPDX-License-Identifier:** `GPL-3.0-or-later`

Third-party libraries used at build/runtime (AndroidX, Kotlin, Bouncy Castle,
etc.) retain their own licenses; see the dependency declarations in
`app/build.gradle.kts`.
