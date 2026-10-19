#!/usr/bin/env python3
"""Generate the Leaf launcher icons (legacy + adaptive foreground) as PNGs.

The mark is an open book with a leaf growing out of it - the whole point of the app:
documents you read, with the leaf of the name.

Pure python PNG writer (zlib + struct) so no external imaging dependency is needed.
The shapes are rasterised scanline wise with 3x3 supersampling for smooth edges.
"""
import math
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

GREEN = (0x2B, 0x7A, 0x5B)
GREEN_DARK = (0x1B, 0x53, 0x3D)
LEAF = (0x8F, 0xD3, 0xB0)
LEAF_DARK = (0x14, 0x46, 0x33)
PAPER = (0xF6, 0xF2, 0xE6)
PAPER_SHADE = (0xDC, 0xD4, 0xBE)
INK = (0x6E, 0x8B, 0x7C)

SAMPLES = 3  # supersampling per axis


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


# ----------------------------------------------------------------- geometry helpers


def quad(p0, p1, p2, steps=20):
    """Points of a quadratic bezier from p0 to p2 with the control point p1."""
    out = []
    for i in range(steps + 1):
        t = i / steps
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
        out.append((x, y))
    return out


def thick_line(a, b, width):
    """A thin quad around the segment a-b."""
    dx, dy = b[0] - a[0], b[1] - a[1]
    length = math.hypot(dx, dy) or 1.0
    nx, ny = -dy / length * width, dx / length * width
    return [
        (a[0] + nx, a[1] + ny),
        (b[0] + nx, b[1] + ny),
        (b[0] - nx, b[1] - ny),
        (a[0] - nx, a[1] - ny),
    ]


def spans(poly, y):
    """The x intervals where the polygon covers the given y."""
    xs = []
    n = len(poly)
    j = n - 1
    for i in range(n):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > y) != (yj > y):
            xs.append((xj - xi) * (y - yi) / (yj - yi) + xi)
        j = i
    if len(xs) < 2:
        return []
    xs.sort()
    return [(xs[i], xs[i + 1]) for i in range(0, len(xs) - 1, 2)]


def in_spans(spans_list, x):
    for left, right in spans_list:
        if left <= x <= right:
            return True
    return False


def rounded_rect_alpha(nx, ny, radius, soft=0.02):
    cx = min(max(nx, radius), 1 - radius)
    cy = min(max(ny, radius), 1 - radius)
    d = math.hypot(nx - cx, ny - cy)
    if d <= radius:
        return 1.0
    if d <= radius + soft:
        return 1.0 - (d - radius) / soft
    return 0.0


# ----------------------------------------------------------------- the mark


def build_layers():
    """Every shape of the mark, painted from the back to the front."""
    # the leaf that grows out of the book
    base = (0.45, 0.57)
    tip = (0.73, 0.11)
    left = quad(base, (0.37, 0.28), tip)
    right = quad(tip, (0.77, 0.35), base)
    leaf = left + right
    midrib = thick_line(base, tip, 0.012)

    # the open book
    left_page = (
        [(0.5, 0.44), (0.5, 0.79), (0.17, 0.85), (0.13, 0.57)]
        + quad((0.13, 0.57), (0.31, 0.49), (0.5, 0.44))[1:]
    )
    right_page = (
        [(0.5, 0.44), (0.5, 0.79), (0.83, 0.85), (0.87, 0.57)]
        + quad((0.87, 0.57), (0.69, 0.49), (0.5, 0.44))[1:]
    )
    # the shaded fold next to the spine
    fold_left = [(0.5, 0.44), (0.5, 0.79), (0.455, 0.792), (0.455, 0.445)]
    fold_right = [(0.5, 0.44), (0.5, 0.79), (0.545, 0.792), (0.545, 0.445)]

    # the lines of text on both pages
    lines = []
    for y, length in ((0.60, 0.16), (0.66, 0.19), (0.72, 0.13)):
        lines.append(thick_line((0.21, y), (0.21 + length, y - 0.012), 0.009))
        lines.append(thick_line((0.79 - length, y - 0.012), (0.79, y), 0.009))

    return [
        (leaf, LEAF),
        (midrib, LEAF_DARK),
        (left_page, PAPER),
        (right_page, PAPER),
        (fold_left, PAPER_SHADE),
        (fold_right, PAPER_SHADE),
    ] + [(line, INK) for line in lines]


LAYERS = build_layers()


def render(size, foreground):
    step = 1.0 / (size * SAMPLES)
    offset = step / 2
    pixels = []
    for y in range(size):
        row = []
        for x in range(size):
            r = g = b = a = 0
            count = 0
            for sy in range(SAMPLES):
                ny = (y * SAMPLES + sy) * step + offset
                if foreground:
                    # the adaptive foreground keeps its content in the middle 66%
                    my = (ny - 0.5) / 0.62 + 0.5
                else:
                    my = ny
                layer_spans = [spans(poly, my) for poly, _ in LAYERS]
                for sx in range(SAMPLES):
                    nx = (x * SAMPLES + sx) * step + offset
                    if foreground:
                        mx = (nx - 0.5) / 0.62 + 0.5
                    else:
                        mx = nx
                    if foreground and (mx < 0 or mx > 1 or my < 0 or my > 1):
                        continue
                    col = None
                    for (poly, color), sp in zip(LAYERS, layer_spans):
                        if in_spans(sp, mx):
                            col = color
                            break
                    if foreground:
                        if col is None:
                            continue
                        r += col[0]
                        g += col[1]
                        b += col[2]
                        a += 255
                        count += 1
                    else:
                        bg_a = rounded_rect_alpha(mx, my, 0.22)
                        if bg_a <= 0:
                            continue
                        t = my
                        bg = (
                            int(GREEN[0] * (1 - t) + GREEN_DARK[0] * t),
                            int(GREEN[1] * (1 - t) + GREEN_DARK[1] * t),
                            int(GREEN[2] * (1 - t) + GREEN_DARK[2] * t),
                        )
                        if col is None:
                            col = bg
                        r += col[0]
                        g += col[1]
                        b += col[2]
                        a += int(255 * bg_a)
                        count += 1
            total = SAMPLES * SAMPLES
            if a == 0 or count == 0:
                row.append((0, 0, 0, 0))
            else:
                row.append((int(r / count), int(g / count), int(b / count), int(a / total)))
        pixels.append(row)
    return pixels


def main():
    legacy = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for folder, size in legacy.items():
        pixels = render(size, foreground=False)
        write_png(os.path.join(RES, folder, "ic_launcher.png"), size, size, pixels)
        write_png(os.path.join(RES, folder, "ic_launcher_round.png"), size, size, pixels)

    adaptive = {
        "mipmap-mdpi": 108,
        "mipmap-hdpi": 162,
        "mipmap-xhdpi": 216,
        "mipmap-xxhdpi": 324,
        "mipmap-xxxhdpi": 432,
    }
    for folder, size in adaptive.items():
        pixels = render(size, foreground=True)
        write_png(os.path.join(RES, folder, "ic_launcher_foreground.png"), size, size, pixels)
    print("icons written")


if __name__ == "__main__":
    main()
