#!/usr/bin/env python3
"""Makes the store artwork that isn't a screenshot, from the raw captures of the screenshot
scripts, on the same sage gradient and device bezel as the framed screenshots:

    scripts/android_screenshots.sh build/raw-android
    scripts/ios_screenshots.sh build/raw-iphone
    scripts/store_art.py --android build/raw-android --ios build/raw-iphone

--android writes store-listing/google-play/en-US/feature-graphic.png (1024x500: the app name
and tagline beside two phones). --ios writes the App Store "Header and Search Results" images
(iOS 27 and later) to store-listing/app-store/en-US/: header.png (3840x1646, 21:9: three phones,
no text) and search-result.png (3840x2560, 3:2: a headline and a timer). Apple's templates keep
key artwork in a safe area, which these layouts stay inside: header x 1097-2743, y 493-1154;
search results x 836-3004, y 765-1795. The back and share buttons sit over the header's top edge.
Needs Pillow.
"""
import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
from frame_screenshots import BEZEL, FONT, FONT_BOLD_INDEX, WHITE, gradient  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
ICON = ROOT / "store-listing" / "google-play" / "icon-512.png"

HEADER_SIZE = (3840, 1646)
SEARCH_SIZE = (3840, 2560)
SEARCH_SAFE = (836, 765, 3004, 1795)
FEATURE_SIZE = (1024, 500)

# Search shows the image about 365 points wide, so the headline is short and large. "\n" is where
# it breaks.
SEARCH_HEADLINE = "Calm,\nvisual\nroutines"
TAGLINE = "Calm routines,\nbeautiful timers"


def phone(shot, screen_width):
    """The screenshot in a dark bezel with rounded corners, as RGBA."""
    bezel = round(0.0167 * screen_width)
    screen_height = round(screen_width * shot.height / shot.width)
    width, height = screen_width + 2 * bezel, screen_height + 2 * bezel
    radius = round(0.1 * width)

    body = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle((0, 0, width - 1, height - 1), radius=radius,
                                           fill=BEZEL + (255,))
    screen = shot.resize((screen_width, screen_height), Image.LANCZOS)
    mask = Image.new("L", screen.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, screen_width - 1, screen_height - 1),
                                           radius=radius - bezel, fill=255)
    body.paste(screen, (bezel, bezel), mask)
    return body


def paste_with_shadow(canvas, body, left, top):
    blur = round(0.04 * body.width)
    shadow = Image.new("RGBA", (body.width + 4 * blur, body.height + 4 * blur), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        (2 * blur, 2 * blur, 2 * blur + body.width, 2 * blur + body.height),
        radius=round(0.1 * body.width), fill=(10, 30, 20, 110))
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(shadow, (left - 2 * blur, top - 2 * blur + blur // 2))
    canvas.alpha_composite(body, (left, top))


def load_shots(raw_dir, names):
    shots = {}
    for name in names:
        matches = sorted(raw_dir.glob(f"*-{name}.png"))
        if not matches:
            sys.exit(f"no {name} screenshot in {raw_dir}")
        shots[name] = Image.open(matches[0]).convert("RGB")
    return shots


def fitted_font(lines, max_width, size):
    """The largest size up to [size] at which every line fits in max_width."""
    while True:
        font = ImageFont.truetype(FONT, size, index=FONT_BOLD_INDEX)
        if max(font.getlength(line) for line in lines) <= max_width:
            return font
        size -= 2


def draw_lines(draw, lines, font, left, top, pitch, fill=WHITE):
    for i, line in enumerate(lines):
        draw.text((left, top + i * pitch), line, font=font, fill=fill, anchor="lt")


def header(shots, out_path):
    width, height = HEADER_SIZE
    canvas = gradient(width, height).convert("RGBA")
    # The water timer in the middle, orbit and lotus a step lower on either side. They run off the
    # bottom edge; their top halves, with the dials, sit in the safe area.
    centre = phone(shots["water"], 820)
    side_width = 700
    left_phone, right_phone = phone(shots["orbit"], side_width), phone(shots["lotus"], side_width)
    gap = 110
    paste_with_shadow(canvas, left_phone, width // 2 - centre.width // 2 - gap - left_phone.width, 470)
    paste_with_shadow(canvas, right_phone, width // 2 + centre.width // 2 + gap, 470)
    paste_with_shadow(canvas, centre, width // 2 - centre.width // 2, 300)
    save(canvas, out_path)


def search_result(shots, out_path):
    width, height = SEARCH_SIZE
    safe_left, safe_top, safe_right, safe_bottom = SEARCH_SAFE
    canvas = gradient(width, height).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    # The phone runs off the bottom edge; its top, with the dial, fills the safe area's right half.
    body = phone(shots["lotus"], 1120)
    phone_left = safe_right - body.width
    paste_with_shadow(canvas, body, phone_left, 560)

    lines = SEARCH_HEADLINE.split("\n")
    font = fitted_font(lines, phone_left - 140 - safe_left, 260)
    # Centred on the safe area's height, from the first line's cap height to the last baseline.
    pitch = round(1.12 * font.size)
    cap = font.getbbox("H", anchor="ls")
    block = (len(lines) - 1) * pitch + cap[3] - cap[1]
    draw_lines(draw, lines, font, safe_left, (safe_top + safe_bottom - block) // 2, pitch)
    save(canvas, out_path)


def feature_graphic(shots, out_path):
    width, height = FEATURE_SIZE
    canvas = gradient(width, height).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    # Two phones on the right, running off the bottom edge.
    back = phone(shots["lotus"], 200)
    front = phone(shots["water"], 220)
    paste_with_shadow(canvas, back, 830 - back.width // 2 + 70, 120)
    paste_with_shadow(canvas, front, 690 - front.width // 2, 60)

    # Icon, name and tagline on the left, vertically centred.
    icon = Image.open(ICON).convert("RGBA").resize((112, 112), Image.LANCZOS)
    mask = Image.new("L", icon.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, 111, 111), radius=26, fill=255)
    left = 64
    name_font = ImageFont.truetype(FONT, 60, index=FONT_BOLD_INDEX)
    tagline = TAGLINE.split("\n")
    tag_font = fitted_font(tagline, 470, 34)
    top = 96
    canvas.paste(icon, (left, top), mask)
    draw.text((left, top + 140), "SereneFlow", font=name_font, fill=WHITE, anchor="lt")
    draw_lines(draw, tagline, tag_font, left, top + 222, round(1.25 * tag_font.size),
               fill=(232, 243, 236))
    save(canvas, out_path)


def save(canvas, out_path):
    out_path.parent.mkdir(parents=True, exist_ok=True)
    canvas.convert("RGB").save(out_path)  # no alpha channel, which App Store Connect rejects
    print(out_path.relative_to(ROOT))


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--android", type=Path, help="raw dir from android_screenshots.sh")
    parser.add_argument("--ios", type=Path, help="raw dir from ios_screenshots.sh (an iPhone)")
    args = parser.parse_args()
    if not (args.android or args.ios):
        parser.error("pass --android, --ios or both")
    if args.android:
        feature_graphic(load_shots(args.android, ["water", "lotus"]),
                        ROOT / "store-listing" / "google-play" / "en-US" / "feature-graphic.png")
    if args.ios:
        shots = load_shots(args.ios, ["water", "orbit", "lotus"])
        listing = ROOT / "store-listing" / "app-store" / "en-US"
        header(shots, listing / "header.png")
        search_result(shots, listing / "search-result.png")


if __name__ == "__main__":
    main()
