#!/usr/bin/env python3
"""Build and sign an APK without the Android SDK.

Pipeline: stub android.jar -> javac -> d8 -> binary manifest (axml.py) -> zip -> apksig.
Needs only: JDK 17+, python3, and tools/bundletool.jar (provides D8 and apksig).

Example:
  python3 tools/build_apk.py --package com.petforge.test --label "PetForge Test" \
      --src android/src --activity com.petforge.test.MainActivity --out build/test.apk
"""
import argparse
import os
import shutil
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
from axml import build_manifest_bytes, simple_manifest  # noqa: E402

BUNDLETOOL = os.path.join(HERE, "bundletool.jar")


def run(cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    # JAVA_TOOL_OPTIONS noise goes to stderr; filter it
    err = "\n".join(l for l in r.stderr.splitlines() if "JAVA_TOOL_OPTIONS" not in l)
    if r.returncode != 0:
        raise SystemExit(f"command failed: {' '.join(cmd)}\n{r.stdout}\n{err}")
    return r.stdout


def java_files(d):
    out = []
    for dp, _, fs in os.walk(d):
        out += [os.path.join(dp, f) for f in fs if f.endswith(".java")]
    return sorted(out)


def jar_dir(classes_dir, jar_path):
    with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as z:
        for dp, _, fs in os.walk(classes_dir):
            for f in sorted(fs):
                full = os.path.join(dp, f)
                z.write(full, os.path.relpath(full, classes_dir))


def build_stub_jar(build):
    stubs_src = os.path.join(ROOT, "android", "stubs")
    out = os.path.join(build, "stub_classes")
    shutil.rmtree(out, ignore_errors=True)
    os.makedirs(out)
    run(["javac", "--release", "8", "-nowarn", "-d", out] + java_files(stubs_src))
    jar = os.path.join(build, "android-stub.jar")
    jar_dir(out, jar)
    return jar


def ensure_keystore(path, alias, password):
    if os.path.exists(path):
        return
    os.makedirs(os.path.dirname(path), exist_ok=True)
    run(["keytool", "-genkeypair", "-keystore", path, "-storetype", "PKCS12",
         "-storepass", password, "-keypass", password, "-alias", alias,
         "-keyalg", "RSA", "-keysize", "2048", "-validity", "36500",
         "-dname", "CN=PetForge, O=PetForge, C=TW"])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--package", required=True)
    ap.add_argument("--label", required=True)
    ap.add_argument("--version-code", type=int, default=1)
    ap.add_argument("--version-name", default="1.0")
    ap.add_argument("--min-sdk", type=int, default=26)
    ap.add_argument("--target-sdk", type=int, default=33)
    ap.add_argument("--src", required=True, help="dir with app .java sources")
    ap.add_argument("--assets", help="dir copied into the APK as assets/")
    ap.add_argument("--activity", required=True, help="launcher activity class")
    ap.add_argument("--service", action="append", default=[], help="service class (repeatable)")
    ap.add_argument("--permission", action="append", default=[])
    ap.add_argument("--fgs-type", type=lambda x: int(x, 0), default=None,
                    help="foregroundServiceType flag for services, e.g. 0x1 (dataSync)")
    ap.add_argument("--keystore", default=os.path.join(ROOT, "keystore", "petforge.p12"))
    ap.add_argument("--alias", default="petforge")
    ap.add_argument("--storepass", default="petforge-pass")
    ap.add_argument("--out", required=True)
    ap.add_argument("--build-dir", default=os.path.join(ROOT, "build"))
    a = ap.parse_args()

    build = a.build_dir
    os.makedirs(build, exist_ok=True)
    out_apk = os.path.abspath(a.out)
    os.makedirs(os.path.dirname(out_apk), exist_ok=True)

    # 1. stub android.jar + app classes
    stub_jar = build_stub_jar(build)
    classes = os.path.join(build, "app_classes")
    shutil.rmtree(classes, ignore_errors=True)
    os.makedirs(classes)
    run(["javac", "--release", "8", "-nowarn", "-cp", stub_jar, "-d", classes] + java_files(a.src))
    app_jar = os.path.join(build, "app.jar")
    jar_dir(classes, app_jar)

    # 2. dex
    dex_dir = os.path.join(build, "dex")
    shutil.rmtree(dex_dir, ignore_errors=True)
    os.makedirs(dex_dir)
    run(["java", "-cp", BUNDLETOOL, "com.android.tools.r8.D8", "--release",
         "--min-api", str(a.min_sdk), "--lib", stub_jar, "--output", dex_dir, app_jar])

    # 3. manifest
    services = [dict(name=s, exported=False, fgs_type_flag=a.fgs_type) for s in a.service]
    manifest = simple_manifest(
        a.package, a.version_code, a.version_name, a.min_sdk, a.target_sdk, a.label,
        activities=[dict(name=a.activity, launcher=True)],
        services=services, permissions=a.permission)
    manifest_bytes = build_manifest_bytes(manifest)

    # 4. unsigned APK
    unsigned = os.path.join(build, "unsigned.apk")
    with zipfile.ZipFile(unsigned, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("AndroidManifest.xml", manifest_bytes)
        for f in sorted(os.listdir(dex_dir)):
            if f.endswith(".dex"):
                z.write(os.path.join(dex_dir, f), f)
        if a.assets:
            for dp, _, fs in os.walk(a.assets):
                for f in sorted(fs):
                    full = os.path.join(dp, f)
                    z.write(full, "assets/" + os.path.relpath(full, a.assets).replace(os.sep, "/"))

    # 5. sign
    ensure_keystore(a.keystore, a.alias, a.storepass)
    signer_cls = os.path.join(build, "signer")
    os.makedirs(signer_cls, exist_ok=True)
    run(["javac", "--release", "8", "-nowarn", "-cp", BUNDLETOOL, "-d", signer_cls,
         os.path.join(HERE, "SignApk.java")])
    cp = BUNDLETOOL + os.pathsep + signer_cls
    run(["java", "-cp", cp, "SignApk", "sign", a.keystore, a.storepass, a.alias,
         unsigned, out_apk, str(a.min_sdk)])
    print(run(["java", "-cp", cp, "SignApk", "verify", out_apk]).strip())
    print("APK:", out_apk, os.path.getsize(out_apk), "bytes")


if __name__ == "__main__":
    main()
