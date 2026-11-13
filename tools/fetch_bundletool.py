#!/usr/bin/env python3
"""Fetch tools/bundletool.jar (provides D8 + apksig) from npm and verify its SHA-256.

The jar is third-party (npm package node-bundletool@1.0.0, MIT, wraps Google's bundletool).
The user approved exactly this file; the hash below pins it, so a different file is refused.
"""
import hashlib
import os
import subprocess
import sys
import tarfile
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
JAR = os.path.join(HERE, "bundletool.jar")
SHA256 = "a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"


def sha(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def ensure():
    if os.path.exists(JAR) and sha(JAR) == SHA256:
        return JAR
    with tempfile.TemporaryDirectory() as tmp:
        out = subprocess.run(["npm", "pack", "node-bundletool@1.0.0", "--silent"], cwd=tmp,
                             capture_output=True, text=True)
        if out.returncode != 0:
            sys.exit("npm pack failed:\n" + out.stderr)
        tgz = os.path.join(tmp, out.stdout.strip().splitlines()[-1])
        with tarfile.open(tgz) as t:
            member = t.getmember("package/bundletool.jar")
            member.name = "bundletool.jar"
            t.extract(member, tmp)
        got = sha(os.path.join(tmp, "bundletool.jar"))
        if got != SHA256:
            sys.exit(f"bundletool.jar hash mismatch: {got} (expected {SHA256}); refusing to use it")
        os.replace(os.path.join(tmp, "bundletool.jar"), JAR)
    return JAR


if __name__ == "__main__":
    print(ensure())
