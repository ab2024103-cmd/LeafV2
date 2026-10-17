#!/usr/bin/env python3
"""Generate Leaf launcher icons (legacy + adaptive foreground) as PNGs.

Pure-python PNG writer (zlib + struct) so no external imaging deps are needed.
The leaf mark: a lens/leaf shape (|x| <= k*y*(1-y)) with a midrib and stem.
"""
import math
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

LEAF = (0x8F, 0xD3, 0xB0)
LEAF_DARK = (0x1B, 0x54, 0x3E)
CREAM = (0xF3, 0xF1, 0xE7)
GREEN = (0x2B, 0x7A, 0x5B)
GREEN_DARK = (0x1C, 0x55, 0x3F)


def write_png(path, width, height, pixels):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            r, g, b, a = pixels[y][x]
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", header)
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(png)


def leaf_alpha(nx, ny):
    """Signed coverage of the leaf lens shape in normalised unit space."""
    # leaf points up: tip at (0,0), base at (0,1)
    x = abs(nx)
    y = ny
    if y < 0.0 or y > 1.0:
        return 0.0
    half = 0.46 * y * (1.0 - y) * 4.0 * 0.25 * 4.0
    half = 0.46 * (4.0 * y * (1.0 - y))
    if x <= half:
        return 1.0
    edge = half * 0.16
    if x <= half + edge:
        return 1.0 - (x - half) / edge
    return 0.0


def blend(dst, src, a):
    return (
        int(dst[0] * (1 - a) + src[0] * a),
        int(dst[1] * (1 - a) + src[1] * a),
        int(dst[2] * (1 - a) + src[2] * a),
    )


def rounded_rect_alpha(nx, ny, radius):
    # unit square with rounded corners
    cx = min(max(nx, radius), 1 - radius)
    cy = min(max(ny, radius), 1 - radius)
    d = math.hypot(nx - cx, ny - cy)
    if d <= radius:
        return 1.0
    if d <= radius + 0.02:
        return 1.0 - (d - radius) / 0.02
    return 0.0


def make_legacy(size):
    px = [[(0, 0, 0, 0)] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            nx = (x + 0.5) / size
            ny = (y + 0.5) / size
            bg_a = rounded_rect_alpha(nx, ny, 0.22)
            if bg_a <= 0:
                continue
            # vertical gradient background
            t = ny
            col = (
                int(GREEN[0] * (1 - t) + GREEN_DARK[0] * t),
                int(GREEN[1] * (1 - t) + GREEN_DARK[1] * t),
                int(GREEN[2] * (1 - t) + GREEN_DARK[2] * t),
            )
            # leaf body
            lx = (nx - 0.5) * 1.0
            ly = (ny - 0.14) / 0.72
            a = leaf_alpha(lx, ly)
            if a > 0:
                col = blend(col, LEAF, a)
                # midrib
                if abs(lx) < 0.018 and 0.0 <= ly <= 1.0:
                    col = blend(col, LEAF_DARK, 0.75)
                # veins
                v = abs((lx * 3.0) - (ly - 0.5) * 0.9)
                if abs(lx) > 0.05 and 0.12 < ly < 0.92:
                    vv = abs(((lx - 0.5) * 3.0) - (ly - 0.55) * 0.8)
                    if vv < 0.02:
                        col = blend(col, LEAF_DARK, 0.45)
            px[y][x] = (col[0], col[1], col[2], int(255 * bg_a))
    return px


def make_foreground(size):
    """Adaptive foreground: leaf inside the 66% safe zone, transparent outside."""
    px = [[(0, 0, 0, 0)] * size for _ in range(size)]
    scale = size / 108.0
    for y in range(size):
        for x in range(size):
            u = x / scale
            v = y / scale
            lx = (u - 54.0) / 30.0
            ly = (v - 24.0) / 60.0
            a = leaf_alpha(lx, ly)
            if a <= 0:
                continue
            col = LEAF
            if abs(lx) < 0.03 and 0.0 <= ly <= 1.0:
                col = LEAF_DARK
            px[y][x] = (col[0], col[1], col[2], int(255 * a))
    return px


def main():
    legacy = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in legacy.items():
        write_png(os.path.join(RES, folder, "ic_launcher.png"), size, size, make_legacy(size))
        write_png(os.path.join(RES, folder, "ic_launcher_round.png"), size, size, make_legacy(size))

    adaptive = {
        "mipmap-mdpi": 108,
        "mipmap-hdpi": 162,
        "mipmap-xhdpi": 216,
        "mipmap-xxhdpi": 324,
        "mipmap-xxxhdpi": 432,
    }
    for folder, size in adaptive.items():
        write_png(os.path.join(RES, folder, "ic_launcher_foreground.png"), size, size, make_foreground(size))
    print("icons written")


if __name__ == "__main__":
    main()
