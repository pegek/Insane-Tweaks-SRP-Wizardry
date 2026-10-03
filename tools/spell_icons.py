#!/usr/bin/env python3
"""Placeholdery ikon zaklec: ikony Redux (GPL-3.0, jak addon) z przesunietym odcieniem.

Ikony z insanetweaks 1.12.2 nie nadaja sie: cleanse i purifying_pulse to emotki z wypalona
szachownica zamiast przezroczystosci, a dispatcher_grasp i yelloweye_gland to pusty szablon.

    python3 tools/spell_icons.py <jar-albo-katalog-z-zasobami-redux>

Wymaga Pillow. Zapisuje src/main/resources/assets/ebreduxaddon/textures/spells/*.png (32x32).
"""
import colorsys
import io
import os
import sys
import zipfile

from PIL import Image

# nasze zaklecie -> (ikona Redux, przesuniecie odcienia w stopniach, mnoznik nasycenia)
ICONS = {
    "cleanse": ("cure_effects", 175, 0.8),
    "purifying_pulse": ("healing_aura", 0, 1.0),
    "grasp": ("entrapment", -25, 1.1),
    "spine_volley": ("dart", -45, 1.2),
}

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "src/main/resources/assets/ebreduxaddon/textures/spells")


def load(source, name):
    path = "assets/ebwizardry/textures/spells/%s.png" % name
    if os.path.isdir(source):
        return Image.open(os.path.join(source, path))
    with zipfile.ZipFile(source) as jar:
        return Image.open(io.BytesIO(jar.read(path)))


# Ramka ikon Redux (zloto-braz, wspolna dla wszystkich) ma 3 px na 32 px - tej nie przebarwiamy.
FRAME = 3


def shift(image, degrees, saturation):
    image = image.convert("RGBA")
    if image.size != (32, 32):
        image = image.resize((32, 32), Image.LANCZOS)
    pixels = image.load()
    for y in range(FRAME, image.height - FRAME):
        for x in range(FRAME, image.width - FRAME):
            r, g, b, a = pixels[x, y]
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            h = (h + degrees / 360.0) % 1.0
            s = min(1.0, s * saturation)
            r, g, b = colorsys.hsv_to_rgb(h, s, v)
            pixels[x, y] = (round(r * 255), round(g * 255), round(b * 255), a)
    return image


def main():
    source = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    for ours, (theirs, degrees, saturation) in ICONS.items():
        icon = shift(load(source, theirs), degrees, saturation)
        icon.save(os.path.join(OUT, ours + ".png"))
        print("%s <- %s (%+d deg)" % (ours, theirs, degrees))


if __name__ == "__main__":
    main()
