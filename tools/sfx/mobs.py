"""Voices for the mod's own creatures, synthesized: nothing borrowed from the zombies and skeletons
they are built on. Each set is ambient / hurt / death / step (and what the creature does besides).

  Ember Wraith    - embers crackling in a hollow chest, steam when struck, a whoosh of ash at the end
  Rune Sentinel   - hollow bones rattling under a crystalline hum, the rune-bow's zing
  Drowned Keeper  - a gurgling groan from a chest full of water, bubbles, the trident's wet whoosh
  Stone Thrall    - stone grinding on stone, heavy feet; the Hollow one whispers from the void
  Guard           - short grunts and the clank of mail; "hup" when it strikes
  Townsfolk       - friendly hums, a rising "hm?" for yes, a grumble for no
  King            - a lower, slower voice; a stern "hmph" when displeased

python3 tools/sfx/mobs.py -> resources/assets/wakingworld/sounds/mob/<mob>/<name>[n].ogg
"""
import numpy as np, os, subprocess, wave
from scipy import signal

OUT = os.path.join(os.path.dirname(__file__), "..", "..", "resources", "assets", "wakingworld", "sounds", "mob")
SR = 44100
rng = np.random.default_rng(7)

# ----------------------------------------------------------------- primitives

def t_of(seconds):
    return np.arange(int(seconds * SR)) / SR

def noise(seconds, seed=None):
    r = rng if seed is None else np.random.default_rng(seed)
    return r.normal(0, 1, int(seconds * SR))

def lowpass(x, fc, order=2):
    b, a = signal.butter(order, min(fc, SR / 2 - 100) / (SR / 2))
    return signal.lfilter(b, a, x)

def highpass(x, fc, order=2):
    b, a = signal.butter(order, max(fc, 20) / (SR / 2), btype="high")
    return signal.lfilter(b, a, x)

def bandpass(x, fc, bw, order=2):
    lo, hi = max(20, fc - bw / 2), min(SR / 2 - 100, fc + bw / 2)
    b, a = signal.butter(order, [lo / (SR / 2), hi / (SR / 2)], btype="band")
    return signal.lfilter(b, a, x)

def sweep_lowpass(x, fc0, fc1, blocks=64):
    """A low-pass whose cutoff glides from fc0 to fc1 over the signal (processed in blocks)."""
    out = np.zeros_like(x)
    n = x.size
    edges = np.linspace(0, n, blocks + 1).astype(int)
    zi = None
    for i in range(blocks):
        fc = fc0 + (fc1 - fc0) * (i / max(1, blocks - 1))
        b, a = signal.butter(2, min(fc, SR / 2 - 100) / (SR / 2))
        seg = x[edges[i]:edges[i + 1]]
        if seg.size == 0: continue
        if zi is None: zi = signal.lfilter_zi(b, a) * seg[0]
        y, zi = signal.lfilter(b, a, seg, zi=zi)
        out[edges[i]:edges[i + 1]] = y
    return out

def env(n, attack, decay, sustain=1.0, release=0.1, curve=1.0):
    """Attack / decay-to-sustain / release, in seconds; the release ends the sound."""
    a, d, r = int(attack * SR), int(decay * SR), int(release * SR)
    e = np.ones(n)
    a = min(a, n)
    e[:a] = np.linspace(0, 1, a) ** curve
    if d > 0 and a + d <= n:
        e[a:a + d] = np.linspace(1, sustain, d)
        e[a + d:] = sustain
    r = min(r, n)
    if r > 0: e[n - r:] *= np.linspace(1, 0, r) ** 1.5
    return e

def decay_env(n, tau):
    return np.exp(-np.arange(n) / (SR * tau))

def glide(f0, f1, seconds, shape=1.0):
    """A frequency curve from f0 to f1 (shape > 1 lingers near f0)."""
    n = int(seconds * SR)
    u = np.linspace(0, 1, n) ** shape
    return f0 + (f1 - f0) * u

def osc(freq_curve, kind="sine", harmonics=1, rolloff=1.0, phase=0.0):
    ph = 2 * np.pi * np.cumsum(freq_curve) / SR + phase
    if kind == "sine":
        return np.sin(ph)
    if kind == "saw":
        return signal.sawtooth(ph)
    if kind == "square":
        return signal.square(ph)
    if kind == "stack":
        out = np.zeros_like(ph)
        for k in range(1, harmonics + 1):
            out += np.sin(k * ph) / k ** rolloff
        return out / np.max(np.abs(out) + 1e-9)
    raise ValueError(kind)

def vibrato(freq_curve, rate, depth):
    n = freq_curve.size
    return freq_curve * (1 + depth * np.sin(2 * np.pi * rate * np.arange(n) / SR))

def voice(freq_curve, formants, breath=0.15, rasp=0.0, seed=None):
    """A crude throat: a bright glottal source shaped by parallel formant band-passes.
    formants = [(centre Hz, bandwidth Hz, gain), ...]; the centre can be a curve too."""
    n = freq_curve.size
    src = osc(freq_curve, "stack", harmonics=40, rolloff=0.9)
    if rasp > 0:
        src *= 1 + rasp * lowpass(noise(n / SR, seed), 400)
    src += breath * lowpass(noise(n / SR, None if seed is None else seed + 1), 3000)
    out = np.zeros(n)
    for f, bw, g in formants:
        if np.isscalar(f):
            out += g * bandpass(src, f, bw)
        else:
            # a moving formant: blockwise
            blocks = 48
            edges = np.linspace(0, n, blocks + 1).astype(int)
            for i in range(blocks):
                fc = float(f[min(f.size - 1, edges[i])])
                seg = src[edges[i]:edges[i + 1]]
                if seg.size: out[edges[i]:edges[i + 1]] += g * bandpass(seg, fc, bw)
    return out / (np.max(np.abs(out)) + 1e-9)

def ring(freqs, decays, gains=None, seconds=1.0, seed=None):
    """Struck metal or stone: a set of decaying partials."""
    t = t_of(seconds)
    out = np.zeros_like(t)
    r = rng if seed is None else np.random.default_rng(seed)
    for i, (f, d) in enumerate(zip(freqs, decays)):
        g = 1.0 if gains is None else gains[i]
        out += g * np.sin(2 * np.pi * f * t + r.uniform(0, 6.28)) * np.exp(-t / d)
    return out / (np.max(np.abs(out)) + 1e-9)

def impact(seconds=0.25, bright=3000, dark=200, tau=0.05, seed=None):
    """A thud: a noise burst whose colour drops from bright to dark as it dies."""
    x = noise(seconds, seed)
    x = sweep_lowpass(x, bright, dark, 24) * decay_env(x.size, tau)
    return x / (np.max(np.abs(x)) + 1e-9)

def crackle(seconds, density, tau=0.004, bright=6000, seed=None):
    """Random little pops: embers, bones, gravel."""
    r = rng if seed is None else np.random.default_rng(seed)
    n = int(seconds * SR)
    out = np.zeros(n)
    count = int(seconds * density)
    for _ in range(count):
        at = r.integers(0, max(1, n - 400))
        length = int(SR * tau * r.uniform(0.5, 2.0))
        pop = r.normal(0, 1, length) * np.exp(-np.arange(length) / (length / 3)) * r.uniform(0.3, 1.0)
        pop = lowpass(pop, bright * r.uniform(0.5, 1.5))
        end = min(n, at + length)
        out[at:end] += pop[:end - at]
    return out / (np.max(np.abs(out)) + 1e-9)

def bubbles(seconds, rate=12, seed=None):
    """Bubbles: short rising sine blips."""
    r = rng if seed is None else np.random.default_rng(seed)
    n = int(seconds * SR)
    out = np.zeros(n)
    for _ in range(int(seconds * rate)):
        at = r.integers(0, max(1, n - 2000))
        length = int(SR * r.uniform(0.03, 0.09))
        f0 = r.uniform(300, 900)
        f = glide(f0, f0 * r.uniform(1.6, 2.6), length / SR, 0.7)
        length = f.size
        blip = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-np.arange(length) / (length / 2.5)) * r.uniform(0.2, 0.7)
        end = min(n, at + length)
        out[at:end] += blip[:end - at]
    return out / (np.max(np.abs(out)) + 1e-9)

def reverb(x, decay=0.9, mix=0.25, tone=3000, seed=11):
    r = np.random.default_rng(seed)
    n = int(decay * SR)
    ir = lowpass(r.normal(0, 1, n), tone) * np.exp(-np.arange(n) / (SR * decay / 4.0))
    for d in (0.011, 0.023, 0.037, 0.053):
        if int(d * SR) < n: ir[int(d * SR)] += 0.5
    ir /= np.sum(np.abs(ir)) / 6
    wet = signal.fftconvolve(x, ir)[: x.size + n]
    dry = np.concatenate([x, np.zeros(wet.size - x.size)])
    return dry * (1 - mix) + wet * mix

def mix(*parts):
    """Sums signals of different lengths (each may be (signal, gain))."""
    arrs = []
    for p in parts:
        if isinstance(p, tuple): sig, g = p
        else: sig, g = p, 1.0
        arrs.append(sig * g)
    n = max(a.size for a in arrs)
    out = np.zeros(n)
    for a in arrs: out[:a.size] += a
    return out

def delay(x, seconds):
    return np.concatenate([np.zeros(int(seconds * SR)), x])

def limiter(x, drive=1.6):
    x = x / (np.max(np.abs(x)) + 1e-9)
    y = np.tanh(x * drive) / np.tanh(drive)
    return y / (np.max(np.abs(y)) + 1e-9)

def fade(x, seconds=0.02):
    n = min(x.size, int(seconds * SR))
    x = x.copy()
    x[:n] *= np.linspace(0, 1, n)
    x[-n:] *= np.linspace(1, 0, n)
    return x

def save(mob, name, x, peak=0.9, drive=1.6):
    x = fade(limiter(x, drive) * peak)
    d = os.path.join(OUT, mob)
    os.makedirs(d, exist_ok=True)
    wav = os.path.join(d, name + ".wav")
    with wave.open(wav, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", os.path.join(d, name + ".ogg")], check=True)
    os.remove(wav)
    return name

# ----------------------------------------------------------------- the creatures

def ember_wraith():
    m = "ember_wraith"
    # ambient: the coals breathe - a slow rumble with a hiss over it and pops
    for i in range(3):
        sec = 1.6 + 0.3 * i
        breath = lowpass(noise(sec, 100 + i), 700) * (0.55 + 0.45 * np.sin(2 * np.pi * (0.9 + 0.2 * i) * t_of(sec)))
        hiss = bandpass(noise(sec, 110 + i), 4200, 3000) * env(int(sec * SR), 0.3, 0.6, 0.5, 0.5)
        pops = crackle(sec, 26 + 6 * i, 0.003, 7000, 120 + i)
        rumble = osc(vibrato(glide(46, 40, sec), 3.0, 0.02), "stack", 6, 1.2) * env(int(sec * SR), 0.2, 0.5, 0.7, 0.6)
        save(m, f"ambient{i + 1}", mix((breath, 0.9), (hiss, 0.35), (pops, 0.8), (rumble, 0.5)))
    # hurt: steam - a sharp hiss that breaks into crackle
    for i in range(2):
        sec = 0.7
        steam = bandpass(noise(sec, 130 + i), 3200 + 600 * i, 4000) * env(int(sec * SR), 0.01, 0.25, 0.3, 0.3)
        pops = crackle(sec, 40, 0.003, 8000, 140 + i) * env(int(sec * SR), 0.05, 0.2, 0.6, 0.3)
        thud = impact(0.3, 1200, 120, 0.05, 150 + i)
        save(m, f"hurt{i + 1}", mix((steam, 1.0), (pops, 0.7), (thud, 0.8)), drive=2.0)
    # death: a whoosh, the fire goes out, ash settles
    sec = 2.4
    whoosh = sweep_lowpass(noise(sec, 160), 600, 5000, 32) * env(int(sec * SR), 0.25, 0.3, 0.2, 1.6)
    out = bandpass(noise(sec, 161), 2500, 3500) * env(int(sec * SR), 0.35, 0.5, 0.2, 1.2)
    ash = crackle(sec, 18, 0.006, 3000, 162) * env(int(sec * SR), 0.5, 0.8, 0.5, 1.0)
    fall = osc(glide(70, 28, sec, 0.8), "stack", 5, 1.3) * env(int(sec * SR), 0.1, 0.8, 0.3, 1.2)
    save(m, "death", reverb(mix((whoosh, 0.9), (out, 0.8), (ash, 0.6), (fall, 0.5)), 1.2, 0.3), drive=1.8)
    # step: a crunch of charcoal
    for i in range(2):
        st = mix((impact(0.18, 2500, 300, 0.03, 170 + i), 1.0), (crackle(0.18, 60, 0.002, 6000, 180 + i), 0.5))
        save(m, f"step{i + 1}", st, peak=0.7)
    # flare: what it does when the fire takes - a flash of flame
    sec = 0.6
    flare = sweep_lowpass(noise(sec, 190), 300, 6000, 24) * env(int(sec * SR), 0.03, 0.15, 0.4, 0.4)
    save(m, "flare", mix((flare, 1.0), (crackle(sec, 50, 0.003, 8000, 191), 0.5)), drive=1.8)

def rune_sentinel():
    m = "rune_sentinel"
    def hum(sec, f, depth=0.006, seed=0):
        t = t_of(sec)
        base = osc(vibrato(np.full(int(sec * SR), f), 5.5, depth), "stack", 4, 1.6)
        shimmer = np.sin(2 * np.pi * f * 3.01 * t) * 0.15 * (0.5 + 0.5 * np.sin(2 * np.pi * 7 * t))
        return (base + shimmer) * env(int(sec * SR), 0.2, 0.4, 0.8, 0.5)
    def bones(sec, density, seed):
        r = np.random.default_rng(seed)
        n = int(sec * SR)
        out = np.zeros(n)
        for _ in range(int(sec * density)):
            at = r.integers(0, max(1, n - 3000))
            click = ring([r.uniform(1500, 2600), r.uniform(3200, 5200)], [0.012, 0.007], seconds=0.06, seed=int(r.integers(0, 9999)))
            click = np.concatenate([click, np.zeros(1)])[:min(3000, n - at)]
            out[at:at + click.size] += click * r.uniform(0.3, 1.0)
        return out / (np.max(np.abs(out)) + 1e-9)
    for i in range(3):
        sec = 1.5 + 0.3 * i
        save(m, f"ambient{i + 1}", mix((bones(sec, 14 + 4 * i, 200 + i), 0.9), (hum(sec, 880 * (1 + 0.06 * i), seed=i), 0.35)))
    for i in range(2):
        sec = 0.6
        crack = ring([1900 + 300 * i, 3300, 5100], [0.05, 0.03, 0.02], [1.0, 0.6, 0.4], sec, 210 + i)
        bend = osc(glide(920, 640, sec, 0.6), "stack", 3, 1.5) * env(int(sec * SR), 0.005, 0.2, 0.3, 0.3)
        save(m, f"hurt{i + 1}", mix((crack, 1.0), (bones(sec, 40, 220 + i), 0.6), (bend, 0.4)), drive=1.9)
    sec = 2.2
    cascade = bones(sec, 60, 230) * env(int(sec * SR), 0.02, 0.6, 0.35, 1.2)
    die = osc(glide(900, 180, sec, 1.6), "stack", 3, 1.5) * env(int(sec * SR), 0.05, 0.5, 0.5, 1.4)
    shatter = ring([2400, 3900, 5600, 7200], [0.3, 0.25, 0.2, 0.15], [1.0, 0.7, 0.5, 0.3], 1.2, 231)
    save(m, "death", reverb(mix((cascade, 0.9), (die, 0.45), (delay(shatter, 0.25), 0.7)), 1.4, 0.3), drive=1.8)
    for i in range(2):
        save(m, f"step{i + 1}", mix((ring([1700 + 200 * i, 4100], [0.02, 0.01], seconds=0.12, seed=240 + i), 0.8), (impact(0.12, 1500, 300, 0.02, 250 + i), 0.6)), peak=0.65)
    # shoot: the rune-bow - a plucked string and a bright zing that runs up
    sec = 0.5
    pluck = osc(glide(180, 176, sec), "stack", 12, 1.1) * decay_env(int(sec * SR), 0.12)
    zing = osc(glide(1400, 3800, sec, 0.5), "sine") * env(int(sec * SR), 0.005, 0.1, 0.0, 0.2)
    save(m, "shoot", mix((pluck, 1.0), (zing, 0.5), (highpass(noise(0.2, 260), 4000) * decay_env(int(0.2 * SR), 0.03), 0.4)), drive=1.5)

def drowned_keeper():
    m = "drowned_keeper"
    def groan(sec, f0, f1, wet, seed):
        f = vibrato(glide(f0, f1, sec, 1.3), 4.0, 0.015)
        v = voice(f, [(380, 160, 1.0), (900, 220, 0.6), (2300, 320, 0.25)], breath=0.3, rasp=0.5, seed=seed)
        gurgle = 1 + 0.5 * lowpass(noise(sec, seed + 5), 60) # the throat full of water
        v = v * gurgle * env(int(sec * SR), 0.15, 0.4, 0.8, 0.5)
        if wet: v = lowpass(v, 900)
        return v
    for i in range(3):
        sec = 1.5 + 0.3 * i
        save(m, f"ambient{i + 1}", mix((groan(sec, 105 - 8 * i, 88, False, 300 + i), 1.0), (bubbles(sec, 6, 310 + i), 0.25)))
    for i in range(2):
        sec = 1.9 + 0.3 * i
        save(m, f"ambient_water{i + 1}", mix((groan(sec, 100 - 6 * i, 84, True, 320 + i), 1.0), (bubbles(sec, 16, 330 + i), 0.6)))
    for i in range(2):
        sec = 0.55
        g = voice(glide(150 + 20 * i, 110, sec, 0.7), [(500, 200, 1.0), (1100, 260, 0.6), (2500, 400, 0.3)], breath=0.35, rasp=0.6, seed=340 + i) * env(int(sec * SR), 0.01, 0.2, 0.4, 0.25)
        save(m, f"hurt{i + 1}", mix((g, 1.0), (bubbles(sec, 20, 350 + i), 0.3), (impact(0.2, 900, 150, 0.04, 360 + i), 0.5)), drive=1.9)
    sec = 2.6
    d = groan(sec, 120, 55, False, 370) * env(int(sec * SR), 0.1, 0.9, 0.6, 1.5)
    save(m, "death", reverb(mix((d, 1.0), (bubbles(sec, 22, 371), 0.5), (sweep_lowpass(noise(sec, 372), 1500, 200, 24) * env(int(sec * SR), 0.6, 0.5, 0.4, 1.5), 0.4)), 1.0, 0.25), drive=1.7)
    for i in range(2):
        st = mix((sweep_lowpass(noise(0.25, 380 + i), 3000, 400, 16) * decay_env(int(0.25 * SR), 0.05), 1.0), (bubbles(0.25, 30, 390 + i), 0.4), (impact(0.2, 600, 100, 0.05, 395 + i), 0.7))
        save(m, f"step{i + 1}", st, peak=0.7)
    sec = 0.5
    swim = mix((sweep_lowpass(noise(sec, 400), 800, 3500, 16) * env(int(sec * SR), 0.05, 0.2, 0.3, 0.25), 1.0), (bubbles(sec, 40, 401), 0.6))
    save(m, "swim", swim, peak=0.75)
    # the trident swung - a heavy wet whoosh
    sec = 0.55
    whoosh = sweep_lowpass(noise(sec, 410), 400, 2600, 16) * env(int(sec * SR), 0.12, 0.1, 0.6, 0.3)
    save(m, "attack", mix((whoosh, 1.0), (osc(glide(160, 90, sec), "stack", 3, 1.4) * env(int(sec * SR), 0.05, 0.2, 0.3, 0.3), 0.4)), drive=1.7)

def stone_thrall():
    m = "stone_thrall"
    def grind(sec, seed, tone=900):
        g = bandpass(noise(sec, seed), tone, 1400) * (0.5 + 0.5 * np.abs(np.sin(2 * np.pi * 1.7 * t_of(sec) + seed)))
        return g * env(int(sec * SR), 0.15, 0.5, 0.7, 0.4)
    def rumble(sec, seed, f=42):
        return osc(vibrato(np.full(int(sec * SR), f), 2.5, 0.03), "stack", 5, 1.2) * env(int(sec * SR), 0.2, 0.6, 0.6, 0.5)
    for i in range(3):
        sec = 1.5 + 0.35 * i
        save(m, f"ambient{i + 1}", mix((grind(sec, 500 + i, 800 + 150 * i), 0.8), (rumble(sec, 510 + i, 40 + 4 * i), 0.7), (crackle(sec, 10, 0.01, 2500, 520 + i), 0.5)))
    for i in range(2):
        sec = 0.55
        crack = mix((impact(0.3, 3500, 300, 0.04, 530 + i), 1.0), (crackle(sec, 50, 0.006, 3500, 540 + i) * decay_env(int(sec * SR), 0.15), 0.8), (ring([320, 610, 1180], [0.15, 0.1, 0.06], seconds=sec, seed=550 + i), 0.5))
        save(m, f"hurt{i + 1}", crack, drive=1.9)
    sec = 2.4
    fall = crackle(sec, 22, 0.02, 1500, 560) * env(int(sec * SR), 0.05, 0.9, 0.5, 1.2)
    boulders = mix(*[(delay(impact(0.35, 1500, 120, 0.08, 570 + k), 0.12 * k + 0.05 * (k % 2)), 0.9 - 0.08 * k) for k in range(8)])
    save(m, "death", reverb(mix((fall, 0.9), (boulders, 1.0), (rumble(sec, 580, 36) * env(int(sec * SR), 0.05, 0.6, 0.6, 1.4), 0.7)), 1.4, 0.3), drive=1.7)
    for i in range(2):
        save(m, f"step{i + 1}", mix((impact(0.28, 1200, 90, 0.07, 590 + i), 1.0), (crackle(0.28, 30, 0.005, 3000, 600 + i) * decay_env(int(0.28 * SR), 0.06), 0.4)), peak=0.8)
    # the Hollow one: the same stone, and something whispering out of it
    def whisper(sec, seed):
        r = np.random.default_rng(seed)
        f = glide(r.uniform(700, 1100), r.uniform(1400, 2400), sec, 0.9)
        w = voice(f * 0.001 + 1, [], breath=1.0, seed=seed) if False else None
        n = int(sec * SR)
        w = bandpass(noise(sec, seed), 1600, 1200) * (0.5 + 0.5 * np.sin(2 * np.pi * 6.5 * t_of(sec)))
        for k in range(3):
            fc = glide(r.uniform(600, 1200), r.uniform(1800, 3200), sec, 0.7)
            blocks = 40
            edges = np.linspace(0, n, blocks + 1).astype(int)
            src = noise(sec, seed + 10 + k)
            for b in range(blocks):
                seg = src[edges[b]:edges[b + 1]]
                if seg.size: w[edges[b]:edges[b + 1]] += 0.6 * bandpass(seg, float(fc[edges[b]]), 220)
        tone = osc(vibrato(glide(220, 180, sec), 3.0, 0.04), "stack", 3, 1.8) * 0.3
        return (w / (np.max(np.abs(w)) + 1e-9) + tone) * env(n, 0.3, 0.5, 0.7, 0.6)
    for i in range(3):
        sec = 1.8 + 0.3 * i
        save(m, f"hollow_ambient{i + 1}", reverb(mix((grind(sec, 610 + i, 700), 0.45), (rumble(sec, 620 + i, 36), 0.5), (whisper(sec, 630 + i), 0.75)), 1.6, 0.45, 2500, 631 + i))
    for i in range(2):
        sec = 0.6
        save(m, f"hollow_hurt{i + 1}", reverb(mix((impact(0.3, 3000, 250, 0.04, 640 + i), 0.9), (whisper(sec, 650 + i) * env(int(sec * SR), 0.005, 0.1, 0.6, 0.3), 0.8), (osc(glide(900, 300, sec, 0.5), "sine") * decay_env(int(sec * SR), 0.15), 0.4)), 0.9, 0.35), drive=1.9)
    sec = 2.6
    save(m, "hollow_death", reverb(mix((boulders, 0.8), (whisper(sec, 660) * env(int(sec * SR), 0.1, 0.8, 0.6, 1.5), 0.9), (osc(glide(400, 60, sec, 1.4), "stack", 4, 1.5) * env(int(sec * SR), 0.1, 0.8, 0.5, 1.5), 0.6)), 1.8, 0.45, 2200, 661), drive=1.7)

def fit(a, b):
    """Multiplies two signals that may differ by a sample or two in length."""
    n = min(a.size, b.size)
    return a[:n] * b[:n]

def human_grunt(sec, f0, f1, vowel, seed, breath=0.25, rasp=0.15, shape=0.9):
    """A short voiced sound. vowel: formant targets."""
    vowels = {
        "uh": [(600, 140, 1.0), (1100, 200, 0.6), (2500, 300, 0.25)],
        "ah": [(750, 160, 1.0), (1250, 220, 0.7), (2600, 320, 0.3)],
        "oh": [(450, 130, 1.0), (850, 180, 0.6), (2500, 300, 0.2)],
        "mm": [(280, 90, 1.0), (1100, 200, 0.15), (2300, 300, 0.05)],
        "eh": [(600, 140, 1.0), (1800, 240, 0.6), (2600, 320, 0.3)],
    }
    f = vibrato(glide(f0, f1, sec, shape), 5.0, 0.01)
    return voice(f, vowels[vowel], breath=breath, rasp=rasp, seed=seed)

def guard():
    m = "guard"
    mail = lambda seed, sec=0.5: ring([2100, 2900, 3700, 4600, 6100], [0.09, 0.07, 0.06, 0.05, 0.04], [1.0, 0.8, 0.7, 0.5, 0.4], sec, seed) * 0.6 + crackle(sec, 80, 0.002, 9000, seed + 1) * decay_env(int(sec * SR), 0.12) * 0.5
    for i in range(3):
        sec = 0.45 + 0.05 * i
        v = ["uh", "ah", "eh"][i]
        g = human_grunt(sec, 135 - 8 * i, 100, v, 700 + i, breath=0.3, rasp=0.25) * env(int(sec * SR), 0.01, 0.15, 0.5, 0.22)
        save(m, f"hurt{i + 1}", mix((g, 1.0), (mail(710 + i, 0.4), 0.55)), drive=1.8)
    sec = 1.6
    d = human_grunt(sec, 125, 70, "oh", 720, breath=0.35, rasp=0.3, shape=1.4) * env(int(sec * SR), 0.03, 0.5, 0.5, 1.0)
    clatter = mix(*[(delay(mail(730 + k, 0.35), 0.5 + 0.11 * k), 0.8 - 0.1 * k) for k in range(5)])
    save(m, "death", reverb(mix((d, 1.0), (clatter, 0.7)), 0.8, 0.25), drive=1.7)
    for i in range(2):
        sec = 0.32
        hup = human_grunt(sec, 150, 175 + 10 * i, "uh", 740 + i, breath=0.2, rasp=0.1, shape=0.6) * env(int(sec * SR), 0.01, 0.08, 0.7, 0.15)
        save(m, f"attack{i + 1}", mix((hup, 1.0), (mail(750 + i, 0.25), 0.3)), drive=1.7)
    # the archer's bow
    sec = 0.4
    pluck = osc(glide(210, 205, sec), "stack", 10, 1.2) * decay_env(int(sec * SR), 0.08)
    save(m, "shoot", mix((pluck, 1.0), (highpass(noise(0.15, 760), 3000) * decay_env(int(0.15 * SR), 0.02), 0.5)), drive=1.4)
    # a step in boots and mail
    for i in range(2):
        save(m, f"step{i + 1}", mix((impact(0.16, 1800, 200, 0.03, 770 + i), 1.0), (mail(780 + i, 0.16), 0.25)), peak=0.6)

def townsfolk():
    m = "townsfolk"
    # hums: a little melody in each, friendly
    for i in range(3):
        sec = 0.9 + 0.15 * i
        f0 = [150, 175, 165][i]
        contour = np.concatenate([glide(f0, f0 * 1.12, sec * 0.45, 1.0), glide(f0 * 1.12, f0 * 0.95, sec * 0.55, 1.0)])
        contour = vibrato(contour, 4.5, 0.012)
        h = voice(contour, [(300, 100, 1.0), (1000, 220, 0.25), (2400, 320, 0.08)], breath=0.12, rasp=0.05, seed=800 + i) * env(contour.size, 0.06, 0.3, 0.8, 0.3)
        save(m, f"ambient{i + 1}", h, peak=0.75, drive=1.3)
    for i in range(2):
        sec = 0.55
        contour = vibrato(glide(160 + 10 * i, 240, sec, 0.8), 5.0, 0.01)  # rising: "hm?"
        y = voice(contour, [(320, 110, 1.0), (1100, 220, 0.3), (2400, 300, 0.1)], breath=0.12, seed=810 + i) * env(int(sec * SR), 0.04, 0.2, 0.8, 0.2)
        save(m, f"yes{i + 1}", y, peak=0.75, drive=1.3)
    for i in range(2):
        sec = 0.6
        contour = vibrato(glide(170, 110 - 10 * i, sec, 1.2), 4.0, 0.015)  # falling grumble
        n = voice(contour, [(280, 100, 1.0), (900, 200, 0.4), (2300, 300, 0.08)], breath=0.15, rasp=0.3, seed=820 + i) * env(int(sec * SR), 0.03, 0.25, 0.7, 0.25)
        save(m, f"no{i + 1}", n, peak=0.75, drive=1.4)
    for i in range(2):
        sec = 0.4
        a = human_grunt(sec, 210 - 20 * i, 150, "ah", 830 + i, breath=0.3, rasp=0.15) * env(int(sec * SR), 0.01, 0.12, 0.5, 0.2)
        save(m, f"hurt{i + 1}", a, drive=1.7)
    sec = 1.3
    d = human_grunt(sec, 190, 90, "oh", 840, breath=0.35, rasp=0.25, shape=1.3) * env(int(sec * SR), 0.03, 0.5, 0.5, 0.8)
    save(m, "death", reverb(d, 0.7, 0.2), drive=1.6)
    # a greeting when you come up: a warm "oh, hm" - two notes
    sec = 0.8
    contour = np.concatenate([glide(190, 230, sec * 0.35, 0.8), glide(230, 170, sec * 0.65, 1.0)])
    save(m, "greet", voice(vibrato(contour, 4.5, 0.012), [(420, 130, 1.0), (900, 200, 0.5), (2500, 320, 0.1)], breath=0.14, seed=850) * env(contour.size, 0.04, 0.25, 0.8, 0.3), peak=0.75, drive=1.3)

def king():
    m = "king"
    for i in range(2):
        sec = 1.2 + 0.2 * i
        f0 = 112 - 6 * i
        contour = np.concatenate([glide(f0, f0 * 1.06, sec * 0.5, 1.0), glide(f0 * 1.06, f0 * 0.92, sec * 0.5, 1.0)])
        h = voice(vibrato(contour, 4.0, 0.01), [(280, 90, 1.0), (950, 200, 0.25), (2300, 300, 0.06)], breath=0.1, rasp=0.08, seed=900 + i) * env(contour.size, 0.08, 0.4, 0.8, 0.4)
        save(m, f"ambient{i + 1}", reverb(h, 0.9, 0.2), peak=0.75, drive=1.3)
    # "ahem": a cleared throat then a short low note
    sec = 0.9
    clear = bandpass(noise(0.18, 910), 900, 900) * (1 + 0.8 * np.sin(2 * np.pi * 38 * t_of(0.18))) * env(int(0.18 * SR), 0.01, 0.05, 0.6, 0.08)
    note = human_grunt(0.5, 118, 105, "mm", 911, breath=0.1, rasp=0.05) * env(int(0.5 * SR), 0.04, 0.2, 0.8, 0.2)
    save(m, "greet", reverb(mix((clear, 0.9), (delay(note, 0.3), 1.0)), 0.9, 0.2), peak=0.75, drive=1.4)
    # displeased: "hmph" - a hum that shuts with a puff
    sec = 0.6
    hm = human_grunt(0.35, 125, 95, "mm", 920, breath=0.1, rasp=0.15, shape=1.5) * env(int(0.35 * SR), 0.02, 0.15, 0.7, 0.12)
    puff = lowpass(noise(0.15, 921), 2500) * decay_env(int(0.15 * SR), 0.03)
    save(m, "angry", mix((hm, 1.0), (delay(puff, 0.32), 0.7)), drive=1.6)
    for i in range(2):
        sec = 0.45
        save(m, f"hurt{i + 1}", human_grunt(sec, 130 - 10 * i, 95, ["uh", "oh"][i], 930 + i, breath=0.3, rasp=0.2) * env(int(sec * SR), 0.01, 0.15, 0.5, 0.22), drive=1.7)
    sec = 1.8
    d = human_grunt(sec, 120, 65, "oh", 940, breath=0.35, rasp=0.3, shape=1.4) * env(int(sec * SR), 0.03, 0.6, 0.5, 1.1)
    crown = delay(ring([2600, 3300, 4400, 5200], [0.25, 0.2, 0.15, 0.12], [1.0, 0.7, 0.5, 0.3], 1.0, 941), 0.9)
    save(m, "death", reverb(mix((d, 1.0), (crown, 0.5)), 1.2, 0.3), drive=1.6)

if __name__ == "__main__":
    ember_wraith(); rune_sentinel(); drowned_keeper(); stone_thrall(); guard(); townsfolk(); king()
    total = sum(len(files) for _, _, files in os.walk(OUT))
    print("mob sounds written:", total, "files under", OUT)
