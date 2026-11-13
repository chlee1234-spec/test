#!/usr/bin/env python3
"""Convert a desktopPet animations.xml into the runtime format (pet.txt + sprite.png).

usage: xml2pet.py <animations.xml> <outdir>

Output:
  pet.txt     line-based animation data (see FORMAT.md)
  sprite.png  RGBA sprite sheet; any colour key is turned into real alpha
  icon.png    first frame, for launcher / tray icons
"""
import base64
import io
import os
import re
import sys
import xml.etree.ElementTree as ET

from PIL import Image

NS = {"p": "https://esheep.petrucci.ch/"}
KEYS = {"magenta": (255, 0, 255), "cyan": (0, 255, 255), "green": (0, 128, 0),
        "lime": (0, 255, 0), "red": (255, 0, 0), "blue": (0, 0, 255), "white": (255, 255, 255),
        "black": (0, 0, 0), "yellow": (255, 255, 0)}


def txt(node, path, default=""):
    n = node.find(path, NS)
    return (n.text or "").strip() if n is not None and n.text is not None else default


def clean_expr(s):
    return re.sub(r"\s+", " ", s.strip()) or "0"


def load_sprite(b64, key):
    raw = base64.b64decode(re.sub(r"\s", "", b64))
    im = Image.open(io.BytesIO(raw)).convert("RGBA")
    k = (key or "").strip().lower()
    if k in KEYS:
        kr, kg, kb = KEYS[k]
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                r, g, b, a = px[x, y]
                if abs(r - kr) <= 2 and abs(g - kg) <= 2 and abs(b - kb) <= 2:
                    px[x, y] = (0, 0, 0, 0)
    return im


def convert(xml_path, out_dir):
    root = ET.parse(xml_path).getroot()
    hdr = root.find("p:header", NS)
    title = txt(hdr, "p:petname") or txt(hdr, "p:title") or "pet"
    img = root.find("p:image", NS)
    tx, ty = int(txt(img, "p:tilesx")), int(txt(img, "p:tilesy"))
    sprite = load_sprite(txt(img, "p:png"), txt(img, "p:transparency"))
    if sprite.width % tx or sprite.height % ty:
        raise SystemExit(f"sprite {sprite.size} is not divisible by {tx}x{ty}")
    cw, ch = sprite.width // tx, sprite.height // ty

    lines = ["V\t1", f"NAME\t{title}", f"IMG\t{tx}\t{ty}\t{cw}\t{ch}"]
    for sp in root.find("p:spawns", NS).findall("p:spawn", NS):
        lines.append("\t".join([
            "S", sp.get("id", "0"), sp.get("probability", "1"), txt(sp, "p:next"),
            clean_expr(txt(sp, "p:x")), clean_expr(txt(sp, "p:y"))]))

    for an in root.find("p:animations", NS).findall("p:animation", NS):
        lines.append(f"A\t{an.get('id')}\t{txt(an, 'p:name').replace(chr(9), ' ')}")
        for tag, name in (("ST", "p:start"), ("EN", "p:end")):
            m = an.find(name, NS)
            lines.append("\t".join([
                tag, clean_expr(txt(m, "p:x")), clean_expr(txt(m, "p:y")),
                txt(m, "p:offsety", "0") or "0", txt(m, "p:opacity", "1") or "1",
                clean_expr(txt(m, "p:interval", "200"))]))
        seq = an.find("p:sequence", NS)
        action = txt(seq, "p:action", "none")
        lines.append("\t".join(["Q", clean_expr(seq.get("repeat", "0")),
                                seq.get("repeatfrom", "0"), action]))
        lines.append("F\t" + " ".join(f.text.strip() for f in seq.findall("p:frame", NS)))
        for nx in seq.findall("p:next", NS):
            lines.append(f"N\t{nx.get('probability', '1')}\t{nx.get('only', 'none')}\t{nx.text.strip()}")
        for tag, name in (("B", "p:border"), ("G", "p:gravity")):
            blk = an.find(name, NS)
            if blk is not None:
                for nx in blk.findall("p:next", NS):
                    lines.append(f"{tag}\t{nx.get('probability', '1')}\t{nx.get('only', 'none')}\t{nx.text.strip()}")

    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "pet.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    sprite.save(os.path.join(out_dir, "sprite.png"))
    sprite.crop((0, 0, cw, ch)).resize((96, 96), Image.NEAREST).save(os.path.join(out_dir, "icon.png"))
    print(f"{title}: {tx}x{ty} tiles of {cw}x{ch}, {len([l for l in lines if l.startswith('A')])} animations -> {out_dir}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    convert(sys.argv[1], sys.argv[2])
