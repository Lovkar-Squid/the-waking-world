import subprocess, json, re, sys, os
SRC="/mnt/user-data/uploads/curseforge/waking-world/music"
OUT="/root/wakingworld/resources/assets/wakingworld/sounds/music"
TMP="/tmp/music"
I, TP, LRA = -17.0, -1.5, 11.0
def run(args): 
    r = subprocess.run(args, capture_output=True, text=True)
    if r.returncode != 0: print(r.stderr[-2000:]); sys.exit(1)
    return r
def measure(f):
    r = subprocess.run(["ffmpeg","-v","info","-i",f,"-af",f"loudnorm=I={I}:TP={TP}:LRA={LRA}:print_format=json","-f","null","-"],capture_output=True,text=True)
    j = json.loads(r.stderr[r.stderr.rfind("{"):])
    return j
def normalize(src, dst):
    m = measure(src)
    af = (f"loudnorm=I={I}:TP={TP}:LRA={LRA}:measured_I={m['input_i']}:measured_TP={m['input_tp']}:measured_LRA={m['input_lra']}"
          f":measured_thresh={m['input_thresh']}:offset={m['target_offset']}:linear=true:print_format=summary")
    run(["ffmpeg","-y","-v","error","-i",src,"-af",af,"-ar","44100","-ac","2",dst])
def duration(f):
    return float(run(["ffprobe","-v","error","-show_entries","format=duration","-of","csv=p=0",f]).stdout.strip())
def trailing_silence(f, thresh="-45dB"):
    r = subprocess.run(["ffmpeg","-v","info","-i",f,"-af",f"silencedetect=noise={thresh}:d=0.2","-f","null","-"],capture_output=True,text=True)
    starts = [float(x) for x in re.findall(r"silence_start: ([\d.]+)", r.stderr)]
    ends = [float(x) for x in re.findall(r"silence_end: ([\d.]+)", r.stderr)]
    d = duration(f)
    # a silence that runs to (or nearly to) the end
    if starts and (len(ends) < len(starts) or d - ends[-1] < 0.05):
        return d - starts[-1]
    return 0.0
def loop(name, c=4.0):
    src = f"{SRC}/{name}.mp3"; norm = f"{TMP}/{name}.wav"
    normalize(src, norm)
    d = duration(norm); ts = trailing_silence(norm)
    L = d - ts - 0.02
    fc = (f"[0:a]atrim=start={c}:end={L-c},asetpts=PTS-STARTPTS[mid];"
          f"[0:a]atrim=start={L-c}:end={L},asetpts=PTS-STARTPTS[tail];"
          f"[0:a]atrim=start=0:end={c},asetpts=PTS-STARTPTS[head];"
          f"[tail][head]acrossfade=d={c}:c1=tri:c2=tri[xf];"
          f"[mid][xf]concat=n=2:v=0:a=1[out]")
    dst = f"{OUT}/{name}.ogg"
    run(["ffmpeg","-y","-v","error","-i",norm,"-filter_complex",fc,"-map","[out]","-c:a","libvorbis","-q:a","4",dst])
    print(f"{name}: {d:.2f}s, trailing silence {ts:.2f}s, loop {duration(dst):.2f}s, {os.path.getsize(dst)//1024} KB")
def sting(name, start=None, end=None, fadein=0.0, fadeout=0.0):
    src = f"{SRC}/{name}.mp3"; norm = f"{TMP}/{name}.wav"
    normalize(src, norm)
    args = ["ffmpeg","-y","-v","error","-i",norm]
    af = []
    if start is not None: af.append(f"atrim=start={start}" + (f":end={end}" if end else "") + ",asetpts=PTS-STARTPTS")
    if fadein: af.append(f"afade=t=in:st=0:d={fadein}")
    if fadeout and start is not None and end: af.append(f"afade=t=out:st={end-start-fadeout}:d={fadeout}")
    if af: args += ["-af", ",".join(af)]
    dst = f"{OUT}/{name}.ogg"
    run(args + ["-c:a","libvorbis","-q:a","4",dst])
    print(f"{name}: sting {duration(dst):.2f}s, {os.path.getsize(dst)//1024} KB")
if __name__ == "__main__":
    for n in ["stone","earth","sandstone","ice","prismarine","moss"]: loop(n)
    sting("awakening", start=18.0, end=29.0, fadein=0.35)
    sting("victory")
