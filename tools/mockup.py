"""Draws a mock-up of the Pulse HUD using the real mod textures.

Layout numbers are copied from Hud.java (GUI units); S pixels per GUI unit.
Item icons are simple stand-ins, Minecraft's own item icons are drawn in-game.
"""
import random
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

TEX = "/home/claude/pulsehud/src/main/resources/assets/pulsehud/textures/gui/"
OUT = sys.argv[1]
S = 3
SW, SH = 480, 270  # 1080p at GUI scale 4

panel = Image.open(TEX + "panel.png").convert("RGBA")
circle = Image.open(TEX + "circle.png").convert("RGBA")
slot = Image.open(TEX + "slot.png").convert("RGBA")
dot = Image.open(TEX + "dot.png").convert("RGBA")
bar = Image.open(TEX + "bar.png").convert("RGBA")
FONT = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 8 * S + 1)
FONT_S = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", int(8 * S * 0.78))
random.seed(7)


def U(v):
    return int(round(v * S))


# ------------------------------------------------------------------ background (simple scene)
W, H = SW * S, SH * S
yy = np.linspace(0, 1, H)[:, None, None]
sky_top = np.array([88, 150, 230], np.float32)
sky_bot = np.array([178, 214, 245], np.float32)
bg = (sky_top * (1 - yy) + sky_bot * yy).repeat(W, axis=1)
img = Image.fromarray(bg.astype(np.uint8), "RGB").convert("RGBA")
d = ImageDraw.Draw(img)
horizon = int(H * 0.60)
d.rectangle([0, horizon, W, H], fill=(96, 148, 62, 255))
for i in range(0, W, U(16)):
    shade = 8 if (i // U(16)) % 2 else 0
    d.rectangle([i, horizon, i + U(16), horizon + U(10)], fill=(88 + shade, 140 + shade, 56, 255))
for bx, bw, bh in [(60, 5, 3), (330, 4, 2), (380, 6, 4), (20, 3, 2)]:
    d.rectangle([U(bx * 1.0), horizon - U(bh * 8), U(bx + bw * 8), horizon], fill=(110, 110, 120, 255))
d.rectangle([0, horizon + U(10), W, H], fill=(121, 85, 58, 255))


def paste(im, pos):
    img.alpha_composite(im, pos)


# ------------------------------------------------------------------ 9-slice (same maths as Gfx.panel)
def nine(w, h, wanted):
    r = max(2, min(wanted, (min(w, h) - 1) // 2))
    rp = U(r)
    wp, hp = U(w), U(h)
    out = Image.new("RGBA", (wp, hp), (0, 0, 0, 0))

    def put(box, size, pos):
        if size[0] > 0 and size[1] > 0:
            out.alpha_composite(panel.crop(box).resize(size, Image.LANCZOS), pos)

    put((0, 0, 16, 16), (rp, rp), (0, 0))
    put((48, 0, 64, 16), (rp, rp), (wp - rp, 0))
    put((0, 48, 16, 64), (rp, rp), (0, hp - rp))
    put((48, 48, 64, 64), (rp, rp), (wp - rp, hp - rp))
    put((16, 0, 48, 16), (wp - 2 * rp, rp), (rp, 0))
    put((16, 48, 48, 64), (wp - 2 * rp, rp), (rp, hp - rp))
    put((0, 16, 16, 48), (rp, hp - 2 * rp), (0, rp))
    put((48, 16, 64, 48), (rp, hp - 2 * rp), (wp - rp, rp))
    put((16, 16, 48, 48), (wp - 2 * rp, hp - 2 * rp), (rp, rp))
    return out


def tinted(im, rgba):
    r, g, b, a = rgba
    arr = np.array(im, np.float32)
    arr[..., 0] *= r / 255
    arr[..., 1] *= g / 255
    arr[..., 2] *= b / 255
    arr[..., 3] *= a / 255
    return Image.fromarray(arr.astype(np.uint8), "RGBA")


def text(x, y, s, color=(255, 255, 255), font=FONT):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    dl = ImageDraw.Draw(layer)
    dl.text((U(x) + S, U(y) + S), s, font=font, fill=(0, 0, 0, 150))      # shadow
    dl.text((U(x), U(y)), s, font=font, fill=color + (255,))
    img.alpha_composite(layer)


def twidth(s, font=FONT):
    return font.getlength(s) / S


# ------------------------------------------------------------------ item stand-ins (16x16 GUI units)
def icon(kind, x, y, scale=1.0):
    n = U(16 * scale)
    ss = 4
    t = Image.new("RGBA", (n * ss, n * ss), (0, 0, 0, 0))
    dt = ImageDraw.Draw(t)
    u = n * ss / 16
    if kind == "sword":
        dt.line([(3 * u, 13 * u), (13 * u, 3 * u)], fill=(210, 225, 235, 255), width=int(2.4 * u))
        dt.line([(4 * u, 9 * u), (7 * u, 12 * u)], fill=(120, 90, 50, 255), width=int(1.6 * u))
        dt.line([(2 * u, 14 * u), (4.5 * u, 11.5 * u)], fill=(100, 70, 40, 255), width=int(1.8 * u))
    elif kind == "pick":
        dt.line([(3 * u, 13 * u), (12 * u, 4 * u)], fill=(120, 90, 50, 255), width=int(1.6 * u))
        dt.arc([2 * u, 1 * u, 14 * u, 13 * u], 215, 325, fill=(90, 230, 220, 255), width=int(2.4 * u))
    elif kind == "apple":
        dt.ellipse([2.5 * u, 3.5 * u, 13.5 * u, 14.5 * u], fill=(235, 190, 40, 255))
        dt.ellipse([4 * u, 5 * u, 12 * u, 13 * u], fill=(245, 215, 90, 255))
        dt.line([(8 * u, 3.5 * u), (9.5 * u, 1.5 * u)], fill=(90, 140, 50, 255), width=int(1.3 * u))
    elif kind == "pearl":
        dt.ellipse([2.5 * u, 2.5 * u, 13.5 * u, 13.5 * u], fill=(30, 110, 100, 255))
        dt.ellipse([4.5 * u, 4.5 * u, 11.5 * u, 11.5 * u], fill=(70, 190, 170, 255))
        dt.ellipse([5.5 * u, 5.5 * u, 8 * u, 8 * u], fill=(190, 255, 240, 255))
    elif kind == "shield":
        dt.polygon([(3 * u, 2 * u), (13 * u, 2 * u), (13 * u, 9 * u), (8 * u, 14.5 * u), (3 * u, 9 * u)],
                   fill=(150, 105, 60, 255))
        dt.polygon([(5 * u, 4 * u), (11 * u, 4 * u), (11 * u, 8.5 * u), (8 * u, 12 * u), (5 * u, 8.5 * u)],
                   fill=(190, 190, 200, 255))
    elif kind == "block":
        dt.rectangle([2 * u, 2 * u, 14 * u, 14 * u], fill=(125, 125, 130, 255), outline=(70, 70, 76, 255),
                     width=int(1 * u))
    elif kind == "bow":
        dt.arc([2 * u, 1 * u, 14 * u, 15 * u], 270, 90, fill=(130, 90, 50, 255), width=int(1.8 * u))
        dt.line([(8 * u, 1.5 * u), (8 * u, 14.5 * u)], fill=(230, 230, 230, 255), width=int(0.7 * u))
    elif kind == "food":
        dt.ellipse([2 * u, 5 * u, 14 * u, 13 * u], fill=(200, 120, 70, 255))
        dt.ellipse([4 * u, 6 * u, 12 * u, 11 * u], fill=(225, 150, 95, 255))
    elif kind == "helmet":
        dt.rounded_rectangle([3 * u, 3 * u, 13 * u, 11 * u], radius=int(2.5 * u), fill=(90, 220, 215, 255))
        dt.rectangle([4.5 * u, 8 * u, 11.5 * u, 10.5 * u], fill=(0, 0, 0, 0))
    elif kind == "chest":
        dt.polygon([(2 * u, 3 * u), (6 * u, 2 * u), (10 * u, 2 * u), (14 * u, 3 * u), (13 * u, 9 * u),
                    (12 * u, 14 * u), (4 * u, 14 * u), (3 * u, 9 * u)], fill=(90, 220, 215, 255))
    elif kind == "legs":
        dt.polygon([(3 * u, 2 * u), (13 * u, 2 * u), (13 * u, 14 * u), (9 * u, 14 * u), (8 * u, 7 * u),
                    (7 * u, 14 * u), (3 * u, 14 * u)], fill=(90, 220, 215, 255))
    elif kind == "boots":
        dt.polygon([(3 * u, 4 * u), (7 * u, 4 * u), (7 * u, 10 * u), (10 * u, 13 * u), (3 * u, 13 * u)],
                   fill=(90, 220, 215, 255))
        dt.polygon([(9 * u, 4 * u), (13 * u, 4 * u), (13 * u, 13 * u), (8 * u, 13 * u)], fill=(90, 220, 215, 255))
    t = t.resize((n, n), Image.LANCZOS)
    shadow = Image.new("RGBA", t.size, (0, 0, 0, 0))
    sh_a = np.array(t)[..., 3] * 0.35
    shadow_arr = np.zeros((n, n, 4), np.uint8)
    shadow_arr[..., 3] = sh_a.astype(np.uint8)
    shadow = Image.fromarray(shadow_arr, "RGBA")
    paste(shadow, (U(x) + S, U(y) + S))
    paste(t, (U(x), U(y)))


def draw_bar(x, y, w, h, alpha=255):
    wp, hp = U(w), U(h)
    cap = max(1, min(hp // 2, wp // 2))
    out = Image.new("RGBA", (wp, hp), (0, 0, 0, 0))
    out.alpha_composite(bar.crop((0, 0, 8, 16)).resize((cap, hp), Image.LANCZOS), (0, 0))
    out.alpha_composite(bar.crop((56, 0, 64, 16)).resize((cap, hp), Image.LANCZOS), (wp - cap, 0))
    if wp - 2 * cap > 0:
        out.alpha_composite(bar.crop((8, 0, 56, 16)).resize((wp - 2 * cap, hp), Image.LANCZOS), (cap, 0))
    paste(tinted(out, (255, 255, 255, alpha)), (U(x), U(y)))


# ------------------------------------------------------------------ particles
def particles_around(x, y, w, h, count):
    for _ in range(count):
        pos = random.random() * 2 * (w + h)
        if pos < w:
            px, py, nx, ny = x + pos, y, 0, -1
        elif pos < w + h:
            px, py, nx, ny = x + w, y + (pos - w), 1, 0
        elif pos < 2 * w + h:
            px, py, nx, ny = x + (pos - w - h), y + h, 0, 1
        else:
            px, py, nx, ny = x, y + (pos - 2 * w - h), -1, 0
        dist = random.random() * 9
        px += nx * dist + random.uniform(-2, 2)
        py += ny * dist + random.uniform(-2, 2) - dist * 0.2
        size = random.uniform(1.4, 3.6) * (1 - dist / 14)
        alpha = int(220 * (1 - dist / 10))
        n = max(2, U(size))
        pdot = tinted(dot.resize((n, n), Image.LANCZOS), (255, 255, 255, alpha))
        paste(pdot, (U(px) - n // 2, U(py) - n // 2))


# ================================================================== widgets
MARGIN, GAP = 6, 6
emitters = []

# ---- FPS and ping
s = "144 FPS   |   32 ms"
w = twidth(s) + 18
paste(nine(w, 18, 9), (U(MARGIN), U(MARGIN)))
text(MARGIN + 9, MARGIN + 5, s)
emitters.append((MARGIN, MARGIN, w, 18, 9))

# ---- potion effects
effects = [("Speed II", "3:45", (255, 255, 255), (110, 210, 255)),
           ("Strength", "0:58", (255, 255, 255), (230, 120, 60)),
           ("Weakness", "1:20", (255, 107, 107), (150, 150, 160))]
tmax = max(max(twidth(n), twidth(t)) for n, t, _, _ in effects)
padX, padY, rowH = 8, 7, 22
ew = padX * 2 + 18 + 6 + tmax
eh = padY * 2 + rowH * len(effects) - (rowH - 18)
ex, ey = SW - MARGIN - ew, MARGIN
paste(nine(ew, eh, 8), (U(ex), U(ey)))
for i, (n, t, col, ic) in enumerate(effects):
    ry = ey + padY + i * rowH
    tile = Image.new("RGBA", (U(18), U(18)), (0, 0, 0, 0))
    ImageDraw.Draw(tile).rounded_rectangle([0, 0, U(18) - 1, U(18) - 1], radius=U(4), fill=ic + (255,))
    paste(tile, (U(ex + padX), U(ry)))
    text(ex + padX + 24, ry, n, col)
    text(ex + padX + 24, ry + 9, t, (184, 184, 184))
emitters.append((ex, ey, ew, eh, 8))

# ---- cooldowns
cds = [("pearl", 0.72), ("apple", 0.35)]
barW = 44
cw = padX * 2 + 16 + 8 + barW
ch = padY * 2 + rowH * len(cds) - (rowH - 16)
cx, cy = SW - MARGIN - cw, ey + eh + GAP
paste(nine(cw, ch, 8), (U(cx), U(cy)))
for i, (kind, prog) in enumerate(cds):
    ry = cy + padY + i * rowH
    icon(kind, cx + padX, ry)
    bx, by = cx + padX + 24, ry + 6
    draw_bar(bx, by, barW, 4, 85)
    draw_bar(bx, by, max(4, barW * prog), 4)
emitters.append((cx, cy, cw, ch, 8))

# ---- hotbar
slotW, hpad, hh = 20, 8, 22
hw = slotW * 9 + hpad * 2
hx, hy = (SW - hw) // 2, SH - hh - 2
paste(nine(hw, hh, 11), (U(hx), U(hy)))
div = Image.new("RGBA", (S, U(hh - 10)), (255, 255, 255, 46))
for i in range(1, 9):
    paste(div, (U(hx + hpad + i * slotW), U(hy + 5)))
sel = 2
paste(slot.resize((U(slotW - 2), U(hh - 4)), Image.LANCZOS), (U(hx + hpad + sel * slotW + 1), U(hy + 2)))
hot = ["sword", "pick", "block", "bow", "apple", "pearl", "food", "block", None]
for i, kind in enumerate(hot):
    if kind:
        icon(kind, hx + hpad + i * slotW + 2, hy + 3)
text(hx + hpad + 2 * slotW + 12, hy + 12, "64", (255, 255, 255), FONT_S)  # stack count
emitters.append((hx, hy, hw, hh, 11))

# ---- off-hand circle
od = 26
ox, oy = hx - GAP - od, hy + (hh - od) // 2
paste(circle.resize((U(od), U(od)), Image.LANCZOS), (U(ox), U(oy)))
icon("shield", ox + 5, oy + 5)
emitters.append((ox, oy, od, od, 13))

# ---- armor durability
pieces = [("helmet", "100", None), ("chest", "87", None), ("legs", "45", (255, 211, 107)), ("boots", "12", (255, 107, 107))]
scale = 0.75
iconW = round(16 * scale)
cellGap, apad = 7, 8
cells = [iconW + 3 + round(twidth(l) * scale) for _, l, _ in pieces]
aw = sum(cells) + cellGap * (len(pieces) - 1) + apad * 2
ax = min(hx + hw + GAP, SW - MARGIN - aw)
paste(nine(aw, hh, 11), (U(ax), U(hy)))
cxx = ax + apad
small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", int(8 * S * scale) + 1)
for (kind, label, col), cwid in zip(pieces, cells):
    icon(kind, cxx, hy + (hh - iconW) / 2, scale)
    text(cxx + iconW + 3, hy + (hh - 8 * scale) / 2 - 0.3, label, col or (255, 255, 255), small)
    cxx += cwid + cellGap
emitters.append((ax, hy, aw, hh, 11))

# ---- particles last, like in the mod
for (x, y, w_, h_, _) in emitters:
    particles_around(x, y, w_, h_, int((w_ + h_) / 7))

# ---- tiny vanilla crosshair for context
cxm, cym = U(SW / 2), U(SH * 0.45)
dc = ImageDraw.Draw(img)
dc.rectangle([cxm - U(4), cym - 1, cxm + U(4), cym + 1], fill=(255, 255, 255, 230))
dc.rectangle([cxm - 1, cym - U(4), cxm + 1, cym + U(4)], fill=(255, 255, 255, 230))

img.convert("RGB").save(OUT)
print("saved", OUT, img.size)
