"""The Titan's Gate, synthesized (using the primitives of mobs.py):

  open    - a rumble out of the floor as the frame comes up, stone grinding, a rising choir of
            partials that locks into a chord when the void fills the frame (~5 s, the raising takes 5.3)
  close   - the chord breaks, the sheet goes out with a falling sweep, the frame sinks (~2.5 s)
  ambient - the sheet breathing: a slow swell of the chord with a whisper of the void (~3 s)

python3 tools/sfx/gate.py -> resources/assets/wakingworld/sounds/block/titan_gate/<name>.ogg
"""
import os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
import mobs
from mobs import t_of, noise, lowpass, highpass, bandpass, sweep_lowpass, decay_env, glide, osc, ring, impact, crackle, reverb, mix, delay, fade, limiter, SR

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "sounds", "block", "titan_gate")

def save(name, x, peak=0.9, drive=1.5):
    x = fade(limiter(x, drive) * peak, 0.03)
    os.makedirs(OUT, exist_ok=True)
    import wave, subprocess
    wav = os.path.join(OUT, name + ".wav")
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(wav)
    print("wrote", name)

# the gate's chord: a low root with a dark fifth and a shimmer an octave and a bit above (End-ish, unresolved)
CHORD = [55.0, 82.4, 110.0, 164.8, 233.1, 329.6, 466.2]

def chord(seconds, gains, wobble=0.004, seed=3):
    r = np.random.default_rng(seed)
    t = t_of(seconds)
    out = np.zeros_like(t)
    for f, g in zip(CHORD, gains):
        vib = 1 + wobble * np.sin(2 * np.pi * r.uniform(0.15, 0.4) * t + r.uniform(0, 6.28))
        out += g * np.sin(2 * np.pi * np.cumsum(f * vib) / SR + r.uniform(0, 6.28))
    return out / (np.max(np.abs(out)) + 1e-9)

def open_():
    total = 5.4
    # the rumble: brown-ish noise that swells, with the stone grinding in bursts as the rows come up
    rum = lowpass(noise(total, 21), 90) * np.clip(np.linspace(0.2, 1.0, int(total * SR)), 0, 1)
    grind = np.zeros(int(total * SR))
    for k in range(9):
        at = int((0.3 * k) * SR)
        g = bandpass(noise(0.35, 31 + k), 400 + 40 * k, 500) * decay_env(int(0.35 * SR), 0.12)
        end = min(grind.size, at + g.size)
        grind[at:end] += g[:end - at] * (0.5 + 0.06 * k)
        thud = impact(0.3, 1200, 120, 0.06, seed=51 + k)
        end = min(grind.size, at + thud.size)
        grind[at:end] += thud[:end - at] * 0.7
    # the choir: partials fade in one by one as the frame rises, then the whole chord swells when the sheet fills (t 3.1 .. 5.4)
    t = t_of(total)
    gains = [0.9, 0.5, 0.6, 0.35, 0.25, 0.18, 0.1]
    ch = np.zeros_like(t)
    r = np.random.default_rng(5)
    for i, (f, g) in enumerate(zip(CHORD, gains)):
        start = 0.4 + i * 0.4
        e = np.clip((t - start) / 0.6, 0, 1)
        e = e * e
        vib = 1 + 0.003 * np.sin(2 * np.pi * r.uniform(0.2, 0.5) * t)
        ch += g * e * np.sin(2 * np.pi * np.cumsum(f * vib) / SR + r.uniform(0, 6.28))
    ch /= np.max(np.abs(ch)) + 1e-9
    swell = 0.35 + 0.65 * np.clip((t - 3.1) / 1.6, 0, 1) ** 2
    ch *= swell
    # the sheet filling: a rising sweep of filtered noise, and a whisper of the void
    fill = sweep_lowpass(noise(2.4, 77), 300, 5000, 48) * np.clip(np.linspace(0, 1, int(2.4 * SR)), 0, 1) ** 1.5
    fill = delay(fill * 0.35, 3.0)
    # the lock: a bright ring when it stands
    lock = delay(ring([466.2, 932.3, 1398.5, 2330.0], [1.6, 1.1, 0.7, 0.4], [1, 0.5, 0.3, 0.15], 2.2, seed=9) * 0.55, 4.6)
    x = mix((rum, 0.7), (grind, 0.6), (ch, 0.8), fill, lock)
    x = reverb(x, 1.6, 0.35, 2500)
    save("open", x, 0.92, 1.7)

def close_():
    total = 2.6
    t = t_of(total)
    # the chord, breaking: it holds a moment, then detunes and falls away
    r = np.random.default_rng(13)
    ch = np.zeros_like(t)
    for f, g in zip(CHORD, [0.9, 0.5, 0.6, 0.35, 0.25, 0.18, 0.1]):
        fall = f * (1 - 0.35 * np.clip((t - 0.4) / 1.4, 0, 1) ** 1.5)
        ch += g * np.sin(2 * np.pi * np.cumsum(fall) / SR + r.uniform(0, 6.28))
    ch /= np.max(np.abs(ch)) + 1e-9
    ch *= np.clip(1.2 - t / 1.9, 0, 1)
    # the sheet going out: a downward sweep, a glassy shatter
    sweep = sweep_lowpass(noise(1.4, 71), 6000, 150, 40) * decay_env(int(1.4 * SR), 0.5)
    shatter = crackle(0.5, 400, 0.003, 7000, seed=88) * 0.6
    # the frame sinking: grinding and a last thud
    grind = bandpass(noise(1.4, 45), 350, 500) * np.clip(1 - np.linspace(0, 1, int(1.4 * SR)), 0, 1)
    thud = impact(0.5, 900, 80, 0.12, seed=99)
    x = mix((ch, 0.8), (sweep, 0.5), (shatter, 0.5), (delay(grind, 0.6), 0.5), (delay(thud, 1.9), 0.9))
    x = reverb(x, 1.4, 0.3, 2200)
    save("close", x, 0.9, 1.6)

def ambient():
    total = 3.2
    t = t_of(total)
    ch = chord(total, [0.8, 0.4, 0.5, 0.3, 0.22, 0.15, 0.08], 0.006, seed=17)
    breath = np.sin(np.pi * t / total) ** 1.4
    whisper = bandpass(noise(total, 61), 1800, 1600) * breath * 0.25
    x = mix((ch * breath, 0.9), whisper)
    x = reverb(x, 1.2, 0.3, 3000)
    save("ambient", x, 0.6, 1.3)

if __name__ == "__main__":
    open_(); close_(); ambient()
