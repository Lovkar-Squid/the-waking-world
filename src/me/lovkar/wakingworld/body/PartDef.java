package me.lovkar.wakingworld.body;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * One rigid piece of a colossus: a box of block cells in body space (integer coordinates,
 * +Y up, the giant's front is -Z, the origin is the ground under the middle of its feet)
 * plus the pivot the piece swings around. Limbs rotate about the X axis at their pivot
 * (legs at the hip, arms at the shoulder), the head turns about Y at the neck.
 */
public final class PartDef {

    public enum Kind { TORSO, HEAD, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG }

    public final Kind kind;
    /** Min corner of the cell box in body space. */
    public final int ox, oy, oz;
    /** Size of the cell box. */
    public final int sx, sy, sz;
    /** Pivot in body space (block units, may be fractional). */
    public final float px, py, pz;
    /** Cells, index = (y * sz + z) * sx + x; null = air. */
    private final BlockState[] cells;
    private int filled;

    public PartDef(Kind kind, int ox, int oy, int oz, int sx, int sy, int sz, float px, float py, float pz) {
        this.kind = kind;
        this.ox = ox; this.oy = oy; this.oz = oz;
        this.sx = sx; this.sy = sy; this.sz = sz;
        this.px = px; this.py = py; this.pz = pz;
        this.cells = new BlockState[sx * sy * sz];
    }

    public int index(int x, int y, int z) {
        return (y * sz + z) * sx + x;
    }

    public boolean inside(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz;
    }

    /** Cell in part-local coordinates (0..s-1). */
    public BlockState get(int x, int y, int z) {
        return inside(x, y, z) ? cells[index(x, y, z)] : null;
    }

    public void set(int x, int y, int z, BlockState state) {
        if (!inside(x, y, z)) return;
        int i = index(x, y, z);
        if (cells[i] == null && state != null) filled++;
        else if (cells[i] != null && state == null) filled--;
        cells[i] = state;
    }

    public void fill(BlockState state) {
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++)
                    set(x, y, z, state);
    }

    public int filled() {
        return filled;
    }

    /** True when the cell is on the outside of the box or borders an empty cell - a candidate for weathering. */
    public boolean isSurface(int x, int y, int z) {
        if (get(x, y, z) == null) return false;
        return get(x + 1, y, z) == null || get(x - 1, y, z) == null || get(x, y + 1, z) == null
                || get(x, y - 1, z) == null || get(x, y, z + 1) == null || get(x, y, z - 1) == null;
    }

    /** The whole cell box in body space, in block units. */
    public AABB box() {
        return new AABB(ox, oy, oz, ox + sx, oy + sy, oz + sz);
    }

    /**
     * The part cut into boxes: {@code k} intervals along its longest axis, each of those into
     * {@code m} intervals along its second-longest axis, every box hugging the cells that fall into
     * its intervals - a long diagonal arm becomes a staircase of small boxes instead of one huge
     * one, a wide torso a grid of them, so a sword swung past the giant no longer counts as a hit
     * and a player leaning on its chest stands where the chest is. Empty intervals yield a 1-block
     * box at the part's pivot (kept so the count is stable). Boxes are in body space, block units.
     */
    public List<AABB> slices(int k, int m) {
        List<AABB> out = new ArrayList<>(k * m);
        int[] size = {sx, sy, sz};
        int a0 = sy >= sx && sy >= sz ? 1 : (sx >= sz ? 0 : 2);            // longest axis
        int a1 = a0 == 0 ? (sy >= sz ? 1 : 2) : a0 == 1 ? (sx >= sz ? 0 : 2) : (sx >= sy ? 0 : 1); // second longest
        int len0 = size[a0], len1 = size[a1];
        int[] c = new int[3];
        for (int i = 0; i < k; i++) {
            int lo0 = len0 * i / k, hi0 = Math.max(lo0 + 1, len0 * (i + 1) / k);
            for (int j = 0; j < m; j++) {
                int lo1 = len1 * j / m, hi1 = Math.max(lo1 + 1, len1 * (j + 1) / m);
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                for (int y = 0; y < sy; y++)
                    for (int z = 0; z < sz; z++)
                        for (int x = 0; x < sx; x++) {
                            c[0] = x; c[1] = y; c[2] = z;
                            int v0 = c[a0], v1 = c[a1];
                            if (v0 < lo0 || v0 >= hi0 || v1 < lo1 || v1 >= hi1 || cells[index(x, y, z)] == null) continue;
                            minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                            minY = Math.min(minY, y); maxY = Math.max(maxY, y);
                            minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                        }
                if (minX == Integer.MAX_VALUE) {
                    out.add(new AABB(px - 0.5, py - 0.5, pz - 0.5, px + 0.5, py + 0.5, pz + 0.5));
                } else {
                    out.add(new AABB(ox + minX, oy + minY, oz + minZ, ox + maxX + 1, oy + maxY + 1, oz + maxZ + 1));
                }
            }
        }
        return out;
    }

    /** {@link #slices(int, int)} along the longest axis only. */
    public List<AABB> slices(int k) {
        return slices(k, 1);
    }

    /** Body-space position of the min corner of a cell, relative to the pivot. */
    public float localX(int x) { return ox + x - px; }
    public float localY(int y) { return oy + y - py; }
    public float localZ(int z) { return oz + z - pz; }

    public boolean isLimb() {
        return kind != Kind.TORSO && kind != Kind.HEAD;
    }

    public boolean isLeg() {
        return kind == Kind.LEFT_LEG || kind == Kind.RIGHT_LEG;
    }

    public boolean isArm() {
        return kind == Kind.LEFT_ARM || kind == Kind.RIGHT_ARM;
    }
}
