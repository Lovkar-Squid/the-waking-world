"""The Titan's three pieces (run after the generic process.py): battle loop with an explicit end
(the generated track fades out over its last seconds - the loop must close before that), the
awakening cut so its big hit lands 10.9 s in (TITAN_WAKE_TICKS = 220 -> 11.0 s), and the victory
piece as is. python3 tools/music/titan.py [battle_end_seconds] [hit_seconds]"""
import subprocess, sys, os, numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from process import normalize, duration, sting, SRC, OUT, TMP
SR = 44100
def load(f):
    raw = subprocess.run(["ffmpeg","-v","error","-i",f,"-ac","2","-ar",str(SR),"-f","f32le","-"],capture_output=True).stdout
    return np.frombuffer(raw, np.float32).reshape(-1,2).copy()
def save_ogg(x, dst):
    p = subprocess.run(["ffmpeg","-y","-v","error","-f","f32le","-ar",str(SR),"-ac","2","-i","-","-c:a","libvorbis","-q:a","4",dst], input=x.astype(np.float32).tobytes(), capture_output=True)
    if p.returncode: print(p.stderr.decode()[-1000:]); raise SystemExit(1)
def loop_until(name, end_s, c_s=4.0):
    norm = f"{TMP}/{name}.wav"
    normalize(f"{SRC}/{name}.mp3", norm)
    a = load(norm)
    L = min(len(a), int(end_s*SR)); c = int(c_s*SR)
    mid = a[c:L-c]; tail = a[L-c:L]; head = a[0:c]
    ramp = np.linspace(0,1,c,dtype=np.float32)[:,None]
    loop = np.concatenate([mid, tail*(1-ramp) + head*ramp])
    peak = np.abs(loop).max()
    if peak > 0.98: loop *= 0.98/peak
    save_ogg(loop, f"{OUT}/{name}.ogg")
    # seam check: the loop's last sample continues into its first
    j = np.abs(loop[-1]-loop[0]).max()
    print(f"{name}: loop {len(loop)/SR:.2f}s (cut at {end_s}s), peak {peak:.2f}, seam jump {j:.4f}, {os.path.getsize(f'{OUT}/{name}.ogg')//1024} KB")
if __name__ == "__main__":
    end = float(sys.argv[1]) if len(sys.argv) > 1 else 139.0
    hit = float(sys.argv[2]) if len(sys.argv) > 2 else 27.25
    os.makedirs(TMP, exist_ok=True)
    if os.path.exists(f"{SRC}/titan.mp3"): loop_until("titan", end)
    if os.path.exists(f"{SRC}/titan_awakening.mp3"):
        start = hit - 10.9
        sting("titan_awakening", start=start, end=start + 17.0, fadein=0.35, fadeout=3.0)
    if os.path.exists(f"{SRC}/titan_victory.mp3"): sting("titan_victory")
