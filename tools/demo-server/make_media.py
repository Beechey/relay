#!/usr/bin/env python3
"""Generates every image and video the demo server serves: posters, stills, channel logos and the playback clip.

Usage: python3 make_media.py   (needs Pillow; the clip also needs ffmpeg on PATH)
Everything is drawn from scratch (flat landscapes, shapes and text in Roboto Flex from the app), so there's
no third-party artwork, footage, logo or trademark anywhere in the demo.
"""
import math
import os
import random
import shutil
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

import server

HERE = os.path.dirname(os.path.abspath(__file__))
MEDIA = os.path.join(HERE, "media")
FONT = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "font", "roboto_flex.ttf")


def font(size, weight=b"Bold"):
    f = ImageFont.truetype(FONT, size)
    f.set_variation_by_name(weight)
    return f


# sky top, sky bottom, sun colour, ridge colours (back to front), extras
SCENES = {
    "the-last-lighthouse": ("#0B1633", "#3A4E7A", "#FFE6A8", ["#24345C", "#18244A", "#0E1838"], {"stars", "sea", "lighthouse", "moon"}),
    "paper-moons": ("#2A1846", "#E58FA6", "#FFF1E0", ["#8E5A92", "#6A3F78", "#46285A"], {"moon", "stars"}),
    "copper-valley": ("#F2A65A", "#FCE3B0", "#FFF4D6", ["#D9773A", "#B4532A", "#7E3420"], {"mesa"}),
    "northern-line": ("#06142B", "#1B4D5C", "#DDF5FF", ["#2D5E73", "#1B3F55", "#0C2438"], {"stars", "aurora", "snow"}),
    "tidal": ("#7FD3E6", "#E8F7F5", "#FFFDF0", ["#4AA3B8", "#2E7F97"], {"sea"}),
    "salt-and-static": ("#3B0D1F", "#E0674F", "#FFD9B8", ["#B4463A", "#8A2F2E", "#5C1E22"], {"rings", "stars"}),
    "orbit": ("#05060F", "#1E2350", "#9FD0FF", ["#2A2F66"], {"stars", "rings", "planet"}),
    "midnight-orchard": ("#0D1B2E", "#2F4C6E", "#F5F0D8", ["#24405E", "#18304A", "#0D1F33"], {"stars", "moon", "trees"}),
    "glasshouse": ("#BFE8C9", "#F4FBEA", "#FFFFFF", ["#6DB58A", "#3F8F66", "#246B4A"], {"dome"}),
    "harbour-lights": ("#1A2340", "#C96F4A", "#FFD08A", ["#3A3550", "#262541", "#15182E"], {"sea", "lighthouse"}),
    "lost-cities": ("#E9C27A", "#FBEBC4", "#FFF7E0", ["#C99A5B", "#A87943", "#7A552E"], {"mesa", "ruins"}),
    "the-night-sky": ("#02040C", "#14204A", "#E8EEFF", ["#101A3A", "#0A1128"], {"stars", "moon", "aurora"}),
}


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def mix(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scene(slug, w, h, seed=0):
    """A flat, layered landscape for one title, drawn at 2x and scaled down for smooth edges."""
    top, bottom, sun, ridges, extra = SCENES[slug]
    rng = random.Random(f"{slug}-{seed}")
    W, H = w * 2, h * 2
    im = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(im)
    for y in range(H):
        d.line([(0, y), (W, y)], fill=mix(rgb(top), rgb(bottom), y / H))
    if "stars" in extra:
        for _ in range(W * H // 9000):
            x, y, r = rng.randrange(W), rng.randrange(int(H * 0.6)), rng.choice([1, 1, 2, 2, 3])
            d.ellipse((x - r, y - r, x + r, y + r), fill=mix(rgb(top), (255, 255, 255), rng.uniform(0.4, 1)))
    if "aurora" in extra:
        glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        g = ImageDraw.Draw(glow)
        for k, colour in enumerate([(80, 255, 170), (60, 200, 255), (160, 120, 255)]):
            base, amp, ph = H * (0.18 + 0.09 * k + rng.uniform(-0.03, 0.03)), H * 0.06, rng.uniform(0, 6)
            pts = [(x, base + amp * math.sin(x / W * 5 + ph)) for x in range(0, W + 20, 20)]
            g.line(pts, fill=colour + (110,), width=int(H * 0.05))
        im.paste(glow.filter(ImageFilter.GaussianBlur(H * 0.03)), (0, 0), glow.filter(ImageFilter.GaussianBlur(H * 0.03)))
    sx, sy = W * rng.uniform(0.6, 0.78), H * rng.uniform(0.2, 0.32)
    r = H * (0.16 if "planet" in extra else 0.09 if "moon" in extra else 0.12)
    halo = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(halo).ellipse((sx - r * 2, sy - r * 2, sx + r * 2, sy + r * 2), fill=rgb(sun) + (70,))
    halo = halo.filter(ImageFilter.GaussianBlur(r * 0.6))
    im.paste(halo, (0, 0), halo)
    d = ImageDraw.Draw(im)
    disc = mix(rgb(sun), rgb(ridges[0]), 0.45) if "planet" in extra else rgb(sun)
    d.ellipse((sx - r, sy - r, sx + r, sy + r), fill=disc)
    if "rings" in extra:
        d.ellipse((sx - r * 2.1, sy - r * 0.5, sx + r * 2.1, sy + r * 0.5), outline=mix(rgb(sun), (255, 255, 255), 0.3), width=int(r * 0.12))
        d.pieslice((sx - r, sy - r, sx + r, sy + r), 180, 360, fill=disc)
    sea_y = H * 0.72 if "sea" in extra else None
    for i, colour in enumerate(ridges):
        if sea_y and i == len(ridges) - 1:
            break
        base = H * (0.52 + 0.1 * i)
        if "mesa" in extra:
            pts, x = [], 0
            while x < W:
                top_y = base - rng.uniform(0, H * 0.12) if rng.random() < 0.5 else base
                step = rng.uniform(W * 0.08, W * 0.2)
                pts += [(x, base), (x + step * 0.1, top_y), (x + step * 0.9, top_y), (x + step, base)]
                x += step
        else:
            pts, y = [], base
            for x in range(0, W + 40, 40):
                y = min(max(y + rng.uniform(-1, 1) * H * 0.02 * (len(ridges) - i), base - H * 0.12), base + H * 0.06)
                pts.append((x, y))
        d.polygon(pts + [(W, H), (0, H)], fill=colour)
        if "snow" in extra and i == 0:
            d.line(pts, fill=(230, 245, 255), width=6)
    if sea_y:
        sea = mix(rgb(ridges[-1]), rgb(bottom), 0.25)
        d.rectangle((0, sea_y, W, H), fill=sea)
        for k in range(14):
            yy = sea_y + (H - sea_y) * (k + 1) / 15
            half = r * (1.4 - k * 0.07) * rng.uniform(0.7, 1.2)
            d.line([(sx - half, yy), (sx + half, yy)], fill=mix(sea, rgb(sun), 0.55), width=max(2, int(H * 0.006)))
    if "lighthouse" in extra:
        lx, ly, lw, lh = W * 0.24, (sea_y or H * 0.7) + H * 0.02, W * 0.035, H * 0.36
        beam = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(beam).polygon([(lx, ly - lh), (W * 0.95, ly - lh - H * 0.2), (W * 0.95, ly - lh + H * 0.06)], fill=rgb(sun) + (60,))
        im.paste(beam.filter(ImageFilter.GaussianBlur(H * 0.01)), (0, 0), beam.filter(ImageFilter.GaussianBlur(H * 0.01)))
        d = ImageDraw.Draw(im)
        d.polygon([(lx - lw, ly), (lx + lw, ly), (lx + lw * 0.6, ly - lh), (lx - lw * 0.6, ly - lh)], fill=(235, 232, 225))
        for k in range(3):
            yy = ly - lh * (0.2 + 0.28 * k)
            d.rectangle((lx - lw, yy, lx + lw, yy + lh * 0.08), fill=(200, 70, 60))
        d.rectangle((lx - lw * 0.7, ly - lh - lw * 1.2, lx + lw * 0.7, ly - lh), fill=rgb(sun))
        d.polygon([(lx - lw * 0.9, ly - lh - lw * 1.2), (lx + lw * 0.9, ly - lh - lw * 1.2), (lx, ly - lh - lw * 2.2)], fill=(40, 40, 50))
        d.rectangle((0, ly, W * 0.45, H), fill=rgb(ridges[-1]))
    if "trees" in extra:
        for k in range(9):
            tx, ty, tr = W * (0.05 + k * 0.115) + rng.uniform(-20, 20), H * rng.uniform(0.8, 0.86), H * rng.uniform(0.06, 0.09)
            d.rectangle((tx - tr * 0.08, ty, tx + tr * 0.08, H), fill=(14, 24, 36))
            d.ellipse((tx - tr, ty - tr * 1.6, tx + tr, ty + tr * 0.2), fill=(22, 44, 62))
            for _ in range(6):
                bx, by = tx + rng.uniform(-tr * 0.7, tr * 0.7), ty - tr * rng.uniform(0.2, 1.3)
                d.ellipse((bx - 5, by - 5, bx + 5, by + 5), fill=(250, 236, 245))
    if "dome" in extra:
        cx, cy, cr = W * 0.42, H * 0.86, H * 0.5
        dome = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        dd = ImageDraw.Draw(dome)
        dd.pieslice((cx - cr, cy - cr, cx + cr, cy + cr), 180, 360, fill=(255, 255, 255, 60))
        for k in range(1, 6):
            dd.arc((cx - cr * k / 6, cy - cr, cx + cr * k / 6, cy + cr), 180, 360, fill=(255, 255, 255, 140), width=4)
        dd.arc((cx - cr, cy - cr, cx + cr, cy + cr), 180, 360, fill=(255, 255, 255, 200), width=8)
        im.paste(dome, (0, 0), dome)
    if "ruins" in extra:
        for k in range(5):
            cx, ch = W * (0.12 + 0.07 * k), H * rng.uniform(0.18, 0.32)
            d.rectangle((cx, H * 0.78 - ch, cx + W * 0.025, H * 0.78), fill=(120, 84, 48))
        d.rectangle((W * 0.1, H * 0.78 - H * 0.34, W * 0.45, H * 0.78 - H * 0.31), fill=(120, 84, 48))
    return im.resize((w, h), Image.LANCZOS)


def poster(slug, title, line):
    im = scene(slug, 400, 600).convert("RGB")
    shade = Image.new("L", (1, 600))
    for y in range(600):
        shade.putpixel((0, y), max(0, min(225, int((y - 330) / 270 * 225))))
    im = Image.composite(Image.new("RGB", im.size, (10, 12, 18)), im, shade.resize(im.size))
    d = ImageDraw.Draw(im)
    size, words = 64, title.upper()
    while d.textlength(words, font=font(size, b"ExtraBold")) > 350:
        size -= 2
    d.text((200, 532), words, font=font(size, b"ExtraBold"), fill="white", anchor="ms")
    d.text((200, 566), line.upper(), font=font(15, b"Medium"), fill=(255, 255, 255, 190), anchor="ms")
    return im


def logo(initials, colour, rounded):
    S, m = 512, 24
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if rounded:
        d.rounded_rectangle((m, m + 40, S - m, S - m - 40), 90, fill=colour)
    else:
        d.ellipse((m, m, S - m, S - m), fill=colour)
    d.text((S / 2, S / 2), initials, font=font(230 if len(initials) <= 2 else 170, b"ExtraBold"), fill="white", anchor="mm")
    return im.resize((256, 256), Image.LANCZOS)


def save_jpg(im, *path):
    im.convert("RGB").save(os.path.join(MEDIA, *path), quality=82, optimize=True, progressive=True)


def main():
    for sub in ("posters", "stills", "logos"):
        os.makedirs(os.path.join(MEDIA, sub), exist_ok=True)
    for f in server.FILMS:
        save_jpg(poster(f[8], f[1], f"{f[7].split(',')[0]} · {f[3]}"), "posters", f"{f[8]}.jpg")
        save_jpg(scene(f[8], 960, 540, 1), "stills", f"{f[8]}.jpg")
    for s in server.SERIES:
        save_jpg(poster(s[5], s[1], f"{s[6].split(',')[0]} · Series"), "posters", f"{s[5]}.jpg")
        save_jpg(scene(s[5], 960, 540, 1), "stills", f"{s[5]}.jpg")
        for k in range(4):  # episode stills
            save_jpg(scene(s[5], 960, 540, 10 + k), "stills", f"{s[5]}-{k}.jpg")
    for i, c in enumerate(server.CHANNELS):
        logo(server.initials(c["name"]), c["colour"], i % 2 == 1).save(os.path.join(MEDIA, "logos", f"{c['slug']}.png"), optimize=True)
    print("images done")

    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg not found: install it to make media/clip.mp4 and media/clip.ts")
    frame = os.path.join(MEDIA, "clip-frame.png")
    scene("northern-line", 2560, 1440, 2).save(frame)
    mp4, ts = os.path.join(MEDIA, "clip.mp4"), os.path.join(MEDIA, "clip.ts")
    # a slow push-in over the generated landscape, with a silent stereo track (players expect audio)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-loop", "1", "-i", frame, "-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo",
                    "-t", "120", "-vf", "zoompan=z='1+0.15*on/3000':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=1:s=1280x720:fps=25",
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "24", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "64k",
                    "-movflags", "+faststart", mp4], check=True)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", mp4, "-c", "copy", "-bsf:v", "h264_mp4toannexb", "-f", "mpegts", ts], check=True)
    os.remove(frame)
    print("clip done")


if __name__ == "__main__":
    main()
