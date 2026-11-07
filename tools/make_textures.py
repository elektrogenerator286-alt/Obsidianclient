"""Generates the smooth (anti-aliased) rounded GUI textures for Pulse HUD.

Everything is drawn at 4x and downsampled with LANCZOS so the edges are truly
round, not stair-stepped.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

OUT = sys.argv[1]
SS = 4  # supersampling factor


def sdf_rounded_rect(w, h, radius):
    """Signed distance (in px, negative inside) of a rounded rect filling the image."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    xs += 0.5
    ys += 0.5
    cx, cy = w / 2.0, h / 2.0
    qx = np.abs(xs - cx) - (w / 2.0 - radius)
    qy = np.abs(ys - cy) - (h / 2.0 - radius)
    outside = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0))
    inside = np.minimum(np.maximum(qx, qy), 0)
    return outside + inside - radius


def build_panel(size, radius, stroke, fill_alpha, glow_px, glow_alpha, tint=(0, 0, 0)):
    """Dark rounded panel with a white outline and a faint inner glow."""
    s = size * SS
    d = sdf_rounded_rect(s, s, radius * SS) / SS  # distance in final px

    # coverage of the whole shape (1 inside, 0 outside, soft over ~1 final px)
    cover = np.clip(0.5 - d, 0, 1)

    # stroke band: from the edge inward by `stroke` px
    band = np.clip(0.5 - d, 0, 1) * np.clip(d + stroke + 0.5, 0, 1)

    # inner glow: fades from the stroke inward
    glow = np.clip(1 - (-(d + stroke)) / glow_px, 0, 1) * (d < -stroke)
    glow = glow * glow * glow_alpha

    rgb = np.zeros((s, s, 3), np.float32)
    rgb[...] = np.array(tint, np.float32) / 255.0

    # composite: fill (dark), then glow (white), then stroke (white)
    a_fill = cover * fill_alpha
    a_glow = cover * glow
    a_stroke = band * 0.92

    # straight-alpha compositing of three layers
    out_a = a_fill + a_glow * (1 - a_fill)
    out_rgb = (rgb * a_fill[..., None] * (1 - a_glow[..., None])
               + 1.0 * a_glow[..., None])
    out_rgb = np.where(out_a[..., None] > 0, out_rgb / np.maximum(out_a[..., None], 1e-6), 0)

    out_a2 = a_stroke + out_a * (1 - a_stroke)
    out_rgb2 = (1.0 * a_stroke[..., None] + out_rgb * out_a[..., None] * (1 - a_stroke[..., None]))
    out_rgb2 = np.where(out_a2[..., None] > 0, out_rgb2 / np.maximum(out_a2[..., None], 1e-6), 0)

    img = np.dstack([np.clip(out_rgb2, 0, 1), np.clip(out_a2, 0, 1)])
    im = Image.fromarray((img * 255).astype(np.uint8), "RGBA")
    return im.resize((size, size), Image.LANCZOS)


def build_slot(size, radius):
    """Soft translucent white rounded square used as the selected-slot highlight / bars."""
    s = size * SS
    d = sdf_rounded_rect(s, s, radius * SS) / SS
    cover = np.clip(0.5 - d, 0, 1)
    edge = np.clip(0.5 - d, 0, 1) * np.clip(d + 1.4 + 0.5, 0, 1)
    a = np.clip(cover * 0.22 + edge * 0.55, 0, 1)
    img = np.dstack([np.ones((s, s, 3), np.float32), a])
    im = Image.fromarray((img * 255).astype(np.uint8), "RGBA")
    return im.resize((size, size), Image.LANCZOS)


def build_bar(w, h):
    """Solid white pill (w x h, fully round ends). Drawn in 3 parts by Gfx.bar()."""
    sw_, sh_ = w * SS, h * SS
    d = sdf_rounded_rect(sw_, sh_, (h / 2) * SS) / SS
    a = np.clip(0.5 - d, 0, 1)
    img = np.dstack([np.ones((sh_, sw_, 3), np.float32), a])
    im = Image.fromarray((img * 255).astype(np.uint8), "RGBA")
    return im.resize((w, h), Image.LANCZOS)


def build_dot(size):
    """Soft white disc for particles."""
    s = size * SS
    ys, xs = np.mgrid[0:s, 0:s].astype(np.float32)
    r = np.hypot(xs + 0.5 - s / 2, ys + 0.5 - s / 2) / SS
    R = size / 2 - 0.5
    a = np.clip(R - r + 0.5, 0, 1)
    # tiny soft halo so a 2px dot does not look like a hard square
    halo = np.clip(1 - r / (size / 2), 0, 1) ** 2 * 0.35
    a = np.clip(np.maximum(a, halo), 0, 1)
    img = np.dstack([np.ones((s, s, 3), np.float32), a])
    im = Image.fromarray((img * 255).astype(np.uint8), "RGBA")
    return im.resize((size, size), Image.LANCZOS)


def save(im, name):
    path = os.path.join(OUT, name)
    im.save(path)
    with open(path + ".mcmeta", "w") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f)
    print("wrote", path, im.size)


os.makedirs(OUT, exist_ok=True)

# 9-slice panel: 64x64, corner radius 16 -> corners are 16x16, middle is 32x32
save(build_panel(64, 16, 2.2, 0.80, 7.0, 0.20), "panel.png")
# Circle for the off-hand slot (drawn ~1:1 at 26px, texture is 32px)
save(build_panel(32, 16, 1.7, 0.80, 6.0, 0.20), "circle.png")
# Highlight / progress-bar pill
save(build_slot(32, 9), "slot.png")
# Solid progress bar pill
save(build_bar(64, 16), "bar.png")
# Particle dot
save(build_dot(16), "dot.png")
