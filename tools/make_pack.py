#!/usr/bin/env python3
"""Generates the ShinobiSMP resource pack (item icons + HUD glyph font) with no external dependencies.

Usage: python3 tools/make_pack.py   ->   resourcepack/  and  ShinobiPack.zip
PACK_FORMAT is for your Minecraft version: 46 = 1.21.4, 55 = 1.21.5, 63 = 1.21.6, 64 = 1.21.7-1.21.8,
69 = 1.21.9-1.21.10, 75 = 1.21.11 (default). From 1.21.9 on, pack.mcmeta uses min_format/max_format.
"""
import json, os, struct, zlib, zipfile, hashlib

PACK_FORMAT = 75
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepack")
ROOT = os.path.abspath(ROOT)

T = (0, 0, 0, 0)

def rgb(h, a=255):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, a)

# ---------- tiny PNG writer ----------
def write_png(path, pixels):
    h = len(pixels); w = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)
    def chunk(t, d):
        c = struct.pack(">I", len(d)) + t + d
        return c + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(png)

# ---------- drawing helpers ----------
def canvas(w=16, h=16):
    return [[T for _ in range(w)] for _ in range(h)]

def px(c, x, y, col):
    if 0 <= y < len(c) and 0 <= x < len(c[0]):
        c[y][x] = col

def rect(c, x0, y0, x1, y1, col):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(c, x, y, col)

def line(c, x0, y0, x1, y1, col):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        px(c, x0, y0, col)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy: err += dy; x0 += sx
        if e2 <= dx: err += dx; y0 += sy

def disc(c, cx, cy, r, col):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            if (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                px(c, x, y, col)

# ---------- palette ----------
STEEL = rgb(0xb8c0c8); DARK = rgb(0x4a4f57); BROWN = rgb(0x7a4a22); TAN = rgb(0xe3cf9a); TAN_D = rgb(0xb89a5e)
RED = rgb(0xc8281e); BLUE = rgb(0x2a5db0); LBLUE = rgb(0x7fd0ff); GOLD = rgb(0xf2c230); GOLD_D = rgb(0xb98a12)
GREEN = rgb(0x3f9d4a); WHITE = rgb(0xf4f4f4); GRAY = rgb(0x8c8c8c); BLACK = rgb(0x151515); YELLOW = rgb(0xf3e08a)
CRIM = rgb(0xa01018)

# ---------- item icons ----------
def kunai():
    c = canvas()
    line(c, 4, 11, 12, 3, STEEL); line(c, 5, 11, 13, 3, STEEL); line(c, 5, 10, 12, 4, WHITE)
    px(c, 13, 2, STEEL); px(c, 12, 2, STEEL)
    line(c, 3, 12, 5, 10, BROWN); line(c, 4, 13, 6, 11, BROWN)
    disc(c, 2, 13, 1.5, GRAY); px(c, 2, 13, T)
    return c

def shuriken():
    c = canvas()
    for d in range(7):
        w = max(0, (6 - d) // 2)
        for dx in range(-w, w + 1):
            px(c, 8 + dx, 1 + d, STEEL); px(c, 8 + dx, 14 - d, STEEL)
            px(c, 1 + d, 8 + dx, STEEL); px(c, 14 - d, 8 + dx, STEEL)
    rect(c, 7, 7, 8, 8, DARK)
    return c

def explosive_tag():
    c = canvas()
    rect(c, 5, 2, 10, 13, TAN); rect(c, 5, 2, 10, 2, TAN_D); rect(c, 5, 13, 10, 13, TAN_D)
    for y in (4, 6, 8, 10): rect(c, 6, y, 9, y, RED)
    px(c, 7, 5, RED); px(c, 8, 7, RED); px(c, 7, 9, RED); px(c, 8, 11, RED)
    return c

def smoke_bomb():
    c = canvas()
    disc(c, 8, 9, 5, GRAY); disc(c, 7, 8, 2, rgb(0xb5b5b5))
    line(c, 8, 4, 10, 2, BROWN); px(c, 11, 1, rgb(0xff9b2f)); px(c, 11, 2, YELLOW)
    return c

def soldier_pill():
    c = canvas()
    rect(c, 3, 5, 7, 8, RED); rect(c, 8, 5, 12, 8, WHITE); rect(c, 3, 5, 12, 5, rgb(0xe8776f))
    px(c, 3, 5, T); px(c, 12, 5, T); px(c, 3, 8, T); px(c, 12, 8, T)
    rect(c, 5, 10, 8, 12, GOLD); rect(c, 9, 10, 11, 12, GREEN)
    return c

def ramen():
    c = canvas()
    rect(c, 3, 9, 12, 10, CRIM); rect(c, 4, 11, 11, 11, CRIM); rect(c, 5, 12, 10, 12, CRIM); rect(c, 6, 13, 9, 13, CRIM)
    rect(c, 4, 6, 11, 8, YELLOW)
    for x in range(4, 12, 2): px(c, x, 7, TAN_D)
    rect(c, 6, 5, 7, 6, WHITE); px(c, 6, 5, GOLD)
    px(c, 5, 3, rgb(0xdddddd)); px(c, 8, 2, rgb(0xdddddd)); px(c, 10, 3, rgb(0xdddddd))
    return c

def healing_salve():
    c = canvas()
    rect(c, 5, 6, 10, 13, GREEN); rect(c, 6, 7, 6, 11, rgb(0x8fe39a)); rect(c, 5, 4, 10, 5, BROWN)
    rect(c, 6, 9, 9, 11, rgb(0x2a7a35))
    return c

def scroll_base(c, ribbon=None, seal=None):
    rect(c, 3, 5, 12, 11, TAN); rect(c, 3, 5, 12, 5, rgb(0xf3e3b5)); rect(c, 3, 11, 12, 11, TAN_D)
    rect(c, 2, 4, 3, 12, TAN_D); rect(c, 12, 4, 13, 12, TAN_D)
    if ribbon: rect(c, 7, 4, 8, 12, ribbon)
    if seal: disc(c, 8, 8, 2, seal)
    return c

def blank_scroll(): return scroll_base(canvas())
def jutsu_scroll(): return scroll_base(canvas(), RED, rgb(0xe04a3a))
def sealing_scroll(): return scroll_base(canvas(), BLUE, LBLUE)
def mission_scroll(): return scroll_base(canvas(), GOLD, GOLD_D)

def chakra_paper():
    c = canvas()
    rect(c, 4, 2, 11, 13, WHITE)
    for i in range(2, 14, 2): px(c, 3, i, LBLUE); px(c, 12, i, LBLUE)
    for i in range(4, 12, 2): px(c, i, 1, LBLUE); px(c, i, 14, LBLUE)
    rect(c, 6, 6, 9, 9, rgb(0xcfeaff))
    return c

def headband():
    c = canvas()
    rect(c, 1, 6, 14, 9, BLUE); rect(c, 1, 6, 14, 6, rgb(0x3f78d0))
    rect(c, 5, 4, 10, 11, STEEL); rect(c, 5, 4, 10, 4, WHITE)
    line(c, 6, 8, 9, 8, DARK); line(c, 8, 6, 8, 9, DARK)
    return c

def ryo():
    c = canvas()
    disc(c, 8, 8, 6, GOLD_D); disc(c, 8, 8, 5, GOLD); rect(c, 7, 7, 8, 8, GOLD_D)
    line(c, 5, 5, 7, 4, YELLOW)
    return c

def chakra_blade():
    c = canvas()
    line(c, 4, 12, 13, 3, LBLUE); line(c, 5, 12, 14, 3, LBLUE); line(c, 4, 11, 12, 3, WHITE)
    line(c, 3, 10, 7, 14, DARK); line(c, 3, 13, 5, 11, BROWN); px(c, 2, 14, BROWN)
    return c

def training_dummy():
    c = canvas()
    rect(c, 7, 4, 8, 14, BROWN); rect(c, 2, 6, 13, 7, BROWN)
    rect(c, 6, 1, 9, 3, YELLOW); px(c, 6, 1, TAN_D); px(c, 9, 3, TAN_D)
    rect(c, 5, 13, 10, 14, DARK)
    return c

def scroll_desk():
    c = canvas()
    rect(c, 3, 4, 12, 6, TAN); rect(c, 3, 4, 12, 4, rgb(0xf3e3b5))
    rect(c, 2, 6, 13, 7, BROWN)
    rect(c, 7, 8, 8, 12, BROWN); rect(c, 4, 12, 11, 13, rgb(0x5a3518))
    return c

def mission_board():
    c = canvas()
    rect(c, 2, 2, 13, 12, BROWN); rect(c, 3, 3, 12, 11, rgb(0xc9a56a))
    rect(c, 4, 4, 7, 7, WHITE); rect(c, 8, 4, 11, 6, TAN); rect(c, 5, 8, 9, 10, WHITE); rect(c, 10, 8, 11, 10, TAN)
    px(c, 5, 4, RED); px(c, 9, 4, RED); px(c, 6, 8, RED)
    rect(c, 7, 12, 8, 14, BROWN)
    return c

def eternal_eyes():
    c = canvas()
    disc(c, 8, 8, 6, BLACK); disc(c, 8, 8, 5, CRIM)
    disc(c, 8, 8, 1.5, BLACK)
    for (x, y) in ((8, 4), (5, 10), (11, 10)): px(c, x, y, BLACK); px(c, x + 1, y, BLACK)
    px(c, 5, 5, rgb(0xff6b5e))
    return c

ITEMS = {
    "kunai": kunai, "shuriken": shuriken, "explosive_tag": explosive_tag, "smoke_bomb": smoke_bomb,
    "soldier_pill": soldier_pill, "ramen": ramen, "healing_salve": healing_salve, "blank_scroll": blank_scroll,
    "jutsu_scroll": jutsu_scroll, "sealing_scroll": sealing_scroll, "mission_scroll": mission_scroll,
    "chakra_paper": chakra_paper, "headband": headband, "ryo": ryo, "chakra_blade": chakra_blade,
    "training_dummy": training_dummy, "scroll_desk": scroll_desk, "mission_board": mission_board,
    "eternal_eyes": eternal_eyes,
}
HANDHELD = {"chakra_blade", "training_dummy"}

# ---------- HUD glyphs ----------
FONT = {
    "A": "010101111101101", "B": "110101110101110", "D": "110101101101110", "G": "011100101101011",
    "H": "101101111101101", "I": "111010010010111", "K": "101101110101101", "M": "101111111101101",
    "N": "110101101101101", "O": "010101101101010", "R": "110101110101101", "S": "011100010001110",
    "T": "111010010010010", "X": "101101010101101",
}
SEALS = [("RAT", 0x8a6a4a), ("OX", 0x6b4a2a), ("TIG", 0xe08a1e), ("HAR", 0xd8d8d8), ("DRA", 0x2f9e5b),
         ("SNK", 0x5aa832), ("HOR", 0xa8442a), ("RAM", 0xb9b2a0), ("MON", 0xc8a13c), ("BRD", 0x3c8fd6),
         ("DOG", 0x7a7f86), ("BOR", 0x5c3a2e)]

def text3x5(c, s, x0, y0, col):
    x = x0
    for ch in s:
        bits = FONT[ch]
        for i, b in enumerate(bits):
            if b == "1":
                px(c, x + i % 3, y0 + i // 3, col)
        x += 4

def seals_sheet():
    sheet = canvas(16 * len(SEALS), 16)
    for i, (label, color) in enumerate(SEALS):
        cell = canvas()
        disc(cell, 7.5, 7.5, 7.9, BLACK)
        disc(cell, 7.5, 7.5, 7.0, rgb(color))
        width = len(label) * 4 - 1
        text3x5(cell, label, 8 - (width + 1) // 2 + 0, 5, BLACK if color > 0xa0a0a0 else WHITE)
        for y in range(16):
            for x in range(16):
                sheet[y][16 * i + x] = cell[y][x]
    return sheet

def bar_sheet():
    c = canvas(16, 16)
    for y in range(4, 12):
        shade = rgb(0x39a9e0) if y < 8 else rgb(0x1f7fb8)
        for x in range(0, 8): px(c, x, y, shade)
        for x in range(8, 16): px(c, x, y, rgb(0x2b2f36))
    for x in range(0, 8): px(c, x, 4, rgb(0x8fdcff))
    for x in range(8, 16): px(c, x, 11, rgb(0x1a1d22))
    return c

# =====================================================================================
# v2: eyes, natures, per-jutsu scrolls, mission icons, clan crests, gates, animation support
# =====================================================================================
import math

def blit(c, rows, x0, y0, col, scale=1):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#":
                for dy in range(scale):
                    for dx in range(scale):
                        px(c, x0 + i * scale + dx, y0 + j * scale + dy, col)

# ---------- eyes ----------
ALMOND = {3: (6, 9), 4: (4, 11), 5: (3, 12), 6: (2, 13), 7: (1, 14), 8: (1, 14), 9: (2, 13), 10: (3, 12), 11: (4, 11), 12: (6, 9)}
IR = 3.5
EYE_FILL = {(x, y) for y, (x0, x1) in ALMOND.items() for x in range(x0, x1 + 1)}
CX = CY = 7.5

def eye_base(c, sclera=WHITE, outline=BLACK):
    for (x, y) in EYE_FILL:
        px(c, x, y, sclera)
    for (x, y) in EYE_FILL:
        if any((x + dx, y + dy) not in EYE_FILL for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            px(c, x, y, outline)

def iris(c, r, col, cx=CX, cy=CY):
    for y in range(16):
        for x in range(16):
            if (x, y) in EYE_FILL and (x - cx) ** 2 + (y - cy) ** 2 <= r * r:
                px(c, x, y, col)

def ring(c, r, col, width=0.7):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - CX, y - CY)
            if (x, y) in EYE_FILL and abs(d - r) <= width:
                px(c, x, y, col)

def polar(r, a):
    return (int(round(CX + r * math.cos(a))), int(round(CY + r * math.sin(a))))

def pupil(c, col=BLACK, r=1.1):
    iris(c, r, col)

def plot_polar(c, pts, col):
    for (r, a) in pts:
        x, y = polar(r, a)
        if (x, y) in EYE_FILL:
            px(c, x, y, col)

def sharingan(stage, f, n=8):
    c = canvas(); eye_base(c); iris(c, IR, rgb(0xd01820)); ring(c, IR, rgb(0x6a0a10), 0.6)
    pupil(c, BLACK, 0.8)
    spin = f * (2 * math.pi / n)
    for k in range(stage):
        a = spin + k * 2 * math.pi / stage
        x, y = polar(2.1, a); px(c, x, y, BLACK)
        x, y = polar(2.1, a - 0.6); px(c, x, y, BLACK)
        x, y = polar(2.6, a - 1.0)
        if (x, y) in EYE_FILL: px(c, x, y, BLACK)
    return c

def mk_base(f, n):
    c = canvas(); eye_base(c); iris(c, IR, rgb(0xd01820)); ring(c, IR, rgb(0x6a0a10), 0.6)
    return c, f * (2 * math.pi / n)

def mangekyo_amaterasu(f, n=8):
    c, s = mk_base(f, n)
    for k in range(3):
        a = s + k * 2 * math.pi / 3
        plot_polar(c, [(r, a) for r in (1.0, 1.7, 2.4, 3.1, 3.6)], BLACK)
        plot_polar(c, [(2.0, a + 0.4), (2.0, a - 0.4), (2.8, a + 0.25), (2.8, a - 0.25)], BLACK)
    pupil(c, BLACK, 0.9)
    return c

def mangekyo_tsukuyomi(f, n=8):
    c, s = mk_base(f, n)
    for k in range(3):
        a0 = s + k * 2 * math.pi / 3
        pts = []
        for i in range(0, 12):
            t = i / 11
            pts.append((0.8 + 2.7 * t, a0 + t * 1.5))
        plot_polar(c, pts, BLACK)
        plot_polar(c, [(r + 0.4, a + 0.12) for (r, a) in pts[3:9]], BLACK)
    pupil(c, BLACK, 1.0)
    return c

def mangekyo_kamui(f, n=8):
    c, s = mk_base(f, n)
    pts = [(0.5 + 3.2 * (i / 24), s + (i / 24) * 4.4 * math.pi) for i in range(25)]
    plot_polar(c, pts, BLACK)
    plot_polar(c, [(r + 0.6, a) for (r, a) in pts[8:22]], BLACK)
    pupil(c, BLACK, 0.8)
    return c

def mangekyo_susanoo(f, n=8):
    c, s = mk_base(f, n)
    ring(c, 3.0, BLACK, 0.55)
    for k in range(3):
        a = s + k * 2 * math.pi / 3
        plot_polar(c, [(r, a) for r in (0.8, 1.5, 2.2)], BLACK)
        plot_polar(c, [(3.5, a + 0.35), (3.5, a - 0.35)], BLACK)
    pupil(c, BLACK, 0.9)
    return c

def eternal_mangekyo(f, n=8):
    c = canvas(); eye_base(c)
    pulse = [0x5a1590, 0x6a1fa8, 0x7b2bc0, 0x8a3ad4, 0x7b2bc0, 0x6a1fa8, 0x5a1590, 0x4a0f7c][f % 8]
    iris(c, IR, rgb(pulse)); ring(c, IR, rgb(0xc08cff), 0.5)
    s = f * (math.pi / 3) / n
    for k in range(6):
        a = s + k * math.pi / 3
        plot_polar(c, [(r, a) for r in (1.0, 1.8, 2.6, 3.4)], BLACK)
    for k in range(3):
        a = s + math.pi / 6 + k * 2 * math.pi / 3
        plot_polar(c, [(r, a) for r in (1.4, 2.2)], BLACK)
    pupil(c, BLACK, 0.9)
    return c

def byakugan(f, n=6):
    c = canvas(); eye_base(c, sclera=rgb(0xf1eefc), outline=rgb(0x3a3560))
    iris(c, IR, rgb(0xd9d3f0)); ring(c, IR, rgb(0xaaa2d6), 0.6); ring(c, 2.0, rgb(0xe6e1f7), 0.5)
    pupil(c, rgb(0xb3abd9), 0.7)
    vein = rgb([0x6f64c8, 0x8478d8, 0xa296ee, 0xb9b0f7, 0xa296ee, 0x8478d8][f % 6])
    for (x0, y0, x1, y1) in ((4, 2, 1, 4), (11, 2, 14, 4), (4, 13, 1, 11), (11, 13, 14, 11)):
        line(c, x0, y0, x1, y1, vein)
    return c

def rinnegan(f, n=6):
    c = canvas(); eye_base(c); iris(c, IR, rgb(0xa98ad8))
    for i, r in enumerate((3.4, 2.5, 1.6)):
        ring(c, r + (0.35 if (f + i) % 3 == 0 else 0), rgb(0x3b2a66), 0.45)
    pupil(c, BLACK, 0.7)
    return c

def plain_eye():
    c = canvas(); eye_base(c); iris(c, 3.2, rgb(0x6b4a2a)); pupil(c, BLACK, 1.3)
    px(c, 6, 6, WHITE)
    return c

def eyes_static():
    return {
        "eye_none": plain_eye(),
        "eye_sharingan_1": sharingan(1, 0), "eye_sharingan_2": sharingan(2, 0), "eye_sharingan_3": sharingan(3, 0),
        "eye_mangekyo_amaterasu": mangekyo_amaterasu(0), "eye_mangekyo_tsukuyomi": mangekyo_tsukuyomi(0),
        "eye_mangekyo_kamui": mangekyo_kamui(0), "eye_mangekyo_susanoo": mangekyo_susanoo(0),
        "eye_eternal_mangekyo": eternal_mangekyo(3), "eye_byakugan": byakugan(3), "eye_rinnegan": rinnegan(0),
    }

EYE_ORDER = ["eye_none", "eye_sharingan_1", "eye_sharingan_2", "eye_sharingan_3", "eye_mangekyo_amaterasu",
             "eye_mangekyo_tsukuyomi", "eye_mangekyo_kamui", "eye_mangekyo_susanoo", "eye_eternal_mangekyo",
             "eye_byakugan", "eye_rinnegan"]

ANIMATED_EYES = {   # name -> (frame function, frames, frametime)
    "eye_sharingan_1": (lambda f: sharingan(1, f), 8, 3), "eye_sharingan_2": (lambda f: sharingan(2, f), 8, 3),
    "eye_sharingan_3": (lambda f: sharingan(3, f), 8, 3),
    "eye_mangekyo_amaterasu": (mangekyo_amaterasu, 8, 3), "eye_mangekyo_tsukuyomi": (mangekyo_tsukuyomi, 8, 3),
    "eye_mangekyo_kamui": (mangekyo_kamui, 8, 3), "eye_mangekyo_susanoo": (mangekyo_susanoo, 8, 3),
    "eye_eternal_mangekyo": (eternal_mangekyo, 8, 4), "eye_byakugan": (byakugan, 6, 4),
    "eye_rinnegan": (rinnegan, 6, 5),
}

# ---------- natures (animated) ----------
def nature_fire(f):
    c = canvas(); off = [0, 1, 0, -1][f % 4]
    for y in range(14, 1, -1):
        t = (14 - y) / 12.0
        w = max(0.0, 5.0 * (1 - t) ** 0.8 + (0.6 if y > 10 else 0.0))
        cx = 8 + off * t * 2
        for x in range(int(cx - w), int(cx + w) + 1):
            px(c, x, y, rgb(0xd8321c))
        w2 = w * 0.62
        for x in range(int(cx - w2), int(cx + w2) + 1):
            if y > 4: px(c, x, y, rgb(0xff8a1f))
        w3 = w * 0.3
        for x in range(int(cx - w3), int(cx + w3) + 1):
            if y > 8: px(c, x, y, rgb(0xffe066))
    px(c, 3 + f % 3, 4 - f % 2, rgb(0xffb347)); px(c, 12 - f % 3, 6 + f % 2, rgb(0xffb347))
    return c

def nature_water(f):
    c = canvas()
    for y in range(2, 11):
        w = 0.5 + (y - 2) * 0.5 if y < 8 else 4 - (y - 8) * 1.2
        for x in range(int(8 - w), int(8 + w) + 1):
            px(c, x, y, rgb(0x2f8fe0))
    for y in range(4, 8): px(c, 6, y, rgb(0x9bd4ff))
    r = 2 + f * 1.0
    for x in range(16):
        for y in range(11, 16):
            d = math.hypot((x - 7.5) / r, (y - 13) / (r * 0.35))
            if abs(d - 1) < 0.18: px(c, x, y, rgb(0x6fb7f0))
    return c

def nature_wind(f):
    c = canvas(); col = rgb(0xdff3f7)
    for k, y0 in enumerate((4, 8, 12)):
        for x in range(1, 15):
            y = int(round(y0 + 1.6 * math.sin((x + f * 2.5 + k * 3) * 0.7)))
            px(c, x, y, col)
        px(c, 14 - (k * 2) % 5, y0 - 2, WHITE)
    return c

def nature_lightning(f):
    c = canvas(); col = [rgb(0xffe24a), rgb(0xfff6a0), rgb(0xffe24a), rgb(0xf2b81c)][f % 4]
    pts = [(10, 1), (5, 8), (9, 8), (5, 15)]
    for i in range(3):
        line(c, *pts[i], *pts[i + 1], col); line(c, pts[i][0] + 1, pts[i][1], pts[i + 1][0] + 1, pts[i + 1][1], col)
    if f % 2 == 0: px(c, 12, 4, WHITE); px(c, 3, 11, WHITE)
    else: px(c, 3, 5, WHITE); px(c, 12, 11, WHITE)
    return c

def nature_earth(f):
    c = canvas()
    disc(c, 6, 10, 4.5, rgb(0x8a6a3f)); disc(c, 11, 11, 3.5, rgb(0x6e5230)); disc(c, 8, 6, 3.5, rgb(0xa88650))
    px(c, 7, 5, rgb(0xd7bb86)); px(c, 5, 9, rgb(0xb89a5e))
    for i in range(3):
        px(c, 2 + i * 5 + (f % 2), 13 + (f + i) % 3, rgb(0xcbb48a))
    return c

NATURES = [("fire", nature_fire, 4, 3), ("water", nature_water, 5, 4), ("wind", nature_wind, 6, 3),
           ("lightning", nature_lightning, 4, 2), ("earth", nature_earth, 4, 4)]

# ---------- per-jutsu scrolls ----------
SYM = {
    "flame": ["...#...", "..##...", ".####..", ".#####.", "#######", ".#####.", "..###.."],
    "drop": ["...#...", "..###..", "..###..", ".#####.", ".#####.", ".#####.", "..###.."],
    "swirl": [".#####.", "#.....#", "#.###.#", "#.#...#", "#.#####", "#......", ".######"],
    "bolt": ["....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##...."],
    "brick": ["#######", "#.#.#.#", "#######", ".#.#.#.", "#######", "#.#.#.#", "#######"],
    "log": ["..###..", ".#...#.", "#..#..#", "#.###.#", "#..#..#", ".#...#.", "..###.."],
    "clones": ["##...##", "##...##", ".#...#.", "###.###", "#.#.#.#", "..#.#..", "..#.#.."],
    "streaks": ["#######", ".......", "..#####", ".......", "#####..", ".......", "...####"],
    "chain": [".###...", "#...#..", "#..#.#.", ".##.#.#", "...#..#", "....#.#", ".....##"],
    "blade": ["......#", ".....##", "....##.", "..###..", "...##..", ".##.#..", "##....."],
    "shadow": [".#####.", "#######", "#######", "#######", ".#####.", "..#.#..", "..#.#.."],
    "feet": [".##....", ".##....", "...##..", "...##..", ".....##", ".....##", "......."],
    "ripple": ["..###..", ".#...#.", "#.###.#", "#.#.#.#", "#.###.#", ".#...#.", "..###.."],
    "crescent": ["..###..", ".#...#.", "#......", "#......", "#......", ".#...#.", "..###.."],
    "serpent": [".###...", "#...#..", "....#..", ".###...", "#......", "#...###", ".###..#"],
    "phoenix": ["..#.#..", ".#####.", "#######", ".#####.", "#######", ".#####.", "..#.#.."],
    "eye": [".#####.", "#.....#", "#.###.#", "#.#.#.#", "#.###.#", "#.....#", ".#####."],
}
FIRE_C = rgb(0xc8281e); WATER_C = rgb(0x2a6fc8); WIND_C = rgb(0x9fd6e0); LIGHT_C = rgb(0xe0b81c); EARTH_C = rgb(0x7a4a22)
GREY_C = rgb(0x6b6f78)
JUTSU_ART = {   # id -> (ribbon/symbol color, symbol)
    "great_fireball": (FIRE_C, "flame"), "phoenix_flower": (FIRE_C, "phoenix"),
    "water_dragon": (WATER_C, "serpent"), "water_wall": (WATER_C, "brick"),
    "wind_blade": (WIND_C, "crescent"), "great_breakthrough": (WIND_C, "swirl"),
    "chidori": (LIGHT_C, "bolt"), "earth_wall": (EARTH_C, "brick"),
    "substitution": (BROWN, "log"), "rasengan": (rgb(0x3fa9e8), "swirl"),
    "shadow_clone": (GREY_C, "clones"), "body_flicker": (GREY_C, "streaks"),
    "chakra_chains": (rgb(0xc8281e), "chain"), "white_light_sabre": (rgb(0xdfe6ee), "blade"),
    "shadow_bind": (rgb(0x2f6b3a), "shadow"), "mangekyo_ability": (CRIM, "eye"),
    "wall_walk": (GREEN, "feet"), "water_walk": (WATER_C, "ripple"),
}

def jutsu_scroll_frame(color, sym, f):
    c = canvas()
    scroll_base(c, color)
    blit(c, SYM[sym], 5, 5, color if sym not in ("shadow",) else rgb(0x20302a))
    hx = 3 + (f * 3) % 10
    px(c, hx, 5, rgb(0xfffbe8)); px(c, hx + 1, 5, rgb(0xfffbe8))
    return c

# ---------- mission icons ----------
def m_hunt():
    c = canvas()
    line(c, 3, 3, 12, 12, STEEL); line(c, 4, 3, 13, 12, STEEL); line(c, 12, 3, 3, 12, STEEL); line(c, 11, 3, 2, 12, STEEL)
    rect(c, 2, 12, 4, 13, BROWN); rect(c, 11, 12, 13, 13, BROWN)
    return c

def m_gather():
    c = canvas()
    line(c, 8, 14, 8, 5, GREEN)
    for (x, y) in ((6, 6), (10, 5), (6, 9), (10, 9)): disc(c, x, y, 1.8, GREEN)
    for (x, y) in ((5, 11), (11, 11), (8, 3)): px(c, x, y, RED)
    return c

def m_delivery():
    c = scroll_base(canvas(), GOLD)
    line(c, 4, 8, 11, 8, DARK); line(c, 9, 6, 11, 8, DARK); line(c, 9, 10, 11, 8, DARK)
    return c

def m_cast():
    c = canvas(); blit(c, SYM["flame"], 1, 1, rgb(0xe8481e), 2)
    blit(c, SYM["flame"], 4, 4, rgb(0xffb347), 1)
    return c

def m_meditate():
    c = canvas()
    disc(c, 8, 4, 2, rgb(0xe8c9a0)); rect(c, 6, 6, 9, 9, BLUE)
    line(c, 3, 11, 12, 11, DARK); line(c, 4, 12, 11, 12, DARK); line(c, 6, 7, 3, 9, BLUE); line(c, 9, 7, 12, 9, BLUE)
    for (x, y) in ((2, 3), (13, 3), (1, 8), (14, 8)): px(c, x, y, LBLUE)
    return c

def m_tree():
    c = canvas()
    rect(c, 7, 6, 8, 14, BROWN); disc(c, 8, 5, 4, rgb(0x2f8f3f)); disc(c, 6, 4, 2, rgb(0x57b560))
    px(c, 6, 10, DARK); px(c, 9, 12, DARK)
    return c

def m_water_walk():
    c = canvas()
    for k, y in enumerate((10, 12, 14)):
        for x in range(1, 15):
            px(c, x, y + int(round(math.sin(x * 0.9 + k))), rgb(0x2f8fe0))
    rect(c, 4, 4, 5, 7, DARK); rect(c, 10, 6, 11, 9, DARK)
    return c

def m_waterfall():
    c = canvas()
    rect(c, 1, 1, 14, 4, rgb(0x7a7f86)); rect(c, 1, 1, 14, 1, rgb(0x9aa0a8))
    rect(c, 5, 4, 10, 12, rgb(0x4aa8f0)); rect(c, 6, 5, 6, 11, rgb(0xb8e2ff)); rect(c, 9, 5, 9, 10, rgb(0xb8e2ff))
    rect(c, 3, 12, 12, 14, rgb(0xdff3ff)); rect(c, 4, 13, 11, 14, rgb(0x2f8fe0))
    return c

def m_weights():
    c = canvas()
    rect(c, 2, 7, 13, 8, GRAY); rect(c, 2, 4, 4, 11, DARK); rect(c, 11, 4, 13, 11, DARK)
    rect(c, 1, 6, 1, 9, DARK); rect(c, 14, 6, 14, 9, DARK)
    return c

MISSION_ICONS = {"mission_hunt": m_hunt, "mission_gather": m_gather, "mission_delivery": m_delivery,
                 "mission_cast": m_cast, "mission_meditate": m_meditate, "mission_tree_climb": m_tree,
                 "mission_water_walk": m_water_walk, "mission_waterfall": m_waterfall,
                 "mission_dummy": training_dummy, "mission_weights": m_weights}

# ---------- clan crests ----------
def clan_uchiha():
    c = canvas()
    for y in range(16):
        for x in range(16):
            if (x - 7.5) ** 2 + (y - 6) ** 2 <= 36:
                px(c, x, y, RED if y < 6 else WHITE)
    for y in range(16):
        for x in range(16):
            if abs(math.hypot(x - 7.5, y - 6) - 6) < 0.6: px(c, x, y, BLACK)
    rect(c, 7, 12, 8, 15, BROWN)
    return c

def clan_hyuga():
    c = canvas(); disc(c, 7.5, 7.5, 7, rgb(0x3a3560)); disc(c, 7.5, 7.5, 6, rgb(0xf1eefc))
    rect(c, 7, 3, 8, 12, rgb(0xaaa2d6)); rect(c, 3, 7, 12, 8, rgb(0xaaa2d6)); disc(c, 7.5, 7.5, 1.6, rgb(0x6f64c8))
    return c

def clan_uzumaki():
    c = canvas(); disc(c, 7.5, 7.5, 7, rgb(0xf3e3b5))
    for i in range(0, 90):
        t = i / 89.0
        a = t * 3.2 * math.pi; r = 0.6 + 6 * t
        px(c, int(round(7.5 + r * math.cos(a))), int(round(7.5 + r * math.sin(a))), RED)
    return c

def clan_hatake():
    c = canvas(); disc(c, 7.5, 7.5, 7, rgb(0x2b2f3a))
    for y in range(16):
        for x in range(16):
            if math.hypot(x - 7.5, y - 7.5) <= 5.5 and math.hypot(x - 10, y - 6.5) > 4.6: px(c, x, y, rgb(0xf4f4f4))
    return c

def clan_nara():
    c = canvas(); disc(c, 7.5, 9, 6, rgb(0x2f6b3a)); disc(c, 7.5, 9, 5, rgb(0x57a468))
    for (x0, y0, x1, y1) in ((6, 9, 4, 2), (9, 9, 11, 2), (5, 5, 2, 4), (10, 5, 13, 4), (5, 3, 3, 1), (10, 3, 12, 1)):
        line(c, x0, y0, x1, y1, BROWN)
    rect(c, 6, 9, 9, 12, rgb(0x2f6b3a))
    return c

CLANS = {"clan_uchiha": clan_uchiha, "clan_hyuga": clan_hyuga, "clan_uzumaki": clan_uzumaki,
         "clan_hatake": clan_hatake, "clan_nara": clan_nara}

# ---------- eight gates ----------
GATE_COLORS = [0x6ac96a, 0x9bd45a, 0xd6d94a, 0xf2b13a, 0xf27a2e, 0xe0432a, 0xb0121c, 0x3a0a0f]

def gate_icon(n):
    c = canvas(); col = rgb(GATE_COLORS[n - 1]); frame = rgb(0x6b2e1e) if n < 8 else rgb(0x1a0508)
    rect(c, 5, 6, 10, 14, col)
    for k in range(n): px(c, 6 + k % 4, 13 - k // 4 * 2, WHITE if n < 8 else RED)
    rect(c, 3, 5, 4, 14, frame); rect(c, 11, 5, 12, 14, frame)
    rect(c, 1, 2, 14, 3, frame); rect(c, 2, 4, 13, 4, frame)
    if n == 8:
        for x in range(5, 11): px(c, x, 6, rgb(0x7a0a14))
    return c

GATES = {"gate_%d" % n: (lambda n=n: gate_icon(n)) for n in range(1, 9)}

# ---------- animation support ----------
def write_animated(path, frames, frametime):
    h = len(frames[0]); w = len(frames[0][0])
    strip = [row for fr in frames for row in fr]
    write_png(path, strip)
    json.dump({"animation": {"frametime": frametime}}, open(path + ".mcmeta", "w"))

def write_item(ns, name, handheld=False):
    parent = "minecraft:item/handheld" if handheld else "minecraft:item/generated"
    os.makedirs(os.path.join(ns, "models", "item"), exist_ok=True)
    json.dump({"parent": parent, "textures": {"layer0": "shinobi:item/" + name}},
              open(os.path.join(ns, "models", "item", name + ".json"), "w"), indent=2)
    os.makedirs(os.path.join(ns, "items"), exist_ok=True)
    json.dump({"model": {"type": "minecraft:model", "model": "shinobi:item/" + name}},
              open(os.path.join(ns, "items", name + ".json"), "w"), indent=2)

# existing items that get a subtle animation
def anim_chakra_paper(f):
    c = chakra_paper(); col = [LBLUE, rgb(0xcfeaff), WHITE, rgb(0xcfeaff)][f % 4]
    for i in range(2, 14, 2): px(c, 3, i, col if (i // 2 + f) % 2 == 0 else T); px(c, 12, i, col if (i // 2 + f) % 2 else T)
    return c

def anim_ryo(f):
    c = ryo(); x = 4 + f * 2
    px(c, x, 11 - f, WHITE); px(c, x + 1, 10 - f, WHITE)
    return c

def anim_chakra_blade(f):
    c = chakra_blade(); col = [WHITE, rgb(0xcfeaff), LBLUE, rgb(0xcfeaff)][f % 4]
    line(c, 4, 11, 12, 3, col)
    px(c, 14, 1 + f % 2, WHITE); px(c, 2 + f, 13, LBLUE)
    return c

def anim_smoke_bomb(f):
    c = smoke_bomb(); c[1][11] = T; c[2][11] = T
    px(c, 11, 1 + (f % 2), rgb(0xff9b2f)); px(c, 12, 2 - (f % 2), YELLOW)
    return c

def anim_explosive_tag(f):
    c = explosive_tag(); col = [RED, rgb(0xff6a3a), RED, rgb(0xa01a12)][f % 4]
    for y in (4, 6, 8, 10): rect(c, 6, y, 9, y, col)
    return c

def anim_sealing_scroll(f):
    c = sealing_scroll(); col = [LBLUE, WHITE, LBLUE, rgb(0x4fb0f0)][f % 4]
    disc(c, 8, 8, 2, col)
    return c

def anim_eyes_item(f):
    return eternal_mangekyo(f)

ANIMATED_ITEMS = {"chakra_paper": (anim_chakra_paper, 4, 4), "ryo": (anim_ryo, 6, 4), "chakra_blade": (anim_chakra_blade, 4, 3),
                  "smoke_bomb": (anim_smoke_bomb, 4, 3), "explosive_tag": (anim_explosive_tag, 4, 4),
                  "sealing_scroll": (anim_sealing_scroll, 4, 5), "eternal_eyes": (anim_eyes_item, 8, 4)}

# ---------- HUD glyph sheets ----------
def sheet_of(cells):
    sheet = canvas(16 * len(cells), 16)
    for i, cell in enumerate(cells):
        for y in range(16):
            for x in range(16):
                sheet[y][16 * i + x] = cell[y][x]
    return sheet

def build_v2(ns):
    ft = os.path.join(ns, "textures", "item")
    # static items
    for name, fn in list(MISSION_ICONS.items()) + list(CLANS.items()) + list(GATES.items()):
        write_png(os.path.join(ft, name + ".png"), fn()); write_item(ns, name)
    # animated eyes (items) and the static plain eye
    write_png(os.path.join(ft, "eye_none.png"), plain_eye()); write_item(ns, "eye_none")
    for name, (fn, n, t) in ANIMATED_EYES.items():
        write_animated(os.path.join(ft, name + ".png"), [fn(i) for i in range(n)], t); write_item(ns, name)
    # natures
    for name, fn, n, t in NATURES:
        write_animated(os.path.join(ft, "nature_" + name + ".png"), [fn(i) for i in range(n)], t)
        write_item(ns, "nature_" + name)
    # per-jutsu scrolls
    for jid, (col, sym) in JUTSU_ART.items():
        write_animated(os.path.join(ft, "scroll_" + jid + ".png"), [jutsu_scroll_frame(col, sym, i) for i in range(4)], 5)
        write_item(ns, "scroll_" + jid)
    # animate selected existing items (overwrites their static textures)
    for name, (fn, n, t) in ANIMATED_ITEMS.items():
        write_animated(os.path.join(ft, name + ".png"), [fn(i) for i in range(n)], t)
    # HUD glyph sheets
    eyes = eyes_static()
    fd = os.path.join(ns, "textures", "font")
    write_png(os.path.join(fd, "eyes.png"), sheet_of([eyes[k] for k in EYE_ORDER]))
    write_png(os.path.join(fd, "natures.png"), sheet_of([fn(0) for _, fn, _, _ in NATURES]))
    write_png(os.path.join(fd, "gates.png"), sheet_of([gate_icon(n) for n in range(1, 9)]))
    providers = [
        {"type": "bitmap", "file": "shinobi:font/eyes.png", "ascent": 10, "height": 12,
         "chars": ["".join(chr(0xE200 + i) for i in range(len(EYE_ORDER)))]},
        {"type": "bitmap", "file": "shinobi:font/natures.png", "ascent": 10, "height": 12,
         "chars": ["".join(chr(0xE300 + i) for i in range(len(NATURES)))]},
        {"type": "bitmap", "file": "shinobi:font/gates.png", "ascent": 10, "height": 12,
         "chars": ["".join(chr(0xE400 + i) for i in range(8))]},
    ]
    return providers

# ---------- build ----------
def main():
    ns = os.path.join(ROOT, "assets", "shinobi")
    for name, fn in ITEMS.items():
        write_png(os.path.join(ns, "textures", "item", name + ".png"), fn())
        write_item(ns, name, name in HANDHELD)

    write_png(os.path.join(ns, "textures", "font", "seals.png"), seals_sheet())
    write_png(os.path.join(ns, "textures", "font", "bar.png"), bar_sheet())
    seal_chars = "".join(chr(0xE000 + i) for i in range(len(SEALS)))
    extra_providers = build_v2(ns)
    os.makedirs(os.path.join(ns, "font"), exist_ok=True)
    json.dump({"providers": [
        {"type": "bitmap", "file": "shinobi:font/seals.png", "ascent": 10, "height": 12, "chars": [seal_chars]},
        {"type": "bitmap", "file": "shinobi:font/bar.png", "ascent": 10, "height": 12, "chars": ["\ue100\ue101"]},
    ] + extra_providers}, open(os.path.join(ns, "font", "hud.json"), "w"), indent=2)

    if PACK_FORMAT >= 69:   # 1.21.9 and later
        meta = {"pack": {"description": "ShinobiSMP resources", "min_format": PACK_FORMAT, "max_format": 99}}
    else:
        meta = {"pack": {"pack_format": PACK_FORMAT, "description": "ShinobiSMP resources"}}
    json.dump(meta, open(os.path.join(ROOT, "pack.mcmeta"), "w"), indent=2)

    # Deterministic zip (fixed timestamps, sorted names) so the SHA-1 only changes when the art changes.
    zpath = os.path.abspath(os.path.join(ROOT, "..", "ShinobiPack.zip"))
    files = []
    for folder, _, fs in os.walk(ROOT):
        for f in fs:
            files.append(os.path.join(folder, f))
    files.sort()
    with zipfile.ZipFile(zpath, "w") as z:
        for full in files:
            rel = os.path.relpath(full, ROOT).replace(os.sep, "/")
            zi = zipfile.ZipInfo(rel, date_time=(2020, 1, 1, 0, 0, 0))
            zi.compress_type = zipfile.ZIP_DEFLATED
            zi.external_attr = 0o644 << 16
            z.writestr(zi, open(full, "rb").read())
    sha1 = hashlib.sha1(open(zpath, "rb").read()).hexdigest()
    print("wrote", zpath)
    print("sha1 ", sha1)


if __name__ == "__main__":
    main()
