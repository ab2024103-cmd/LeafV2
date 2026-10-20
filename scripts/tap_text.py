#!/usr/bin/env python3
"""Tap a node in a uiautomator XML dump by (partial) text or content-description.

usage: tap_text.py <dump.xml> <needle> [index]
Exits non-zero when nothing matches so the caller can continue gracefully.
"""
import re
import subprocess
import sys

BOUNDS = re.compile(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: tap_text.py <dump.xml> <needle> [index]")
        return 2
    path, needle = sys.argv[1], sys.argv[2].lower()
    index = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    try:
        with open(path, "r", encoding="utf-8", errors="ignore") as handle:
            data = handle.read()
    except OSError as error:
        print(f"cannot read {path}: {error}")
        return 1

    hits = []
    for node in re.finditer(r"<node[^>]*>", data):
        tag = node.group(0)
        text = " ".join(
            value
            for value in re.findall(r'(?:text|content-desc)="([^"]*)"', tag)
            if value
        ).lower()
        if needle in text:
            match = BOUNDS.search(tag)
            if match:
                x1, y1, x2, y2 = (int(g) for g in match.groups())
                hits.append((( x1 + x2) // 2, (y1 + y2) // 2, text[:60]))
    if not hits:
        print(f"no node matching '{needle}'")
        return 1
    if index >= len(hits):
        print(f"only {len(hits)} matches for '{needle}'")
        return 1
    x, y, label = hits[index]
    print(f"tap '{label}' at {x},{y}")
    subprocess.run(["adb", "shell", "input", "tap", str(x), str(y)], check=False)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
