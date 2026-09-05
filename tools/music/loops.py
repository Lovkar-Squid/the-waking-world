import subprocess, numpy as np, os, re
OUT="/root/wakingworld/resources/assets/wakingworld/sounds/music"; TMP="/tmp/music"; SR=44100
def load(f):
    raw = subprocess.run(["ffmpeg","-v","error","-i",f,"-ac","2","-ar",str(SR),"-f","f32le","-"],capture_output=True).stdout
    return np.frombuffer(raw, np.float32).reshape(-1,2).copy()
def save_ogg(x, dst):
    p = subprocess.run(["ffmpeg","-y","-v","error","-f","f32le","-ar",str(SR),"-ac","2","-i","-","-c:a","libvorbis","-q:a","4",dst], input=x.astype(np.float32).tobytes(), capture_output=True)
    if p.returncode: print(p.stderr.decode()[-1000:]); raise SystemExit(1)
def trailing_silence(x, thresh_db=-45.0, win=int(0.02*SR)):
    n = 0
    lim = 10**(thresh_db/20)
    for i in range(len(x)-win, 0, -win):
        if np.sqrt((x[i:i+win]**2).mean()) > lim: break
        n += win
    return n
for n in ["stone","earth","sandstone","ice","prismarine","moss"]:
    a = load(f"{TMP}/{n}.wav")
    L = len(a) - trailing_silence(a) - int(0.02*SR)
    c = int(4.0*SR)
    mid = a[c:L-c]
    tail = a[L-c:L]; head = a[0:c]
    ramp = np.linspace(0,1,c,dtype=np.float32)[:,None]
    xf = tail*(1-ramp) + head*ramp          # ends exactly at a[c-1], the file starts at a[c]
    loop = np.concatenate([mid, xf])
    peak = np.abs(loop).max()
    if peak > 0.98: loop *= 0.98/peak
    save_ogg(loop, f"{OUT}/{n}.ogg")
    print(f"{n}: loop {len(loop)/SR:.2f}s, peak {peak:.2f}, {os.path.getsize(f'{OUT}/{n}.ogg')//1024} KB")
