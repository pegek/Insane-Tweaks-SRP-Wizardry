#!/usr/bin/env python3
"""Placeholdery tekstur zarazonego maga: tekstury zlego maga Redux (GPL-3.0, jak addon), przebarwione
w chorobliwa zielen, z plamami grzybni. Deterministyczne (staly seed), wiec wynik jest powtarzalny.

    python3 tools/infected_textures.py <jar-albo-katalog-z-zasobami-redux>
"""
import colorsys
import io
import os
import random
import sys
import zipfile

from PIL import Image

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(REPO, "src/main/resources/assets/ebreduxaddon/textures/entity/infected_wizard")
SRC = "assets/ebwizardry/textures/entity/evil_wizard/evil_wizard_%d.png"
COUNT = 6
FUNGUS = [(88, 104, 46), (120, 132, 58), (64, 74, 40), (150, 128, 96)]


def load(source, path):
    if os.path.isdir(source):
        return Image.open(os.path.join(source, path))
    with zipfile.ZipFile(source) as jar:
        return Image.open(io.BytesIO(jar.read(path)))


def infect(image, seed):
    image = image.convert("RGBA")
    px = image.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            # Barwa ciagnieta w strone zgnilej zieleni, nasycenie i jasnosc lekko w dol.
            h = (h * 0.35 + 0.22 * 0.65) % 1.0
            s = min(1.0, s * 0.75 + 0.12)
            v = v * 0.85
            r2, g2, b2 = colorsys.hsv_to_rgb(h, s, v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    rnd = random.Random(seed)
    for _ in range(image.width * image.height // 40):
        x, y = rnd.randrange(image.width), rnd.randrange(image.height)
        if px[x, y][3] == 0:
            continue
        c = rnd.choice(FUNGUS)
        px[x, y] = (c[0], c[1], c[2], px[x, y][3])
    return image


def main():
    source = sys.argv[1]
    os.makedirs(OUT, exist_ok=True)
    for i in range(COUNT):
        infect(load(source, SRC % i), 1000 + i).save(os.path.join(OUT, "infected_wizard_%d.png" % i))
    print("written %d textures to %s" % (COUNT, OUT))


if __name__ == "__main__":
    main()
