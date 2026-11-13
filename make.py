#!/usr/bin/env python3
"""One command: character image -> Windows .exe + Android .apk.

  python3 make.py --sheet my_sheet.png --name "Mochi"            # ChatGPT 4x4 sheet
  python3 make.py --xml Pets/mareep/animations.xml --name Mareep  # existing desktopPet pet

Output: out/<id>/<id>.exe, out/<id>/<id>.apk, preview.gif, (and the pet data).
Steps: build pet data -> run engine tests (Go + Java) -> cross-compile exe -> build + sign apk.
"""
import argparse
import os
import re
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(ROOT, "tools"))
import fetch_bundletool  # noqa: E402


def run(cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    out = "\n".join(l for l in (r.stdout + r.stderr).splitlines() if "Picked up JAVA_TOOL_OPTIONS" not in l)
    if r.returncode != 0:
        sys.exit(f"FAILED: {' '.join(cmd)}\n{out}")
    return out


def main():
    ap = argparse.ArgumentParser()
    src = ap.add_mutually_exclusive_group(required=True)
    src.add_argument("--sheet", help="4x4 sprite sheet PNG (see prompts/spritesheet_prompt.md)")
    src.add_argument("--xml", help="existing desktopPet animations.xml")
    ap.add_argument("--name", required=True, help="display name, e.g. Mochi")
    ap.add_argument("--id", help="ascii id (default: derived from name)")
    ap.add_argument("--cell", type=int, default=96, help="sprite cell size in px (sheet mode)")
    ap.add_argument("--version", default="1.0")
    ap.add_argument("--version-code", type=int, default=1)
    ap.add_argument("--keystore", default=os.path.join(ROOT, "keystore", "petforge.p12"))
    a = ap.parse_args()

    pid = a.id or re.sub(r"[^a-z0-9]+", "", a.name.lower()) or "pet"
    if not re.fullmatch(r"[a-z][a-z0-9]*", pid):
        sys.exit(f"id {pid!r} must start with a letter and be lowercase ascii")
    work = os.path.join(ROOT, "build", pid)
    out = os.path.join(ROOT, "out", pid)
    shutil.rmtree(work, ignore_errors=True)
    os.makedirs(work)
    os.makedirs(out, exist_ok=True)

    print("[1/5] pet data")
    if a.sheet:
        print(run([sys.executable, os.path.join(ROOT, "petforge", "sheet2pet.py"), a.sheet, work,
                   "--name", a.name, "--cell", str(a.cell)]).strip())
    else:
        print(run([sys.executable, os.path.join(ROOT, "petforge", "xml2pet.py"), a.xml, work]).strip())

    print("[2/5] engine tests (Go + Java)")
    env = dict(os.environ, PET_TXT=os.path.join(work, "pet.txt"))
    res = run(["go", "test", "-count=1", "-v", "./engine"], cwd=ROOT, env=env)
    if "SKIP" in res:
        sys.exit("engine tests were skipped:\n" + res)
    jt = os.path.join(ROOT, "build", "jtest")
    os.makedirs(jt, exist_ok=True)
    jars = fetch_bundletool.ensure()
    run(["javac", "--release", "8", "-nowarn", "-d", jt,
         *[os.path.join(ROOT, "android", "src", "com", "petforge", "core", f)
           for f in os.listdir(os.path.join(ROOT, "android", "src", "com", "petforge", "core"))],
         os.path.join(ROOT, "android", "test", "EngineCheck.java")])
    print(run(["java", "-cp", jt, "EngineCheck", os.path.join(work, "pet.txt")]).strip())

    print("[3/5] Windows exe")
    assets = os.path.join(ROOT, "windows", "assets")
    shutil.rmtree(assets, ignore_errors=True)
    os.makedirs(assets)
    for f in ("pet.txt", "sprite.png"):
        shutil.copy(os.path.join(work, f), assets)
    exe = os.path.join(out, f"{pid}.exe")
    run(["go", "build", "-trimpath", "-ldflags", "-H=windowsgui -s -w", "-o", exe, "./windows"], cwd=ROOT,
        env=dict(os.environ, GOOS="windows", GOARCH="amd64", CGO_ENABLED="0"))
    print("  ", exe, os.path.getsize(exe), "bytes")

    print("[4/5] Android apk")
    apk_assets = os.path.join(work, "apk_assets")
    os.makedirs(apk_assets)
    for f in ("pet.txt", "sprite.png"):
        shutil.copy(os.path.join(work, f), apk_assets)
    apk = os.path.join(out, f"{pid}.apk")
    print(run([sys.executable, os.path.join(ROOT, "tools", "build_apk.py"),
               "--package", f"com.petforge.{pid}", "--label", a.name,
               "--version-name", a.version, "--version-code", str(a.version_code),
               "--src", os.path.join(ROOT, "android", "src"), "--assets", apk_assets,
               "--activity", "com.petforge.app.MainActivity", "--service", "com.petforge.app.PetService",
               "--permission", "android.permission.SYSTEM_ALERT_WINDOW",
               "--permission", "android.permission.FOREGROUND_SERVICE",
               "--keystore", a.keystore, "--build-dir", os.path.join(work, "apkbuild"),
               "--out", apk]).strip())

    print("[5/5] collect")
    for f in ("preview.gif", "sprite_preview.png", "icon.png", "pet.txt", "sprite.png"):
        if os.path.exists(os.path.join(work, f)):
            shutil.copy(os.path.join(work, f), out)
    print(f"\nDONE -> {out}\n  {pid}.exe  (Windows 64-bit)\n  {pid}.apk  (Android 8.0+)")


if __name__ == "__main__":
    main()
