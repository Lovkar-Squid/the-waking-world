"""Quick proportion check of ColossusShapes.humanoid (boxes only, no weathering) rendered as voxels."""
import numpy as np, matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

def humanoid(h):
    r = lambda f: int(round(h * f))
    legLen = max(3, r(0.42)); torsoH = max(3, r(0.36)); headH = max(2, h - legLen - torsoH)
    torsoW = max(3, r(0.30)); torsoD = max(2, r(0.16))
    legW = max(1, r(0.11)); legD = max(1, r(0.13))
    armW = max(1, r(0.09)); armD = max(1, r(0.11)); armLen = max(2, r(0.40))
    headW = max(2, r(0.18)); headD = max(2, r(0.16))
    halfTW = torsoW // 2
    parts = {
      'torso': (-halfTW, legLen, -torsoD//2, torsoW, torsoH, torsoD),
      'legL': (-halfTW, 0, -legD//2, legW, legLen, legD),
      'legR': (halfTW-legW, 0, -legD//2, legW, legLen, legD),
      'armL': (-halfTW-armW, legLen+torsoH-armLen, -armD//2, armW, armLen, armD),
      'armR': (halfTW, legLen+torsoH-armLen, -armD//2, armW, armLen, armD),
      'head': (-headW//2, legLen+torsoH, -headD//2-(1 if h>=24 else 0), headW, headH, headD),
    }
    return parts

for h in (16, 40):
    parts = humanoid(h)
    xs = [p[0] for p in parts.values()] + [p[0]+p[3] for p in parts.values()]
    zs = [p[2] for p in parts.values()] + [p[2]+p[5] for p in parts.values()]
    W = max(xs)-min(xs)+2; D = max(zs)-min(zs)+2; H = h+2
    vox = np.zeros((W, D, H), dtype=int); ox, oz = -min(xs)+1, -min(zs)+1
    colors = {'torso':1,'legL':2,'legR':2,'armL':3,'armR':3,'head':4}
    for name,(x,y,z,sx,sy,sz) in parts.items():
        vox[x+ox:x+ox+sx, z+oz:z+oz+sz, y:y+sy] = colors[name]
    fig = plt.figure(figsize=(7,9)); ax = fig.add_subplot(111, projection='3d')
    cmap = {1:'#8a8f94',2:'#6b6f73',3:'#7d8287',4:'#a0a5aa'}
    filled = vox>0
    fc = np.empty(vox.shape, dtype=object)
    for k,c in cmap.items(): fc[vox==k]=c
    ax.voxels(filled, facecolors=fc, edgecolor=(0,0,0,0.15))
    ax.set_box_aspect((W,D,H)); ax.view_init(elev=10, azim=-125); ax.set_axis_off()
    ax.set_title(f'colossus h={h}: cells={int(filled.sum())}')
    plt.tight_layout(); plt.savefig(f'out/proportions_{h}.png', dpi=110); plt.close()
    print(h, {k:v for k,v in parts.items()}, 'cells', int(filled.sum()))
