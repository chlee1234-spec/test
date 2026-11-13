"""Minimal Android binary XML (AXML) writer, enough for an AndroidManifest.xml.

Why this exists: aapt2 needs a platform android.jar to link a manifest, and no
android.jar is available in this sandbox. A manifest without resources only
needs the binary XML container, which is simple.

Usage:
    from axml import Element, build_manifest_bytes
"""
import struct

ANDROID_NS = "http://schemas.android.com/apk/res/android"

# android:* attribute resource IDs (framework public IDs, stable across releases)
ATTR_IDS = {
    "theme": 0x01010000,
    "label": 0x01010001,
    "icon": 0x01010002,
    "name": 0x01010003,
    "permission": 0x01010006,
    "exported": 0x01010010,
    "enabled": 0x0101000E,
    "launchMode": 0x0101001D,
    "screenOrientation": 0x0101001E,
    "configChanges": 0x0101001F,
    "minSdkVersion": 0x0101020C,
    "versionCode": 0x0101021B,
    "versionName": 0x0101021C,
    "targetSdkVersion": 0x01010270,
    "allowBackup": 0x01010280,
    "foregroundServiceType": 0x01010599,
}

RES_XML_TYPE = 0x0003
RES_STRING_POOL_TYPE = 0x0001
RES_XML_RESOURCE_MAP_TYPE = 0x0180
RES_XML_START_NAMESPACE_TYPE = 0x0100
RES_XML_END_NAMESPACE_TYPE = 0x0101
RES_XML_START_ELEMENT_TYPE = 0x0102
RES_XML_END_ELEMENT_TYPE = 0x0103

TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_INT_BOOLEAN = 0x12

NO_INDEX = 0xFFFFFFFF


class Element:
    """tag + attrs. attrs: list of (ns, name, value); ns is 'android' or None.
    value: str -> string, bool -> boolean, int -> decimal int, ('hex', int) -> hex int."""

    def __init__(self, tag, attrs=None, children=None):
        self.tag = tag
        self.attrs = attrs or []
        self.children = children or []


def _collect(el, names, strings):
    names.add(el.tag)
    for ns, name, value in el.attrs:
        names.add((ns, name))
        if isinstance(value, str):
            strings.add(value)
    for c in el.children:
        _collect(c, names, strings)


class _Pool:
    def __init__(self):
        self.items = []
        self.index = {}

    def add(self, s):
        if s not in self.index:
            self.index[s] = len(self.items)
            self.items.append(s)
        return self.index[s]

    def get(self, s):
        return self.index[s]


def _string_pool_chunk(items):
    # UTF-16 pool (flags = 0)
    data = bytearray()
    offsets = []
    for s in items:
        offsets.append(len(data))
        u = s.encode("utf-16-le")
        n = len(s)
        if n > 0x7FFF:
            raise ValueError("string too long")
        data += struct.pack("<H", n) + u + b"\x00\x00"
    while len(data) % 4:
        data += b"\x00"
    header_size = 28
    strings_start = header_size + 4 * len(items)
    size = strings_start + len(data)
    out = struct.pack(
        "<HHIIIIII",
        RES_STRING_POOL_TYPE,
        header_size,
        size,
        len(items),
        0,  # style count
        0,  # flags (UTF-16)
        strings_start,
        0,  # styles start
    )
    out += b"".join(struct.pack("<I", o) for o in offsets)
    out += bytes(data)
    return out


def _node(node_type, body, line=1):
    header = struct.pack("<HHIII", node_type, 16, 16 + len(body), line, NO_INDEX)
    return header + body


def build_manifest_bytes(root):
    """root: Element (the <manifest>). Returns AXML bytes."""
    names, strings = set(), set()
    _collect(root, names, strings)

    # Attribute names that carry a resource ID must come first in the pool,
    # ordered by their position in the resource map.
    res_attr_names = sorted(
        {n for n in names if isinstance(n, tuple) and n[0] == "android"},
        key=lambda t: ATTR_IDS[t[1]],
    )
    pool = _Pool()
    res_ids = []
    for _, name in res_attr_names:
        if name not in ATTR_IDS:
            raise KeyError(f"no resource id for android:{name}")
        pool.add(name)
        res_ids.append(ATTR_IDS[name])

    pool.add("android")
    pool.add(ANDROID_NS)
    for n in sorted(x for x in names if isinstance(x, tuple) and x[0] is None):
        pool.add(n[1])
    for n in sorted(x for x in names if isinstance(x, str)):
        pool.add(n)
    for s in sorted(strings):
        pool.add(s)

    prefix_idx = pool.get("android")
    uri_idx = pool.get(ANDROID_NS)

    body = bytearray()
    body += _node(
        RES_XML_START_NAMESPACE_TYPE, struct.pack("<II", prefix_idx, uri_idx)
    )

    def emit(el):
        attrs = []
        for ns, name, value in el.attrs:
            rid = ATTR_IDS[name] if ns == "android" else 0
            attrs.append((rid, ns, name, value))
        attrs.sort(key=lambda a: a[0])  # aapt sorts by resource id
        out = bytearray()
        out += struct.pack(
            "<IIHHHHHH",
            NO_INDEX,
            pool.get(el.tag),
            20,
            20,
            len(attrs),
            0,
            0,
            0,
        )
        for rid, ns, name, value in attrs:
            ns_idx = uri_idx if ns == "android" else NO_INDEX
            name_idx = pool.get(name)
            if isinstance(value, bool):
                raw, dtype, data = NO_INDEX, TYPE_INT_BOOLEAN, (0xFFFFFFFF if value else 0)
            elif isinstance(value, int):
                raw, dtype, data = NO_INDEX, TYPE_INT_DEC, value & 0xFFFFFFFF
            elif isinstance(value, tuple) and value[0] == "hex":
                raw, dtype, data = NO_INDEX, TYPE_INT_HEX, value[1] & 0xFFFFFFFF
            else:
                idx = pool.get(value)
                raw, dtype, data = idx, TYPE_STRING, idx
            out += struct.pack("<III", ns_idx, name_idx, raw)
            out += struct.pack("<HBBI", 8, 0, dtype, data)
        body.extend(_node(RES_XML_START_ELEMENT_TYPE, bytes(out)))
        for c in el.children:
            emit(c)
        body.extend(
            _node(
                RES_XML_END_ELEMENT_TYPE,
                struct.pack("<II", NO_INDEX, pool.get(el.tag)),
            )
        )

    emit(root)
    body += _node(RES_XML_END_NAMESPACE_TYPE, struct.pack("<II", prefix_idx, uri_idx))

    pool_chunk = _string_pool_chunk(pool.items)
    resmap = struct.pack("<HHI", RES_XML_RESOURCE_MAP_TYPE, 8, 8 + 4 * len(res_ids))
    resmap += b"".join(struct.pack("<I", r) for r in res_ids)

    total = 8 + len(pool_chunk) + len(resmap) + len(body)
    return struct.pack("<HHI", RES_XML_TYPE, 8, total) + pool_chunk + resmap + bytes(body)


def simple_manifest(package, version_code, version_name, min_sdk, target_sdk,
                    label, activities=(), services=(), permissions=()):
    """Convenience builder for the manifests PetForge needs.

    activities: list of dict(name, launcher=bool, exported=bool)
    services:   list of dict(name, exported=bool, fgs_type=str|None)
    permissions: list of permission names (uses-permission)
    """
    A = "android"
    children = [
        Element("uses-sdk", [(A, "minSdkVersion", min_sdk), (A, "targetSdkVersion", target_sdk)])
    ]
    for p in permissions:
        children.append(Element("uses-permission", [(A, "name", p)]))

    app_children = []
    for act in activities:
        a_children = []
        if act.get("launcher"):
            a_children.append(
                Element("intent-filter", [], [
                    Element("action", [(A, "name", "android.intent.action.MAIN")]),
                    Element("category", [(A, "name", "android.intent.category.LAUNCHER")]),
                ])
            )
        app_children.append(Element("activity", [
            (A, "label", label),
            (A, "name", act["name"]),
            (A, "exported", bool(act.get("exported", act.get("launcher", False)))),
        ], a_children))
    for svc in services:
        attrs = [(A, "name", svc["name"]), (A, "exported", bool(svc.get("exported", False)))]
        if svc.get("fgs_type_flag") is not None:
            attrs.append((A, "foregroundServiceType", ("hex", svc["fgs_type_flag"])))
        app_children.append(Element("service", attrs))

    children.append(Element("application", [
        (A, "label", label),
        (A, "allowBackup", True),
    ], app_children))

    return Element("manifest", [
        (A, "versionCode", version_code),
        (A, "versionName", version_name),
        (None, "package", package),
    ], children)
