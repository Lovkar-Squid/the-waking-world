package me.lovkar.wakingworld.body;

/**
 * A labelled voxel grid in body space: which part each cell belongs to (0 = air) and whether it is
 * something special (a core, an eye). Plain arrays and ints, no Minecraft types, so shapes can be
 * generated and inspected outside the game (tools/GridDump.java) and previewed in Blender.
 */
public final class VoxelGrid {
    public final int x0, y0, z0, nx, ny, nz;
    /** Nominal body height in blocks. */
    public final int height;
    /** Seed for the block fill, so the geometry pass and the fill pass do not share a random stream. */
    public final long fillSeed;
    private final byte[] label;
    private final byte[] special;

    public VoxelGrid(int x0, int y0, int z0, int nx, int ny, int nz, int height, long fillSeed) {
        this.x0 = x0; this.y0 = y0; this.z0 = z0;
        this.nx = nx; this.ny = ny; this.nz = nz;
        this.height = height;
        this.fillSeed = fillSeed;
        this.label = new byte[nx * ny * nz];
        this.special = new byte[nx * ny * nz];
    }

    private int idx(int i, int j, int k) {
        return (i * ny + j) * nz + k;
    }

    public int label(int i, int j, int k) { return label[idx(i, j, k)]; }
    public int special(int i, int j, int k) { return special[idx(i, j, k)]; }
    public void setLabel(int i, int j, int k, int v) { label[idx(i, j, k)] = (byte) v; }
    public void setSpecial(int i, int j, int k, int v) { special[idx(i, j, k)] = (byte) v; }

    /** Grid-index bounds {i0, j0, k0, i1, j1, k1} of the cells with this label, or null if there are none. */
    public int[] bounds(int id) {
        int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int i = 0; i < nx; i++)
            for (int j = 0; j < ny; j++)
                for (int k = 0; k < nz; k++) {
                    if (label[idx(i, j, k)] != id) continue;
                    b[0] = Math.min(b[0], i); b[3] = Math.max(b[3], i);
                    b[1] = Math.min(b[1], j); b[4] = Math.max(b[4], j);
                    b[2] = Math.min(b[2], k); b[5] = Math.max(b[5], k);
                }
        return b[0] == Integer.MAX_VALUE ? null : b;
    }

    public int count(int id) {
        int n = 0;
        for (byte b : label) if (b == id) n++;
        return n;
    }

    public int filled() {
        int n = 0;
        for (byte b : label) if (b != 0) n++;
        return n;
    }
}
