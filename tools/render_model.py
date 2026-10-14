#!/usr/bin/env python3
"""Orthographic software render of a pack item model's ELEMENT GEOMETRY (front and side), to sanity-check shapes and UVs.
Axis-aligned cuboids only (rotated elements are drawn unrotated). This is a geometry check, NOT an in-game screenshot."""
import json, os, sys
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
NS = os.path.abspath(os.path.join(HERE, "..", "resourcepack", "assets", "shinobi"))

def tex(ref):
    return Image.open(os.path.join(NS, "textures", ref.split(":")[1] + ".png")).convert("RGBA")

def render(name, view, S=24):
    m = json.load(open(os.path.join(NS, "models", "item", name + ".json")))
    textures = {k: tex(v) for k, v in m["textures"].items() if k != "particle"}
    img = Image.new("RGBA", (16 * S, 16 * S), (40, 40, 48, 255))
    # head (8px) and a netherite-ish helmet outline (10px) for scale
    for half, col in ((5, (70, 60, 70, 255)), (4, (200, 160, 130, 255))):
        for y in range(8 - half, 8 + half):
            for x in range(8 - half, 8 + half):
                img.paste(col, (x * S, (15 - y) * S, (x + 1) * S, (16 - y) * S))
    face, axis_depth = ("north", 2) if view == "front" else ("west", 0)
    items = sorted(m["elements"], key=lambda e: -e["from"][axis_depth] if view == "front" else e["from"][axis_depth])
    for e in items:
        fd = e["faces"].get(face)
        if not fd: continue
        t = textures[fd["texture"][1:]]
        u0, v0, u1, v1 = fd["uv"]
        fh = t.height if t.height > t.width else t.width   # animated strips: first frame is width x width
        frame = t.crop((0, 0, t.width, t.width)) if t.height > t.width else t
        a, b = (0, 1) if view == "front" else (2, 1)
        x0, x1 = e["from"][a], e["to"][a]
        y0, y1 = e["from"][b], e["to"][b]
        if view == "front": x0, x1 = 16 - x1, 16 - x0   # looking south, east (+x) is on the viewer's left
        px0, px1 = int(x0 * S), int(x1 * S)
        py0, py1 = int((16 - y1) * S), int((16 - y0) * S)
        if px1 <= px0 or py1 <= py0: continue
        sub = frame.crop((int(u0 * frame.width / 16), int(v0 * frame.height / 16), max(int(u1 * frame.width / 16), 1), max(int(v1 * frame.height / 16), 1)))
        img.alpha_composite(sub.resize((px1 - px0, py1 - py0), Image.NEAREST), (px0, py0))
    return img.convert("RGB")

if __name__ == "__main__":
    names = sys.argv[1:] or ["headgear_none", "headgear_sharingan_3", "headgear_byakugan"]
    views = [(n, v) for n in names for v in ("front", "side")]
    sheet = Image.new("RGB", (len(views) * 16 * 24 // 2 + 10, 16 * 24 // 2 + 10), (20, 20, 24))
    for i, (n, v) in enumerate(views):
        sheet.paste(render(n, v).resize((16 * 12, 16 * 12), Image.NEAREST), (5 + i * 16 * 12, 5))
    out = os.path.abspath(os.path.join(HERE, "..", "docs", "previews", "headgear_geometry_render.png"))
    sheet.save(out); print("wrote", out, sheet.size)
