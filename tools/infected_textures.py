#!/usr/bin/env python3
"""Placeholdery tekstur zarazonego maga i Mykomanty: tekstury zlego maga Redux (GPL-3.0, jak addon),
przebarwione w chorobliwa zielen, z plamami grzybni. Mykomanta jest ciemniejsza, gesciej zarosnieta
i ma jasne narosla. Deterministyczne (staly seed), wiec wynik jest powtarzalny.

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
TEXTURES = os.path.join(REPO, "src/main/resources/assets/ebreduxaddon/textures/entity")
SRC = "assets/ebwizardry/textures/entity/evil_wizard/evil_wizard_%d.png"
COUNT = 6
FUNGUS = [(88, 104, 46), (120, 132, 58), (64, 74, 40), (150, 128, 96)]
GROWTHS = [(214, 196, 150), (190, 170, 120), (232, 220, 182)]

# nazwa: (seed, jasnosc, gestosc plam: 1 na tyle pikseli, narosla: 1 na tyle pikseli albo 0)
PROFILES = {
    "infected_wizard": (1000, 0.85, 40, 0),
    "mycomancer": (2000, 0.62, 14, 90),
}


def load(source, path):
    if os.path.isdir(source):
        return Image.open(os.path.join(source, path))
    with zipfile.ZipFile(source) as jar:
        return Image.open(io.BytesIO(jar.read(path)))


def infect(image, seed, value, density, growths):
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
            v = v * value
            r2, g2, b2 = colorsys.hsv_to_rgb(h, s, v)
            px[x, y] = (round(r2 * 255), round(g2 * 255), round(b2 * 255), a)
    rnd = random.Random(seed)
    for _ in range(image.width * image.height // density):
        x, y = rnd.randrange(image.width), rnd.randrange(image.height)
        if px[x, y][3] == 0:
            continue
        c = rnd.choice(FUNGUS)
        px[x, y] = (c[0], c[1], c[2], px[x, y][3])
    if growths:
        # Narosla: male, jasne skupiska 2x2, czytelne z daleka.
        for _ in range(image.width * image.height // growths):
            x, y = rnd.randrange(image.width - 1), rnd.randrange(image.height - 1)
            c = rnd.choice(GROWTHS)
            for dx in (0, 1):
                for dy in (0, 1):
                    if px[x + dx, y + dy][3] != 0:
                        px[x + dx, y + dy] = (c[0], c[1], c[2], px[x + dx, y + dy][3])
    return image


def main():
    source = sys.argv[1]
    for name, (seed, value, density, growths) in PROFILES.items():
        out = os.path.join(TEXTURES, name)
        os.makedirs(out, exist_ok=True)
        for i in range(COUNT):
            image = infect(load(source, SRC % i), seed + i, value, density, growths)
            image.save(os.path.join(out, "%s_%d.png" % (name, i)))
        print("written %d textures to %s" % (COUNT, out))


if __name__ == "__main__":
    main()
