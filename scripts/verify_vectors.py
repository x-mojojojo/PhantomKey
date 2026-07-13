#!/usr/bin/env python3
"""
Standalone verification of PhantomKey / Master Password algorithm vectors.
Does not require Android SDK — useful as a quick sanity check.

Expected:
  full name "Robert", master password "password123", site "twitter.com"
    Maximum password → S$YknOb*PVY(BfeO4&1^
    Login name       → meqcoloba
"""

from __future__ import annotations

import hashlib
import hmac
import struct
import sys

NS = b"com.lyndir.masterpassword"
LOGIN_NS = b"com.lyndir.masterpassword.login"

TEMPLATES = {
    "maximum": ["anoxxxxxxxxxxxxxxxxx", "axxxxxxxxxxxxxxxxxno"],
    "long": [
        "CvcvnoCvcvCvcv", "CvcvCvcvnoCvcv", "CvcvCvcvCvcvno",
        "CvccnoCvcvCvcv", "CvccCvcvnoCvcv", "CvccCvcvCvcvno",
        "CvcvnoCvccCvcv", "CvcvCvccnoCvcv", "CvcvCvccCvcvno",
        "CvcvnoCvcvCvcc", "CvcvCvcvnoCvcc", "CvcvCvcvCvccno",
        "CvccnoCvccCvcv", "CvccCvccnoCvcv", "CvccCvccCvcvno",
        "CvcvnoCvccCvcc", "CvcvCvccnoCvcc", "CvcvCvccCvccno",
        "CvccnoCvcvCvcc", "CvccCvcvnoCvcc", "CvccCvcvCvccno",
    ],
    "name": ["cvccvcvcv"],
}

PASSCHARS = {
    "V": "AEIOU",
    "C": "BCDFGHJKLMNPQRSTVWXYZ",
    "v": "aeiou",
    "c": "bcdfghjklmnpqrstvwxyz",
    "A": "AEIOUBCDFGHJKLMNPQRSTVWXYZ",
    "a": "AEIOUaeiouBCDFGHJKLMNPQRSTVWXYZbcdfghjklmnpqrstvwxyz",
    "n": "0123456789",
    "o": "@&%?,=[]_:-+*$#!'^~;()/.",
    "x": "AEIOUaeiouBCDFGHJKLMNPQRSTVWXYZbcdfghjklmnpqrstvwxyz0123456789!@#$%^&*()",
    " ": " ",
}


def master_key(name: str, password: str) -> bytes:
    name_b = name.encode("utf-8")
    salt = NS + struct.pack(">I", len(name_b)) + name_b
    return hashlib.scrypt(
        password.encode("utf-8"),
        salt=salt,
        n=32768,
        r=8,
        p=2,
        dklen=64,
        maxmem=128 * 1024 * 1024,
    )


def site_seed(key: bytes, site: str, counter: int, ns: bytes = NS) -> bytes:
    site_b = site.encode("utf-8")
    data = ns + struct.pack(">I", len(site_b)) + site_b + struct.pack(">I", counter)
    return hmac.new(key, data, hashlib.sha256).digest()


def generate(seed: bytes, template_class: str) -> str:
    templates = TEMPLATES[template_class]
    template = templates[seed[0] % len(templates)]
    out = []
    for i, ch in enumerate(template):
        chars = PASSCHARS[ch]
        out.append(chars[seed[i + 1] % len(chars)])
    return "".join(out)


def main() -> int:
    key = master_key("Robert", "password123")
    maximum = generate(site_seed(key, "twitter.com", 1), "maximum")
    login = generate(site_seed(key, "twitter.com", 1, LOGIN_NS), "name")
    self_test = generate(
        site_seed(master_key("user", "password"), "example.com", 1),
        "long",
    )

    ok = True
    for label, got, expected in [
        ("Maximum password", maximum, "S$YknOb*PVY(BfeO4&1^"),
        ("Login name", login, "meqcoloba"),
        ("Self-test long", self_test, "ZedaFaxcZaso9*"),
    ]:
        status = "OK" if got == expected else "FAIL"
        print(f"[{status}] {label}: {got}")
        if got != expected:
            print(f"       expected: {expected}")
            ok = False

    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
