package me.lovkar.wakingworld.body;

import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Random;

/**
 * The finishing passes of a supporter style (see {@link ColossusStyle}), run on both sides after
 * the body is filled: glowing seams and a visor for the Sentinel, carved gold bands for the
 * Eldest, edge-lights, a visor, lit horn tips and a circlet for the Seraph. Everything here only
 * swaps the block in cells that already exist, so the hit boxes are exactly the plain giant's.
 */
final class StyleDecor {
    private StyleDecor() {
    }

    static void apply(List<PartDef> parts, Palette palette, double H, Random rnd) {
        ColossusStyle style = palette.style();
        if (style == ColossusStyle.NONE) return;
        BlockState glow = palette.core.defaultBlockState();
        for (PartDef p : parts) {
            switch (style) {
                case SENTINEL -> sentinel(p, glow, H);
                case ELDEST -> eldest(p, glow);
                case SERAPH -> seraph(p, glow, H, rnd);
                default -> {
                }
            }
        }
    }

    // ---- the Sentinel ------------------------------------------------------------------------

    private static void sentinel(PartDef p, BlockState glow, double H) {
        switch (p.kind) {
            case TORSO -> {
                ring(p, glow, 0);                                   // the belt
                ring(p, glow, (int) (p.sy * 0.93));                 // the collar
                int cx = p.sx / 2;
                for (int y = (int) (p.sy * 0.30); y < (int) (p.sy * 0.80); y += 3) frontMark(p, glow, cx, y); // the sternum line
                for (int y = (int) (p.sy * 0.30); y < (int) (p.sy * 0.80); y += 3) frontMark(p, glow, cx - 1, y);
            }
            case HEAD -> visor(p, glow);
            case LEFT_ARM, RIGHT_ARM -> {
                ring(p, glow, (int) (p.sy * 0.50));                 // the elbow
                ring(p, glow, (int) (p.sy * 0.50) + 1);
                ring(p, glow, (int) (p.sy * 0.10));                 // the wrist
            }
            default -> {
                ring(p, glow, (int) (p.sy * 0.52));                 // the knee
                ring(p, glow, (int) (p.sy * 0.52) + 1);
                ring(p, glow, (int) (p.sy * 0.12));                 // the ankle
            }
        }
    }

    // ---- the Eldest --------------------------------------------------------------------------

    private static void eldest(PartDef p, BlockState glow) {
        switch (p.kind) {
            case TORSO -> {
                dotted(p, glow, (int) (p.sy * 0.36), 0);
                dotted(p, glow, (int) (p.sy * 0.64), 1);
            }
            case HEAD -> dotted(p, glow, (int) (p.sy * 0.15), 0);   // a carved jaw line
            case LEFT_ARM, RIGHT_ARM -> dotted(p, glow, (int) (p.sy * 0.34), 0);
            default -> dotted(p, glow, (int) (p.sy * 0.30), 1);
        }
    }

    // ---- the Seraph --------------------------------------------------------------------------

    private static void seraph(PartDef p, BlockState glow, double H, Random rnd) {
        switch (p.kind) {
            case TORSO -> {
                edges(p, glow, (int) (p.sy * 0.25), (int) (p.sy * 0.85), 2);   // violet light down the flanks
                int cx = p.sx / 2;
                for (int y = (int) (p.sy * 0.45); y < (int) (p.sy * 0.75); y += 2) frontMark(p, glow, cx, y); // a thin line on the breastplate
            }
            case HEAD -> {
                visor(p, glow);
                crest(p, glow);
            }
            case LEFT_ARM, RIGHT_ARM -> edges(p, glow, (int) (p.sy * 0.15), (int) (p.sy * 0.9), 3);
            default -> edges(p, glow, (int) (p.sy * 0.15), (int) (p.sy * 0.9), 3);
        }
    }

    /**
     * The crest: the head's own horns, antlers or spikes - whatever the land gave it - get lit tips,
     * and a circlet of light runs round the crown of the skull. Nothing is added; only cells change.
     */
    private static void crest(PartDef p, BlockState glow) {
        // the crown row: the highest row still (nearly) as wide as the skull; everything above it is horn
        int[] width = new int[p.sy];
        int widest = 0;
        for (int y = 0; y < p.sy; y++) {
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int z = 0; z < p.sz; z++)
                for (int x = 0; x < p.sx; x++) if (p.get(x, y, z) != null) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
            width[y] = lo == Integer.MAX_VALUE ? 0 : hi - lo + 1;
            widest = Math.max(widest, width[y]);
        }
        int top = -1;
        for (int y = p.sy - 1; y >= 0; y--) if (width[y] >= widest * 0.6) { top = y; break; }
        if (top < 0) return;
        // lit tips: a horn cell with nothing above it, at least two rows clear of the crown, and the cell under the tip too
        for (int y = top + 2; y < p.sy; y++)
            for (int z = 0; z < p.sz; z++)
                for (int x = 0; x < p.sx; x++) {
                    BlockState s = p.get(x, y, z);
                    if (s == null || s.getLightEmission() > 0 || p.get(x, y + 1, z) != null) continue;
                    p.set(x, y, z, glow);
                    BlockState below = p.get(x, y - 1, z);
                    if (y - 1 > top + 1 && below != null && below.getLightEmission() == 0) p.set(x, y - 1, z, glow);
                }
        // the circlet: every other surface cell of the crown row, front half
        for (int z = 0; z < Math.max(1, (p.sz + 1) / 2); z++)
            for (int x = 0; x < p.sx; x++) {
                if ((x + z) % 2 != 0) continue;
                BlockState s = p.get(x, top, z);
                if (s != null && p.isSurface(x, top, z) && s.getLightEmission() == 0) p.set(x, top, z, glow);
            }
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** Every surface cell of the row becomes glow (a seam right round the part). */
    private static void ring(PartDef p, BlockState glow, int y) {
        if (y < 0 || y >= p.sy) return;
        for (int z = 0; z < p.sz; z++)
            for (int x = 0; x < p.sx; x++) {
                BlockState s = p.get(x, y, z);
                if (s != null && p.isSurface(x, y, z) && s.getLightEmission() == 0) p.set(x, y, z, glow);
            }
    }

    /** Every other surface cell of the row (a carved band of glyphs); phase picks which ones. */
    private static void dotted(PartDef p, BlockState glow, int y, int phase) {
        if (y < 0 || y >= p.sy) return;
        for (int z = 0; z < p.sz; z++)
            for (int x = 0; x < p.sx; x++) {
                if ((x + z + phase) % 2 != 0) continue;
                BlockState s = p.get(x, y, z);
                if (s != null && p.isSurface(x, y, z) && s.getLightEmission() == 0) p.set(x, y, z, glow);
            }
    }

    /** The outermost cell on each side (x) of every step-th row, front half only: light along the edges of the plating. */
    private static void edges(PartDef p, BlockState glow, int y0, int y1, int step) {
        for (int y = Math.max(0, y0); y < Math.min(p.sy, y1); y += step) {
            for (int z = 0; z < Math.max(1, p.sz / 2); z++) {
                int lo = -1, hi = -1;
                for (int x = 0; x < p.sx; x++) if (p.get(x, y, z) != null) { if (lo < 0) lo = x; hi = x; }
                if (lo < 0) continue;
                if (p.get(lo, y, z).getLightEmission() == 0) p.set(lo, y, z, glow);
                if (hi != lo && p.get(hi, y, z).getLightEmission() == 0) p.set(hi, y, z, glow);
            }
        }
    }

    /** The first cell from the front (-z) in the column (x, y) becomes glow. */
    private static void frontMark(PartDef p, BlockState glow, int x, int y) {
        if (x < 0 || x >= p.sx || y < 0 || y >= p.sy) return;
        for (int z = 0; z < p.sz; z++) {
            BlockState s = p.get(x, y, z);
            if (s == null) continue;
            if (s.getLightEmission() == 0) p.set(x, y, z, glow);
            return;
        }
    }

    /** A band of light right across the face at the height of the eyes (the rows that already hold lit cells). */
    private static void visor(PartDef p, BlockState glow) {
        boolean[] rows = new boolean[p.sy];
        boolean any = false;
        for (int y = 0; y < p.sy; y++)
            for (int z = 0; z < p.sz && !rows[y]; z++)
                for (int x = 0; x < p.sx; x++) {
                    BlockState s = p.get(x, y, z);
                    if (s != null && s.getLightEmission() > 0) { rows[y] = true; any = true; break; }
                }
        if (!any) return;
        for (int y = 0; y < p.sy; y++) {
            if (!rows[y]) continue;
            // the face's width at this row: leave the outermost cell on each side dark so the visor reads as a slit
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int z = 0; z < p.sz; z++)
                for (int x = 0; x < p.sx; x++) if (p.get(x, y, z) != null) { lo = Math.min(lo, x); hi = Math.max(hi, x); }
            if (hi - lo < 3) continue;
            for (int x = lo + 1; x <= hi - 1; x++) frontMark(p, glow, x, y);
        }
    }
}
