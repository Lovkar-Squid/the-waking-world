"""The Horn of Waking's voice: a deep war-horn blast synthesized from harmonics - a slow breathy
attack, a swell with vibrato, a hard chest note, a long hall reverb, and a sub-bass thud under it.
Two variants (blow, and the deeper answer when the rite begins).
python3 tools/sfx/horn.py -> resources/assets/wakingworld/sounds/item/horn_blow{,2}.ogg, horn_answer.ogg"""
import numpy as np, os, subprocess

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "sounds", "item")
os.makedirs(OUT, exist_ok=True)
SR = 44100

def lowpass(x, cutoff):
    """One-pole low-pass, applied twice - darkens noise into breath and hall."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    for _ in range(2):
        acc = 0.0
        for i in range(x.size):
            acc = a * acc + (1 - a) * x[i]
            y[i] = acc
        x = y.copy()
    return y

def env(t, attack, hold, release, total):
    e = np.ones_like(t)
    a = t < attack
    e[a] = (t[a] / attack) ** 1.6
    r = t > total - release
    e[r] = np.clip((total - t[r]) / release, 0, 1) ** 1.4
    return e

def horn(freq, total, vib_rate=5.2, vib_depth=0.006, rasp=0.25, seed=1):
    rnd = np.random.default_rng(seed)
    t = np.arange(int(total * SR)) / SR
    # the pitch bends up into the note over the first 0.35 s, then a slow vibrato
    bend = freq * (1 - 0.06 * np.exp(-t / 0.18))
    vib = 1 + vib_depth * np.sin(2 * np.pi * vib_rate * t) * np.clip((t - 0.4) / 0.6, 0, 1)
    phase = 2 * np.pi * np.cumsum(bend * vib) / SR
    sig = np.zeros_like(t)
    # brassy harmonic stack: odd and even, rolling off, with a formant bump around 500-900 Hz
    for k in range(1, 18):
        amp = 1.0 / k ** (0.8 if k <= 5 else 1.1)   # a strong chest: the 2nd to 5th harmonics carry the note on any speaker
        f = freq * k
        formant = 1.0 + 1.8 * np.exp(-((f - 800) / 420) ** 2) + 0.5 * np.exp(-((f - 2200) / 600) ** 2)  # brass bite
        if f > 9000: break
        sig += amp * formant * np.sin(k * phase + rnd.uniform(0, 0.3))
    # breath noise in the attack, rasp on the sustain
    noise = lowpass(rnd.normal(0, 1, t.size), 1800)
    breath = noise * np.exp(-t / 0.22) * 1.2
    sig += breath
    sig *= 1 + rasp * 0.15 * np.sin(2 * np.pi * (freq / 2) * t)  # a subharmonic growl
    sig *= env(t, 0.45, 0, 0.9, total)
    # a chest thud at the start and a sub swell
    sub = np.sin(2 * np.pi * (freq / 2) * t) * env(t, 0.6, 0, 1.2, total) * 0.5
    thud = np.sin(2 * np.pi * 55 * t) * np.exp(-t / 0.35) * 0.8
    sig = sig / np.max(np.abs(sig)) + sub + thud
    return t, sig

def reverb(sig, decay=2.4, mix=0.45, seed=3):
    rnd = np.random.default_rng(seed)
    n = int(decay * SR)
    ir = lowpass(rnd.normal(0, 1, n), 2200) * np.exp(-np.arange(n) / (SR * decay / 4.5))
    # early reflections
    for d in (0.023, 0.041, 0.067, 0.11, 0.17):
        ir[int(d * SR)] += 0.6
    ir /= np.sum(np.abs(ir)) / 8
    wet = np.convolve(sig, ir)
    dry = np.concatenate([sig, np.zeros(wet.size - sig.size)])
    out = dry * (1 - mix) + wet * mix
    return out

def loud(sig, drive=2.6):
    """Loudness: a soft limiter (tanh) that lifts the body of the note towards full scale - the game caps a
    sound's gain at 1.0, so the file itself has to be loud - then back to a 0.98 peak."""
    x = sig / np.max(np.abs(sig))
    y = np.tanh(x * drive) / np.tanh(drive)
    return y / np.max(np.abs(y))

def save(name, sig):
    sig = loud(sig) * 0.93
    pcm = (sig * 32767).astype(np.int16)
    wav = os.path.join(OUT, name + ".wav")
    import wave
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "5", os.path.join(OUT, name + ".ogg")], check=True)
    os.remove(wav)
    print(name, round(sig.size / SR, 2), "s")

# the blow: a big D (73.4 Hz) horn, 2.6 s, hall reverb; and a slightly different second take
t, s = horn(73.4, 2.9, seed=1); save("horn_blow", reverb(s, mix=0.26))
t, s = horn(77.8, 2.7, vib_rate=4.6, seed=7); save("horn_blow2", reverb(s, mix=0.26, seed=5))
# the answer from below when the rite begins: lower, longer, more sub, cathedral tail
t, s = horn(49.0, 3.8, vib_rate=3.2, vib_depth=0.004, rasp=0.45, seed=11); save("horn_answer", reverb(s, decay=4.0, mix=0.38, seed=9))
