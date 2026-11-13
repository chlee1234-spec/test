"""Emit a desktopPet-schema animations.xml (https://esheep.petrucci.ch/) for the fixed
16-frame PetForge layout. The original Windows app keys transparency on pure magenta
(no alpha), so the sprite sheet is flattened onto magenta with hard edges."""
import base64
import io
from xml.sax.saxutils import escape

from PIL import Image

GRID = 4
MAGENTA = (255, 0, 255)


def flatten_on_magenta(sheet, thr=128):
    """RGBA sheet -> RGB PNG bytes; pixels with alpha>=thr keep colour, rest = magenta."""
    px = sheet.convert("RGBA")
    out = Image.new("RGB", px.size, MAGENTA)
    src, dst = px.load(), out.load()
    for y in range(px.height):
        for x in range(px.width):
            r, g, b, a = src[x, y]
            if a >= thr:
                if (r, g, b) == MAGENTA:      # never collide with the key colour
                    r, b = 254, 254
                dst[x, y] = (r, g, b)
    buf = io.BytesIO()
    out.save(buf, "PNG", optimize=True)
    return buf.getvalue()


def icon_b64(frame):
    buf = io.BytesIO()
    frame.convert("RGBA").resize((48, 48), Image.LANCZOS).save(buf, "ICO", sizes=[(48, 48)])
    return base64.b64encode(buf.getvalue()).decode()


# id, name, (start x,y,offsety,interval), repeat, action, frames, nexts, borders, gravity
ANIMS = [
    (1, "idle", (0, 0, 0, 200), "random/20+2", "none", [4, 5, 6, 7],
     [(30, 1), (45, 2), (5, 4), (12, 5)], [], 7),
    (2, "walk", (6, 0, 0, 140), "6+random/8", "none", [0, 1, 2, 3],
     [(50, 2), (35, 1), (8, 5), (7, 3)], [(100, "none", 3), (10, "vertical", 3)], 7),
    (3, "turn", (0, 0, 0, 200), "0", "flip", [4], [(70, 2), (30, 1)], [], None),
    (4, "sleep", (0, 0, 0, 500), "6+random/6", "none", [8, 9], [(80, 1), (20, 4)], [], None),
    (5, "happy", (0, 0, 0, 160), "3+random/12", "none", [10, 11], [(70, 1), (30, 2)], [], 7),
    (6, "drag", (0, 0, 0, 150), "0", "none", [12, 13], [(10, 1)], [], None),
    (7, "fall", (0, 14, 0, 45), "120", "none", [14, 15], [(10, 1)], [(10, "taskbar", 1)], None),
]


def _move(tag, x, y, oy, iv):
    return (f"      <{tag}>\n        <x>{x}</x>\n        <y>{y}</y>\n        <offsety>{oy}</offsety>\n"
            f"        <opacity>1</opacity>\n        <interval>{iv}</interval>\n      </{tag}>\n")


def build_xml(sheet_rgba, name, author="PetForge", cell=96):
    png = base64.b64encode(flatten_on_magenta(sheet_rgba)).decode()
    icon = icon_b64(sheet_rgba.crop((0, cell, cell, 2 * cell)))   # frame 4 (row 1, col 0)
    o = ['<?xml version="1.0" encoding="utf-8"?>\n'
         '<animations xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" '
         'xsi:schemaLocation="https://esheep.petrucci.ch/ https://adrianotiger.github.io/desktopPet/Resources/animations.xsd" '
         'xmlns="https://esheep.petrucci.ch/">\n',
         "  <header>\n"
         f"    <author>{escape(author)}</author>\n    <title>{escape(name)}</title>\n"
         f"    <petname>{escape(name)}</petname>\n    <version>1.0</version>\n"
         f"    <info>Made with PetForge</info>\n    <application>1</application>\n"
         f"    <icon><![CDATA[{icon}]]></icon>\n  </header>\n",
         f"  <image>\n    <tilesx>{GRID}</tilesx>\n    <tilesy>{GRID}</tilesy>\n    <skins>1</skins>\n"
         f"    <png><![CDATA[{png}]]></png>\n    <transparency>Magenta</transparency>\n  </image>\n",
         '  <spawns>\n'
         '    <spawn id="1" probability="10">\n      <x>random/100*(screenW-imageW)</x>\n      <y>-imageH-20</y>\n      <next>7</next>\n    </spawn>\n'
         '    <spawn id="2" probability="10">\n      <x>random/100*(screenW-imageW)</x>\n      <y>areaH-imageH</y>\n      <next>1</next>\n    </spawn>\n'
         '  </spawns>\n  <animations>\n']
    for aid, nm, (sx, sy, oy, iv), rep, act, frames, nxt, border, grav in ANIMS:
        o.append(f'    <animation id="{aid}">\n      <name>{nm}</name>\n')
        o.append(_move("start", sx, sy, oy, iv))
        o.append(_move("end", sx, sy, oy, iv))
        o.append(f'      <sequence repeatfrom="0" repeat="{rep}">\n')
        o += [f"        <frame>{f}</frame>\n" for f in frames]
        o.append(f"        <action>{act}</action>\n")
        o += [f'        <next probability="{p}" only="none">{n}</next>\n' for p, n in nxt]
        o.append("      </sequence>\n")
        if border:
            o.append("      <border>\n")
            o += [f'        <next probability="{p}" only="{only}">{n}</next>\n' for p, only, n in border]
            o.append("      </border>\n")
        if grav:
            o.append(f'      <gravity>\n        <next probability="100" only="none">{grav}</next>\n      </gravity>\n')
        o.append("    </animation>\n")
    o.append("  </animations>\n  <childs />\n</animations>\n")
    return "".join(o)
