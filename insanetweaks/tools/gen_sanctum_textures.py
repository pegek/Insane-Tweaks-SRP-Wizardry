"""Generates the Arcane Ladder and Arcane Ward textures (16x16 RGBA).

The ladder uses the lavender-grey palette of Ancient Spellcraft's sealed_stone (sampled colours
only, no pixels copied) with a faint purple rune line on each rung. The ward is a translucent
violet field with a brighter rune border.
"""
import os
import random
from PIL import Image

OUT = os.path.join("insanetweaks", "src", "main", "resources", "assets", "insanetweaks", "textures", "blocks")
STONE = [(154, 148, 161), (151, 144, 157), (145, 138, 152), (140, 134, 145), (158, 152, 165)]
DARK = (110, 104, 118)
RUNE = (176, 120, 230)


def ladder():
    rnd = random.Random(1337)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in (2, 3, 12, 13):
            px[x, y] = STONE[rnd.randrange(len(STONE))] + (255,)
        px[1, y] = DARK + (255,)
        px[14, y] = DARK + (255,)
    for ry in (1, 5, 9, 13):
        for x in range(1, 15):
            px[x, ry] = DARK + (255,)
            px[x, ry + 1] = STONE[rnd.randrange(len(STONE))] + (255,)
        for x in range(5, 11):
            px[x, ry + 1] = RUNE + (255,)
    im.save(os.path.join(OUT, "arcane_ladder.png"))


def ward():
    rnd = random.Random(4242)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                px[x, y] = (190, 130, 255, 170)
            else:
                v = rnd.randrange(-12, 12)
                px[x, y] = (120 + v, 60 + v, 200 + v, 90)
    for i in range(3, 13):
        px[i, i] = (210, 160, 255, 150)
        px[15 - i, i] = (210, 160, 255, 150)
    im.save(os.path.join(OUT, "arcane_ward.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    ladder()
    ward()
