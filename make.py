#!/usr/bin/env python3
"""PetForge: sprite sheet -> pets/<id>/ (animations.xml + icon + preview).

usage: make.py --sheet sheet.png --name "Display Name" [--id id]

The .exe and .apk are built by GitHub Actions when pets/<id>/ is pushed
(see .github/workflows/build.yml). Needs python3 + numpy, scipy, Pillow, lxml(optional).
"""
import argparse
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet", required=True)
    ap.add_argument("--name", required=True)
    ap.add_argument("--id")
    a = ap.parse_args()
    pid = a.id or re.sub(r"[^a-z0-9]+", "_", a.name.lower()).strip("_") or "pet"
    out = os.path.join(ROOT, "pets", pid)
    r = subprocess.run([sys.executable, os.path.join(ROOT, "petforge", "sheet2pet.py"),
                        a.sheet, out, "--name", a.name])
    if r.returncode:
        sys.exit(r.returncode)
    for junk in ("sprite.png",):
        p = os.path.join(out, junk)
        if os.path.exists(p):
            os.remove(p)
    print(f"\npet id: {pid}\nCheck {out}/preview.gif and sprite_preview.png, then commit + push pets/{pid}/ "
          "to build the exe and apk.")


if __name__ == "__main__":
    main()
