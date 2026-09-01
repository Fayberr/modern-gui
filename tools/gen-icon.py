"""Generates assets/faybergui/icon.png: the mod icon.

The design is the library's own widget catalog, composed on a dark square
card in a 2x2 arrangement: an ON pill toggle top left, a primary button top
right, and a slider spanning the full width along the bottom. Colours are
the dark Theme palette (card #1A1A1A, border #3A3A3A, text #F0F0F0, accent
#E6E6E6), so the icon is literally the UI it ships. The card is a plain
square: Modrinth and ModMenu round the corners themselves, and a baked-in
radius shows up as a weird double edge on the site. Regenerate offline with:

    python3 tools/gen-icon.py [out.png]

Requires Pillow. Renders at 8x supersampling and downscales with Lanczos
for the anti-aliased physical-pixel look the library is named for.
"""
import sys

from PIL import Image, ImageDraw

S = 8  # supersample factor
W = 128 * S

# Theme DARK palette (see net.fayber.faybergui.render.Theme).
CARD = (26, 26, 26, 255)        # #1A1A1A
BORDER = (58, 58, 58, 255)      # #3A3A3A
TEXT = (240, 240, 240, 255)     # #F0F0F0
ACCENT = (230, 230, 230, 255)   # #E6E6E6
TRACK = (77, 77, 77, 255)       # #4D4D4D
KNOB = (18, 18, 18, 255)        # #121212 (textOnAccent)

P = 18 * S          # inner padding
RIGHT = W - P

img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
d = ImageDraw.Draw(img)

# Card.
d.rectangle([0, 0, W - 1, W - 1], fill=CARD, outline=BORDER, width=2 * S)

# Top left: pill toggle, ON state (light pill, dark knob at the right end).
t_l, t_t, t_r, t_b = P, 22 * S, P + 46 * S, 48 * S
d.rounded_rectangle([t_l, t_t, t_r, t_b], radius=(t_b - t_t) // 2, fill=ACCENT)
kr = 9 * S
d.ellipse([t_r - 4 * S - 2 * kr, (t_t + t_b) // 2 - kr, t_r - 4 * S, (t_t + t_b) // 2 + kr],
          fill=KNOB)

# Top right: button (plain rounded rect, the accent-filled primary style).
b_l, b_t, b_r, b_b = W - P - 40 * S, 24 * S, RIGHT, 46 * S
d.rounded_rectangle([b_l, b_t, b_r, b_b], radius=8 * S, fill=TEXT)

# Bottom: slider spanning the full inner width.
s_cy = 92 * S
track_h = 10 * S
knob_cx = P + 60 * S
knob_r = 13 * S
# Unfilled track, then the filled portion up to the knob.
d.rounded_rectangle([P, s_cy - track_h // 2, RIGHT, s_cy + track_h // 2],
                    radius=track_h // 2, fill=TRACK)
d.rounded_rectangle([P, s_cy - track_h // 2, knob_cx, s_cy + track_h // 2],
                    radius=track_h // 2, fill=ACCENT)
# Knob: light circle with a dark ring so it separates from the fill.
d.ellipse([knob_cx - knob_r, s_cy - knob_r, knob_cx + knob_r, s_cy + knob_r],
          fill=TEXT, outline=CARD, width=3 * S)

img = img.resize((128, 128), Image.LANCZOS)
out = sys.argv[1] if len(sys.argv) > 1 else \
    "src/main/resources/assets/faybergui/icon.png"
img.save(out, "PNG", optimize=True)
print("wrote", out)