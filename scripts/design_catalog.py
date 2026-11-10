#!/usr/bin/env python3
"""Builds docs/screenshots/design-catalog.png from the design-system screenshot baselines.

Every catalog section has a light and a dark baseline (app/src/test/screenshots/ds_<section>_light.png / _dark.png). Each is
scaled to a 274 px wide tile; the light tile goes in an even column and its dark twin beside it, and each pair lands under the
shortest of the four column pairs (masonry, 8 columns). Variants (1.3x text, reduced motion) and mid-animation frames are left out:
they are in the screenshot folder and would only repeat a section.

    python3 scripts/design_catalog.py            # writes docs/screenshots/design-catalog.png
    python3 scripts/design_catalog.py --check    # prints the sections it would use, writes nothing

Needs Pillow. Run it after a design-system baseline is re-recorded.
"""
import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SHOTS = ROOT / "app" / "src" / "test" / "screenshots"
OUT = ROOT / "docs" / "screenshots" / "design-catalog.png"

TILE = 274          # column width in px
GAP = 8             # space between tiles and around the edge
PAIRS = 4           # 4 light/dark column pairs = 8 columns
BACKGROUND = (238, 235, 229)
# Sections in reading order: the first ones start the columns, so the palette and type lead.
ORDER = ["colors", "type", "buttons", "chips", "status", "inputs", "amounts", "avatars", "structure", "snackbar",
         "illustrations", "states", "bottom_bar", "counters", "sheet", "icon_buttons", "skeleton", "loaders", "shape", "motion"]
SECTION = re.compile(r"^ds_(?P<name>[a-z_]+?)_(?P<theme>light|dark)\.png$")


def sections() -> list[str]:
    found: dict[str, set[str]] = {}
    for path in SHOTS.glob("ds_*.png"):
        match = SECTION.match(path.name)
        if match and not match["name"].startswith("motion_"):
            found.setdefault(match["name"], set()).add(match["theme"])
    paired = [name for name, themes in found.items() if themes == {"light", "dark"}]
    return sorted(paired, key=lambda n: (ORDER.index(n) if n in ORDER else len(ORDER), n))


def tile(image):
    from PIL import Image

    height = round(image.height * TILE / image.width)
    return image.convert("RGB").resize((TILE, height), Image.LANCZOS)


def build(names: list[str]):
    from PIL import Image

    pairs = []
    for name in names:
        light = tile(Image.open(SHOTS / f"ds_{name}_light.png"))
        dark = tile(Image.open(SHOTS / f"ds_{name}_dark.png"))
        pairs.append((light, dark))

    heights = [GAP] * PAIRS
    placed = []
    for light, dark in pairs:
        column = heights.index(min(heights))
        placed.append((column, heights[column], light, dark))
        heights[column] += max(light.height, dark.height) + GAP

    width = GAP + PAIRS * 2 * (TILE + GAP)
    canvas = Image.new("RGB", (width, max(heights)), BACKGROUND)
    for column, top, light, dark in placed:
        left = GAP + column * 2 * (TILE + GAP)
        canvas.paste(light, (left, top))
        canvas.paste(dark, (left + TILE + GAP, top))
    return canvas


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--check", action="store_true", help="list the sections, write nothing")
    args = parser.parse_args()
    names = sections()
    if not names:
        print(f"no ds_*_light/_dark pairs in {SHOTS}", file=sys.stderr)
        return 1
    print(f"{len(names)} sections: {', '.join(names)}")
    if args.check:
        return 0
    try:
        image = build(names)
    except ImportError:
        print("Pillow is missing: python3 -m venv build/venv && build/venv/bin/pip install pillow", file=sys.stderr)
        return 2
    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT, optimize=True)
    print(f"wrote {OUT.relative_to(ROOT)} {image.width}x{image.height}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
