#!/usr/bin/env python3
"""Frames raw screenshots for the stores: a caption on the sage gradient of the app icon, above
the screenshot in a dark device bezel.

    scripts/ios_screenshots.sh build/raw-iphone
    scripts/frame_screenshots.py build/raw-iphone store-listing/app-store/en-US/screenshots

    scripts/android_screenshots.sh build/raw-android
    scripts/frame_screenshots.py build/raw-android \\
        store-listing/google-play/en-US/phone-screenshots --size 1080x1920

reads <raw>/<n>-<name>.png (as the capture scripts write them, one per line of
scripts/demo/shots.txt) and writes <out>/<n>.png. Without --size each image keeps the raw
screenshot's size, which is the size App Store Connect wants for that device; Google Play wants
an aspect ratio of at most 2:1, which a modern phone's raw screenshot exceeds, hence 1080x1920
there. Needs Pillow; the captions are set in Helvetica Neue Bold from macOS.
"""
import argparse
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

FONT = "/System/Library/Fonts/HelveticaNeue.ttc"
FONT_BOLD_INDEX = 1

# One caption per shot name in scripts/demo/shots.txt. "\n" is where a caption breaks on a
# narrow (phone-shaped) canvas; wider ones put it on one line if it fits.
CAPTIONS = {
    "water": "Routines that\nflow by themselves",
    "list": "Morning, focus,\nwind-down & more",
    "bonsai": "Five living,\nanimated timers",
    "editor": "Build a routine\nin seconds",
    "orbit": "Stay in\ndeep focus",
    "lotus": "Spoken cues for\nevery step",
    "hourglass": "Glance,\ndon't count",
    "settings": "Four calming\nthemes",
}

# The app icon's gradient (tools/generate_icons.swift), from the top-left corner downwards.
GRADIENT_STOPS = [(0.0, (127, 176, 150)), (0.45, (79, 133, 106)), (1.0, (47, 93, 70))]
BEZEL = (17, 31, 24)
WHITE = (255, 255, 255)


def gradient(width, height):
    # t = x/2W + y/H: 0 at the top-left corner, 0.5 at the top-right and halfway down the left
    # edge, and 1 or more along the bottom.
    def colour(t):
        t = min(t, 1.0)
        for (t0, c0), (t1, c1) in zip(GRADIENT_STOPS, GRADIENT_STOPS[1:]):
            if t <= t1:
                f = (t - t0) / (t1 - t0)
                return tuple(round(a + (b - a) * f) for a, b in zip(c0, c1))
        return GRADIENT_STOPS[-1][1]

    # Rendered small and scaled up: the gradient is smooth, and per-pixel Python is slow.
    small = Image.new("RGB", (64, 64))
    small.putdata([colour(x / 63 / 2 + y / 63) for y in range(64) for x in range(64)])
    return small.resize((width, height), Image.BICUBIC)


def layout(width, height):
    """Sizes as fractions of the canvas: phone-shaped canvases get a two-line caption, wider ones
    (iPad, Play's 9:16) a smaller one, and landscape ones (Play tablets) one sized to the height."""
    if width > height:
        return dict(font=0.075 * height, caption_top=0.06 * height, frame_top=0.19 * height,
                    frame_height=0.76 * height, bezel=0.012 * height, radius=0.05 * height, wrap=True)
    if width / height < 0.55:
        return dict(font=0.0765 * width, caption_top=0.066 * height, frame_top=0.195 * height,
                    frame_height=0.7245 * height, bezel=0.0167 * width, radius=0.1 * width, wrap=False)
    return dict(font=0.0567 * width, caption_top=0.061 * height, frame_top=0.189 * height,
                frame_height=0.72 * height, bezel=0.0145 * width, radius=0.05 * width, wrap=True)


def caption_lines(text, font, max_width, wrap):
    if not wrap:
        return text.split("\n")
    one_line = text.replace("\n", " ")
    return [one_line] if font.getlength(one_line) <= max_width else text.split("\n")


def frame(raw_path, caption, out_path, size=None):
    shot = Image.open(raw_path).convert("RGB")
    width, height = size or shot.size
    spec = layout(width, height)
    canvas = gradient(width, height)
    draw = ImageDraw.Draw(canvas)

    font = ImageFont.truetype(FONT, round(spec["font"]), index=FONT_BOLD_INDEX)
    lines = caption_lines(caption, font, 0.86 * width, spec["wrap"])
    pitch = 1.13 * spec["font"]
    top = spec["caption_top"]
    for i, line in enumerate(lines):
        draw.text((width / 2, top + i * pitch), line, font=font, fill=WHITE, anchor="mt")
    caption_bottom = top + (len(lines) - 1) * pitch + spec["font"]

    # A caption longer than usual pushes the device down rather than into it.
    frame_top = max(spec["frame_top"], caption_bottom + 0.05 * height)
    frame_height = spec["frame_height"] - (frame_top - spec["frame_top"])
    bezel = round(spec["bezel"])
    screen_height = round(frame_height) - 2 * bezel
    screen_width = round(screen_height * shot.width / shot.height)
    frame_width = screen_width + 2 * bezel
    left = round((width - frame_width) / 2)
    frame_top = round(frame_top)

    radius = round(spec["radius"] * min(1.0, frame_width / width / 0.6))
    draw.rounded_rectangle((left, frame_top, left + frame_width, frame_top + screen_height + 2 * bezel),
                           radius=radius, fill=BEZEL)
    screen = shot.resize((screen_width, screen_height), Image.LANCZOS)
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, screen_width - 1, screen_height - 1),
                                           radius=max(radius - bezel, 0), fill=255)
    canvas.paste(screen, (left + bezel, frame_top + bezel), mask)

    out_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out_path)  # RGB, so no alpha channel, which App Store Connect rejects


def raw_shots(raw_dir):
    """[(n, name, path)] for <n>-<name>.png in raw_dir, in order."""
    shots = []
    for path in raw_dir.glob("*.png"):
        match = re.fullmatch(r"(\d+)-(\w+)\.png", path.name)
        if match:
            shots.append((int(match.group(1)), match.group(2), path))
    if not shots:
        raise SystemExit(f"no <n>-<name>.png screenshots in {raw_dir}")
    return sorted(shots)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("raw_dir", type=Path)
    parser.add_argument("out_dir", type=Path)
    parser.add_argument("--size", help="canvas WxH; defaults to each raw screenshot's size")
    args = parser.parse_args()
    size = tuple(int(n) for n in args.size.split("x")) if args.size else None
    for old in args.out_dir.glob("*.png"):
        old.unlink()
    for n, name, path in raw_shots(args.raw_dir):
        if name not in CAPTIONS:
            raise SystemExit(f"no caption for '{name}'; add it to CAPTIONS in {Path(__file__).name}")
        out = args.out_dir / f"{n}.png"
        frame(path, CAPTIONS[name], out, size)
        print(out)


if __name__ == "__main__":
    main()
