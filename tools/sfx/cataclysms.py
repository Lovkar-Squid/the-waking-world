"""The cataclysms' own voices, synthesized - they were borrowing GENERIC_EXPLODE for everything.

Five sounds, all made from filtered noise and low oscillators rather than samples, and the four that
run under an event are built as SEAMLESS LOOPS: the tail is cross-faded back over the head, so the
game can repeat them for as long as the thing lasts without a click at the seam.

    python3 tools/sfx/cataclysms.py -> resources/assets/wakingworld/sounds/cataclysm/*.ogg
"""
import numpy as np, os, subprocess, wave

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "sounds", "cataclysm")
os.makedirs(OUT, exist_ok=True)
SR = 44100


def noise(n, seed):
    return np.random.default_rng(seed).standard_normal(n)


def lowpass(x, cutoff, passes=2):
    """One-pole low-pass, vectorised with an IIR-by-hand loop that numpy can still do quickly."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = x.astype(np.float64).copy()
    for _ in range(passes):
        # y[i] = a*y[i-1] + (1-a)*x[i]  -- lfilter without scipy
        out = np.empty_like(y)
        acc = 0.0
        for i in range(y.size):
            acc = a * acc + (1 - a) * y[i]
            out[i] = acc
        y = out
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff, passes=1)


def band(n, seed, lo, hi):
    return highpass(lowpass(noise(n, seed), hi), lo)


def loopify(x, fade):
    """Cross-fade the last `fade` samples back over the first, so the ends meet."""
    f = int(fade)
    head, tail = x[:f].copy(), x[-f:].copy()
    ramp = np.linspace(0, 1, f)
    x = x[:-f]
    x[:f] = tail * (1 - ramp) + head * ramp
    return x


def norm(x, peak=0.92):
    return x / max(1e-9, np.max(np.abs(x))) * peak


def save(name, sig, sr=SR):
    pcm = (norm(sig) * 32767).astype(np.int16)
    wav = os.path.join(OUT, name + ".wav")
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav,
                    "-c:a", "libvorbis", "-q:a", "4", os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(wav)
    print(f"{name:<18}{pcm.size / sr:6.2f} s")


def volcano_rumble(seconds=6.0):
    """Under a mountain that is coming up: sub-bass, a slow heave in it, and grit on top."""
    n = int(SR * seconds)
    t = np.arange(n) / SR
    sub = np.sin(2 * np.pi * 27 * t) * 0.55 + np.sin(2 * np.pi * 41 * t + 1.1) * 0.3
    sub *= 0.7 + 0.3 * np.sin(2 * np.pi * 0.23 * t)             # it breathes
    body = lowpass(noise(n, 3), 110) * 3.2
    grit = band(n, 5, 200, 1400) * 0.10 * (0.6 + 0.4 * np.sin(2 * np.pi * 0.37 * t + 2.0))
    return loopify(sub + body + grit, SR * 0.6)


def tornado_roar(seconds=5.0):
    """Not a howl - a freight train. Broadband noise with a slow sweep through it."""
    n = int(SR * seconds)
    t = np.arange(n) / SR
    core = band(n, 11, 60, 900) * 1.0
    air = band(n, 13, 700, 5200) * 0.22 * (0.55 + 0.45 * np.sin(2 * np.pi * 0.31 * t))
    thump = np.sin(2 * np.pi * 34 * t) * 0.18 * (0.5 + 0.5 * np.sin(2 * np.pi * 1.7 * t))
    return loopify(core + air + thump, SR * 0.5)


def quake_rumble(seconds=5.0):
    """The ground itself: very low, with the stone-on-stone groan over it."""
    n = int(SR * seconds)
    t = np.arange(n) / SR
    sub = np.sin(2 * np.pi * 19 * t) * 0.6 + np.sin(2 * np.pi * 31 * t + 0.7) * 0.35
    sub *= 0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 0.55 * t))
    earth = lowpass(noise(n, 17), 80) * 3.6
    groan = lowpass(noise(n, 19), 420) * 0.5 * (0.3 + 0.7 * np.sin(2 * np.pi * 0.19 * t + 1.4) ** 2)
    return loopify(sub + earth + groan, SR * 0.5)


def meteor_scream(seconds=3.2):
    """A star on its way down: a falling whistle over torn air. Plays once, not a loop."""
    n = int(SR * seconds)
    t = np.arange(n) / SR
    p = t / seconds
    f = 1500 * (1 - p) ** 1.6 + 190                              # falls as it comes
    whistle = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.5
    whistle += np.sin(2 * np.pi * np.cumsum(f * 1.5) / SR) * 0.16
    tear = band(n, 23, 300, 6000) * (0.25 + 0.75 * p) * 0.8
    swell = np.clip(p * 2.2, 0, 1) ** 1.4
    return (whistle + tear) * swell


def omen(seconds=4.5):
    """Before it happens: one low note that swells and dies, with the air going wrong under it."""
    n = int(SR * seconds)
    t = np.arange(n) / SR
    p = t / seconds
    bell = (np.sin(2 * np.pi * 58 * t) * 0.5 + np.sin(2 * np.pi * 87 * t + 0.4) * 0.22
            + np.sin(2 * np.pi * 116.5 * t + 1.9) * 0.12)
    shape = np.sin(np.pi * np.clip(p * 1.05, 0, 1)) ** 1.3
    air = lowpass(noise(n, 29), 240) * 1.4 * shape
    return bell * shape + air


save("volcano_rumble", volcano_rumble())
save("tornado_roar", tornado_roar())
save("quake_rumble", quake_rumble())
save("meteor_scream", meteor_scream())
save("omen", omen())
