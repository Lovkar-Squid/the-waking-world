#!/usr/bin/env python3
"""The vertical cut, for TikTok: 1080x1920, about half a minute, sound on.

The source clips are 1920x1080 with the capture's own letterbox, so the real picture is the band
y=116..963 - only 848 pixels tall. A straight 9:16 crop of that keeps a quarter of the width and
saws the boss bar in half; a forty-block giant comes out as a fragment of one arm. So each shot is
a square crop of the picture, laid on a blurred blow-up of itself, with the line above it and the
brand below. Everything sits above y=1600, which is where TikTok's own caption and buttons cover
the frame.

    python3 tools/promo/tiktok_cut.py [out.mp4]
"""

import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
CLIPS = "/root/trailer/clips"
MUSIC = "/root/trailer/src/trailer_music.mp3"
WORK = os.path.join(HERE, "work")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
CINZEL = "/root/trailer/bundle/fonts/Cinzel-700.ttf"
CINZEL9 = "/root/trailer/bundle/fonts/Cinzel-900.ttf"

# clip, in-point, length, x of the square crop within the 1920-wide picture, the line over it
SHOTS = [
    # clip, in-point, length, x of the square crop in the 1920-wide picture, brightness lift, line
    ("08_fight3",  2.0, 2.4, 536, 0.00, "It is forty blocks tall"),
    ("06_fight1",  3.2, 2.2, 460, 0.00, "and it is waking up"),
    ("03_shrineC", 8.0, 2.0, 376, 0.10, "Find the shrine it sleeps under"),
    ("04_rite1",   6.0, 2.2, 276, 0.03, "Pay what the rite asks"),
    ("07_fight2",  2.0, 2.4, 396, 0.00, "Then live through it"),
    ("09_kingdom", 2.0, 2.0, 276, 0.00, "There are kingdoms in these hills"),
    ("10_arena",   1.0, 2.0, 496, 0.00, "and an arena at the end of the world"),
    ("11_rise",    1.2, 2.6, 516, 0.00, "where something bigger is waiting"),
    ("12_tfight",  1.8, 2.2, 330, 0.00, "Put it down"),
    ("13_fall",    1.4, 1.1, 513, 0.00, ""),
    ("14_gate",    1.0, 2.4, 496, 0.00, "and the way home opens"),
]

CARD = 3.6


def wrap(line, limit=26):
    """Two lines at most, split where the halves come out closest in length."""
    if len(line) <= limit:
        return line, ""
    words = line.split()
    best, cut = None, 1
    for i in range(1, len(words)):
        a, b = " ".join(words[:i]), " ".join(words[i:])
        score = abs(len(a) - len(b))
        if best is None or score < best:
            best, cut = score, i
    return " ".join(words[:cut]), " ".join(words[cut:])


def esc(text):
    """drawtext eats colons, backslashes, quotes and percent signs."""
    return (text.replace("\\", "\\\\").replace(":", "\\:")
                .replace("'", "’").replace("%", "\\%"))


def run(args):
    p = subprocess.run(args, capture_output=True, text=True)
    if p.returncode:
        sys.exit(f"ffmpeg failed:\n{' '.join(args[:9])} ...\n{p.stderr[-2500:]}")
    return p


def probe(path, entries):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", entries, "-of", "csv=p=0", path],
        capture_output=True, text=True).stdout.strip()
    return out


def line_text(text, y):
    return (f",drawtext=fontfile={FONT}:text='{esc(text)}':fontcolor=0xF2ECE4:fontsize=60:"
            f"x=(w-text_w)/2:y={y}:shadowcolor=0x000000C0:shadowx=0:shadowy=4")


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "waking-world-tiktok.mp4")
    os.makedirs(WORK, exist_ok=True)
    listing = []

    for i, (clip, ss, dur, cx, lift, line) in enumerate(SHOTS, 1):
        seg = os.path.join(WORK, f"seg{i:02d}.mp4")
        l1, l2 = wrap(line)
        text = ""
        if l1:
            text += line_text(l1, 248)
        if l2:
            text += line_text(l2, 322)
        vf = (
            f"[0:v]fps=30,crop=1920:848:0:116,split=2[bg][fg];"
            f"[bg]crop=848:848:{cx}:0,scale=1080:1920,gblur=sigma=52,"
            f"eq=brightness=-0.30:saturation=1.35[b];"
            f"[fg]crop=848:848:{cx}:0,eq=brightness={lift}:saturation=1.05,"
            f"scale=1080:1080:flags=lanczos,"
            f"pad=1086:1086:3:3:0x35353F[f];"
            f"[b][f]overlay=(W-w)/2:428,"
            # a soft scrim, so the brand still reads when a shot flares white under it
            f"drawbox=x=0:y=1466:w=1080:h=130:color=0x0E0E12@0.55:t=fill,"
            f"drawtext=fontfile={CINZEL}:text='THE WAKING WORLD':fontcolor=0xE2B24A:fontsize=40:"
            f"x=(w-text_w)/2:y=1496,"
            f"drawtext=fontfile={FONT}:text='free on CurseForge':fontcolor=0x9A9490:fontsize=28:"
            f"x=(w-text_w)/2:y=1550{text},format=yuv420p[v]"
        )
        run(["ffmpeg", "-y", "-v", "error", "-ss", str(ss), "-t", str(dur),
             "-i", f"{CLIPS}/{clip}.mp4", "-filter_complex", vf,
             "-map", "[v]", "-an", "-r", "30", "-c:v", "libx264",
             "-preset", "veryfast", "-crf", "19", seg])
        listing.append(seg)
        print(f"  {clip:<12} {ss:>4}+{dur:<4}s  x={cx:<4} {line}")

    card = os.path.join(WORK, "seg99.mp4")
    run(["ffmpeg", "-y", "-v", "error", "-f", "lavfi",
         "-i", f"color=0x0E0E12:s=1080x1920:r=30:d={CARD}",
         "-filter_complex",
         f"[0:v]drawtext=fontfile={CINZEL9}:text='THE WAKING WORLD':fontcolor=0xE2B24A:fontsize=72:"
         f"x=(w-text_w)/2:y=740,"
         f"drawtext=fontfile={FONT}:text='Minecraft 1.21.1 - NeoForge':fontcolor=0xC8C2BA:fontsize=36:"
         f"x=(w-text_w)/2:y=858,"
         f"drawtext=fontfile={FONT}:text='free on CurseForge and Modrinth':fontcolor=0x9A9490:fontsize=32:"
         f"x=(w-text_w)/2:y=944,"
         f"drawtext=fontfile={FONT}:text='by Lovkar':fontcolor=0x6E6A66:fontsize=28:"
         f"x=(w-text_w)/2:y=1080,format=yuv420p[v]",
         "-map", "[v]", "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "19", card])
    listing.append(card)

    with open(os.path.join(WORK, "list.txt"), "w") as f:
        for seg in listing:
            f.write(f"file '{os.path.basename(seg)}'\n")

    silent = os.path.join(WORK, "silent.mp4")
    run(["ffmpeg", "-y", "-v", "error", "-f", "concat", "-safe", "0",
         "-i", os.path.join(WORK, "list.txt"), "-c", "copy", silent])
    dur = float(probe(silent, "format=duration"))

    # the music: the trailer's own, from where it has already built, faded at both ends and
    # brought to the loudness the platforms normalise to anyway
    run(["ffmpeg", "-y", "-v", "error", "-i", silent, "-ss", "62", "-t", f"{dur:.3f}", "-i", MUSIC,
         "-filter_complex",
         f"[1:a]afade=t=in:d=0.8,afade=t=out:st={max(0, dur - 1.8):.3f}:d=1.8,"
         f"loudnorm=I=-14:TP=-1.5:LRA=11,aformat=sample_rates=48000:channel_layouts=stereo[a]",
         "-map", "0:v", "-map", "[a]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
         "-movflags", "+faststart", "-shortest", out])

    print(f"\nbuilt {out}")
    print("  ", probe(out, "format=duration,size"), probe(out, "stream=width,height"))


if __name__ == "__main__":
    main()
