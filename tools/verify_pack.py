#!/usr/bin/env python3
"""Static checks for the generated resource pack (Minecraft 1.21.11, pack format 75).

Checks: pack.mcmeta, items/*.json -> models -> textures, animation .mcmeta strips, font providers,
and that every Clan constant in the Java source has a clan_<name> item definition.
Exit code 1 on any problem. Static analysis only: it does NOT load the pack in a game client.
"""
import json, os, re, struct, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "resourcepack"))
NS = os.path.join(ROOT, "assets", "shinobi")
errors = []

def err(m): errors.append(m)

def png_size(path):
    with open(path, "rb") as f:
        d = f.read(24)
    if d[:8] != b"\x89PNG\r\n\x1a\n": return None
    return struct.unpack(">II", d[16:24])

meta = json.load(open(os.path.join(ROOT, "pack.mcmeta")))["pack"]
if meta.get("min_format") != 75 or meta.get("max_format") != 75: err(f"pack.mcmeta must be min_format=max_format=75, got {meta}")
if "pack_format" in meta: err("pack.mcmeta must not use legacy pack_format")

items = sorted(os.listdir(os.path.join(NS, "items")))
for fn in items:
    d = json.load(open(os.path.join(NS, "items", fn)))
    ref = d.get("model", {}).get("model")
    if not ref or not ref.startswith("shinobi:"): err(f"items/{fn}: bad model ref {ref}"); continue
    mp = os.path.join(NS, "models", ref.split(":")[1] + ".json")
    if not os.path.exists(mp): err(f"items/{fn}: missing model {ref}"); continue
    m = json.load(open(mp))
    for layer, tex in m.get("textures", {}).items():
        if tex.startswith("shinobi:"):
            tp = os.path.join(NS, "textures", tex.split(":")[1] + ".png")
            if not os.path.exists(tp): err(f"{ref}: missing texture {tex}"); continue
            w, h = png_size(tp) or (0, 0)
            if w == 0: err(f"{tp}: not a PNG"); continue
            if os.path.exists(tp + ".mcmeta"):
                json.load(open(tp + ".mcmeta"))
                if h % w: err(f"{tp}: animated strip height {h} not a multiple of width {w}")
            elif (w, h) != (16, 16): err(f"{tp}: expected 16x16, got {w}x{h}")

for p in json.load(open(os.path.join(NS, "font", "hud.json")))["providers"]:
    if p["type"] != "bitmap": continue
    tp = os.path.join(NS, "textures", p["file"].split(":")[1])
    if not os.path.exists(tp): err(f"font: missing {p['file']}"); continue
    w, h = png_size(tp)
    rows = p["chars"]
    cols = len(rows[0]) if rows else 0
    if any(len(r) != cols for r in rows): err(f"font {p['file']}: ragged chars rows")
    elif cols and (w % cols or h % len(rows)): err(f"font {p['file']}: {w}x{h} not divisible by {cols}x{len(rows)} grid")

clan_src = open(os.path.join(HERE, "..", "src", "main", "java", "dev", "shinobi", "Clan.java")).read()
for name in re.findall(r"^\s+([A-Z_]+)\(\"", clan_src, re.M):
    if not os.path.exists(os.path.join(NS, "items", f"clan_{name.lower()}.json")): err(f"Clan {name}: no items/clan_{name.lower()}.json")

# --- custom 3D models (element geometry): headgear per EyeStyle, chakra orbs ---
eye_src = open(os.path.join(HERE, "..", "src", "main", "java", "dev", "shinobi", "EyeStyle.java")).read()
block = eye_src.split("EYE-STYLES-BEGIN")[1].split("EYE-STYLES-END")[0]
styles = re.findall(r'"([a-z0-9_]+)"', block)
if len(styles) < 2: err("could not read EyeStyle.ALL")
required_3d = ["headgear_" + k for k in styles] + ["orb_lightning", "orb_rasengan", "orb_wind", "orb_fire"]
n_geometry = 0
def check_geometry(name):
    mp = os.path.join(NS, "models", "item", name + ".json")
    if not os.path.exists(mp): err(f"3D model {name}: missing models/item/{name}.json"); return
    m = json.load(open(mp))
    els = m.get("elements")
    if not els: err(f"{name}: expected element geometry, found none"); return
    texs = m.get("textures", {})
    for i, e in enumerate(els):
        for k in ("from", "to"):
            if len(e[k]) != 3 or any(not (-16 <= c <= 32) for c in e[k]): err(f"{name} element {i}: {k} out of range {e[k]}")
        if any(e["from"][a2] >= e["to"][a2] and not (a2 == 1 and False) for a2 in range(3)): err(f"{name} element {i}: from must be below to")
        for face, fd in e.get("faces", {}).items():
            if face not in ("north", "south", "east", "west", "up", "down"): err(f"{name} element {i}: bad face {face}")
            uv = fd.get("uv", [])
            if len(uv) != 4 or any(not (0 <= c <= 16) for c in uv): err(f"{name} element {i} {face}: uv out of 0..16 {uv}")
            ref = fd.get("texture", "")
            if not ref.startswith("#") or ref[1:] not in texs: err(f"{name} element {i} {face}: texture ref {ref} not declared")
    return len(els)
for name in required_3d:
    ip = os.path.join(NS, "items", name + ".json")
    if not os.path.exists(ip): err(f"3D model {name}: missing items/{name}.json"); continue
    n = check_geometry(name)
    if n: n_geometry += 1
for fn in os.listdir(os.path.join(NS, "items")):
    if (fn.startswith("headgear_") or fn.startswith("orb_")) and fn[:-5] not in required_3d: err(f"items/{fn}: not declared (EyeStyle.ALL / orb list)")

orphans = []
used = {json.load(open(os.path.join(NS, "items", f)))["model"]["model"].split("/")[-1] for f in items}
for fn in os.listdir(os.path.join(NS, "textures", "item")):
    if fn.endswith(".png") and fn[:-4] not in used: orphans.append(fn)
if orphans: print("note: textures with no item definition:", orphans)

print(f"checked {len(items)} item definitions, {n_geometry} custom 3D (element) models")
if errors:
    print("FAILED:"); [print(" -", e) for e in errors]; sys.exit(1)
print("pack OK (static checks)")
