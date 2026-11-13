#!/usr/bin/env python3
"""Make a synthetic 'ChatGPT-like' sheet: sprites enlarged with smoothing on a noisy magenta
background, slightly misaligned per cell. usage: make_test_sheet.py <sprite.png 4x4> <out.png>"""
import random
import sys
import numpy as np
from PIL import Image, ImageFilter

src = Image.open(sys.argv[1]).convert("RGBA")
cw, ch = src.width // 4, src.height // 4
rnd = random.Random(3)
sheet = Image.new("RGB", (1024, 1024), (255, 0, 255))
for i in range(16):
    cell = src.crop(((i % 4) * cw, (i // 4) * ch, (i % 4) * cw + cw, (i // 4) * ch + ch))
    cell = cell.resize((200, 200), Image.BICUBIC)           # smooth edges like generated art
    dx, dy = rnd.randint(-18, 18), rnd.randint(-14, 10)      # sloppy placement
    tile = Image.new("RGB", (256, 256), (255, 0, 255))
    tile.paste(cell, (28 + dx, 40 + dy), cell)
    sheet.paste(tile, ((i % 4) * 256, (i // 4) * 256))
arr = np.asarray(sheet).astype(np.float32)
arr += np.random.default_rng(1).normal(0, 4, arr.shape)      # compression-like noise
Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.4)).save(sys.argv[2])
