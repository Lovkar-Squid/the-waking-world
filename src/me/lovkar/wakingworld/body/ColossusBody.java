package me.lovkar.wakingworld.body;

import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * A complete colossus: its palette, its height and its rigid parts. Built deterministically
 * from (palette, seed, height) on both sides - the server only needs the part boxes for the
 * hitboxes, the client bakes the cells into geometry - so nothing but three ints is synced.
 */
public final class ColossusBody {
    public final Palette palette;
    public final int seed;
    public final int height;
    public final List<PartDef> parts;
    /** Centres of the cores (weak points) in body space, block units, in a stable order. */
    public final List<double[]> cores;

    ColossusBody(Palette palette, int seed, int height, List<PartDef> parts, List<double[]> cores) {
        this.palette = palette;
        this.seed = seed;
        this.height = height;
        this.parts = List.copyOf(parts);
        this.cores = List.copyOf(cores);
    }

    public static ColossusBody build(Palette palette, int seed, int height) {
        return ColossusShapes.beast(palette, seed, height);
    }

    /** Stable identity of a body - the mesh cache key on the client. */
    public long key() {
        return ((long) palette.hash() << 32) ^ ((long) (height & 0xFFFF) << 16) ^ (seed & 0xFFFFFFFFL) ^ ((long) seed << 40);
    }

    /** How each kind of part is cut: {intervals along the longest axis, along the second longest} - fixed, so the part entities never change count. */
    public static int[] sliceSpec(PartDef.Kind kind) {
        switch (kind) {
            case TORSO: return new int[]{10, 3};
            case HEAD: return new int[]{4, 3};
            case LEFT_ARM: case RIGHT_ARM: return new int[]{12, 2};
            default: return new int[]{8, 2};
        }
    }

    /** How many hit boxes each kind of part is cut into. */
    public static int sliceCount(PartDef.Kind kind) {
        int[] spec = sliceSpec(kind);
        return spec[0] * spec[1];
    }

    /** Every colossus has this many cores, each with a hit box of its own. */
    public static final int CORE_BOXES = 5;

    /** Total number of hit boxes of any colossus: the body slices plus one box per core. */
    public static int hitBoxCount() {
        int n = 0;
        for (PartDef.Kind k : PartDef.Kind.values()) n += sliceCount(k);
        return n + CORE_BOXES;
    }

    /** The part a core box rides on: the part whose cells hold the core's centre (the torso when in doubt). */
    public PartDef.Kind coreKind(int i) {
        return ColossusShapes.beastCoreKind(i);
    }

    /** Half the side of a core's hit box: big enough to be aimed at from a distance, and it sticks out of the skin. */
    public double coreBoxHalf() {
        return Math.max(1.7, height * 0.058);
    }

    private List<HitBox> hitBoxes;

    /** Hit boxes in body space, in a stable order (kind order, then slice index); missing parts give pivot-sized boxes at the origin. */
    public List<HitBox> hitBoxes() {
        List<HitBox> list = this.hitBoxes;
        if (list == null) {
            list = new ArrayList<>(hitBoxCount());
            for (PartDef.Kind k : PartDef.Kind.values()) {
                PartDef def = part(k);
                int n = sliceCount(k);
                if (def == null) {
                    for (int i = 0; i < n; i++) list.add(new HitBox(k, i, new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5), -1));
                } else {
                    int[] spec = sliceSpec(k);
                    List<AABB> slices = def.slices(spec[0], spec[1]);
                    // the slices are armour; the cores get boxes of their own below
                    for (int i = 0; i < n; i++) list.add(new HitBox(k, i, slices.get(i), -1));
                }
            }
            double half = coreBoxHalf();
            for (int i = 0; i < CORE_BOXES; i++) {
                if (i < cores.size()) {
                    double[] c = cores.get(i);
                    list.add(new HitBox(coreKind(i), -1, new AABB(c[0] - half, c[1] - half, c[2] - half, c[0] + half, c[1] + half, c[2] + half), i));
                } else {
                    list.add(new HitBox(PartDef.Kind.TORSO, -1, new AABB(-0.5, 0, -0.5, 0.5, 1, 0.5), -1));
                }
            }
            this.hitBoxes = List.copyOf(list);
            list = this.hitBoxes;
        }
        return list;
    }

    /** Index of the core whose centre lies in (or within a block of) the box, or -1. */
    private int coreIn(AABB box) {
        AABB b = box.inflate(1.0);
        for (int i = 0; i < cores.size(); i++) {
            double[] c = cores.get(i);
            if (b.contains(c[0], c[1], c[2])) return i;
        }
        return -1;
    }

    /** One hit box: which part, which slice of it, where (body space), and which core it protects (-1 = plain armour). */
    public record HitBox(PartDef.Kind kind, int index, AABB box, int core) {
    }

    public PartDef part(PartDef.Kind kind) {
        for (PartDef p : parts) if (p.kind == kind) return p;
        return null;
    }

    public int totalCells() {
        int n = 0;
        for (PartDef p : parts) n += p.filled();
        return n;
    }

    /** Half of the widest horizontal extent (arms included), for culling boxes. */
    public double halfWidth() {
        double m = 0;
        for (PartDef p : parts) {
            m = Math.max(m, Math.max(Math.abs(p.ox), Math.abs(p.ox + p.sx)));
            m = Math.max(m, Math.max(Math.abs(p.oz), Math.abs(p.oz + p.sz)));
        }
        return m + 1;
    }
}
