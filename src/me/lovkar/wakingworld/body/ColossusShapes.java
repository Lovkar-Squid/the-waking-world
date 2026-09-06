package me.lovkar.wakingworld.body;

import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedural bodies. Everything is derived from (palette, seed, height) with java.util.Random,
 * so the server and every client build the exact same giant from three ints.
 *
 * Body space: +Y up, the giant faces -Z, the origin is the ground under the middle of its feet.
 */
public final class ColossusShapes {
    private ColossusShapes() {
    }

    public static ColossusBody humanoid(Palette palette, int seed, int height) {
        int h = Math.max(8, Math.min(96, height));
        Random rnd = new Random(seed * 0x9E3779B97F4A7C15L + h);

        int legLen = Math.max(3, Math.round(h * 0.42f));
        int torsoH = Math.max(3, Math.round(h * 0.36f));
        int headH = Math.max(2, h - legLen - torsoH);

        int torsoW = Math.max(3, Math.round(h * 0.30f));
        int torsoD = Math.max(2, Math.round(h * 0.16f));
        int legW = Math.max(1, Math.round(h * 0.11f));
        int legD = Math.max(1, Math.round(h * 0.13f));
        int armW = Math.max(1, Math.round(h * 0.09f));
        int armD = Math.max(1, Math.round(h * 0.11f));
        int armLen = Math.max(2, Math.round(h * 0.40f));
        int headW = Math.max(2, Math.round(h * 0.18f));
        int headD = Math.max(2, Math.round(h * 0.16f));

        int halfTW = torsoW / 2;
        List<PartDef> parts = new ArrayList<>();

        // Torso - pivot at the hips, front at -Z.
        PartDef torso = new PartDef(PartDef.Kind.TORSO, -halfTW, legLen, -torsoD / 2, torsoW, torsoH, torsoD,
                0f, legLen, 0f);
        fillRandom(torso, palette, rnd);
        // waist taper: the lower third of the torso loses its outer columns
        int waist = Math.max(1, torsoH / 3);
        for (int y = 0; y < waist; y++) {
            int cut = (waist - y) * Math.max(1, torsoW / 8) / waist + 1; // 1..torsoW/8+1 columns per side
            for (int c = 0; c < cut; c++) {
                for (int z = 0; z < torsoD; z++) {
                    torso.set(c, y, z, null);
                    torso.set(torsoW - 1 - c, y, z, null);
                }
            }
        }
        // chest core, 2x2x2 (or 1 block on small giants) on the front face
        int cs = torsoW >= 6 ? 2 : 1;
        int cx = torsoW / 2 - cs / 2, cy = torsoH * 3 / 5;
        for (int dx = 0; dx < cs; dx++)
            for (int dy = 0; dy < cs; dy++)
                for (int dz = 0; dz < Math.min(cs, torsoD); dz++)
                    torso.set(cx + dx, cy + dy, dz, palette.core.defaultBlockState());
        weather(torso, rnd, 0.06f, false);
        parts.add(torso);

        // Legs - pivot at the hip (top of the leg). Left leg on -X, right leg on +X.
        int legZ = -legD / 2;
        PartDef leftLeg = new PartDef(PartDef.Kind.LEFT_LEG, -halfTW, 0, legZ, legW, legLen, legD,
                -halfTW + legW / 2f, legLen, 0f);
        PartDef rightLeg = new PartDef(PartDef.Kind.RIGHT_LEG, halfTW - legW, 0, legZ, legW, legLen, legD,
                halfTW - legW / 2f, legLen, 0f);
        for (PartDef leg : new PartDef[]{leftLeg, rightLeg}) {
            fillRandom(leg, palette, rnd);
            // knee core on the front
            int ky = Math.max(1, Math.round(legLen * 0.45f));
            int kx = legW / 2;
            leg.set(kx, ky, 0, palette.core.defaultBlockState());
            if (legW >= 4) leg.set(kx - 1, ky, 0, palette.core.defaultBlockState());
            weather(leg, rnd, 0.05f, true);
            parts.add(leg);
        }

        // Arms - pivot at the shoulder, hanging outside the torso.
        int shoulderY = legLen + torsoH;
        int armY = shoulderY - armLen;
        int armZ = -armD / 2;
        PartDef leftArm = new PartDef(PartDef.Kind.LEFT_ARM, -halfTW - armW, armY, armZ, armW, armLen, armD,
                -halfTW - armW / 2f, shoulderY - 1f, 0f);
        PartDef rightArm = new PartDef(PartDef.Kind.RIGHT_ARM, halfTW, armY, armZ, armW, armLen, armD,
                halfTW + armW / 2f, shoulderY - 1f, 0f);
        for (PartDef arm : new PartDef[]{leftArm, rightArm}) {
            fillRandom(arm, palette, rnd);
            int ey = Math.max(1, Math.round(armLen * 0.5f));
            arm.set(armW / 2, ey, 0, palette.core.defaultBlockState());
            weather(arm, rnd, 0.05f, false);
            parts.add(arm);
        }

        // Head - pivot at the neck, pushed a block forward so it broods over the chest.
        PartDef head = new PartDef(PartDef.Kind.HEAD, -headW / 2, shoulderY, -headD / 2 - (h >= 24 ? 1 : 0), headW, headH, headD,
                0f, shoulderY, 0f);
        fillRandom(head, palette, rnd);
        // eyes on the front face
        int eyeY = Math.max(0, Math.min(headH - 1, Math.round(headH * 0.55f)));
        int eyeDx = Math.max(1, headW / 4);
        BlockState eye = palette.eye.defaultBlockState();
        head.set(headW / 2 - eyeDx, eyeY, 0, eye);
        head.set(headW / 2 + eyeDx - (headW % 2 == 0 ? 1 : 0), eyeY, 0, eye);
        // a brow: shave the top front edge
        for (int x = 0; x < headW; x++) head.set(x, headH - 1, 0, null);
        weather(head, rnd, 0.04f, false);
        parts.add(head);

        return new ColossusBody(palette, seed, h, parts, List.of());
    }


    // ------------------------------------------------------------------------------------------
    // The beast: a hunched, knuckle-walking mountain of rock. Designed in Blender as a union of
    // ellipsoids and tapered capsules (see tools/colossus_blender.py), reproduced here in units of
    // the body height so it comes out the same at 20 blocks and at 80, with a little variation per
    // seed: horn length, spine spikes, shoulder mass, forearm thickness, jaw.
    // ------------------------------------------------------------------------------------------

    private static final int L_TORSO = 1, L_HEAD = 2, L_RARM = 3, L_LARM = 4, L_RLEG = 5, L_LLEG = 6;
    private static final int S_CORE = 7, S_EYE = 8;

    /**
     * One kind's build, in units of the body height. Every knob is a multiplier or a switch on the
     * base beast, so all kinds share pivots, cores and animation and differ in silhouette: the
     * brute of stone, the mound of earth, the tall crowned desert titan, the shard-covered ice
     * giant, the finned and shelled sea titan, the antlered ent of moss, and the Titan of the End.
     */
    record Style(double torsoW, double torsoD, double belly, double shoulder, double lean,
                 double armThick, double armLen, double fist, double legThick, double foot,
                 double headScale, double neckDrop, double jaw, double brow,
                 double hornLen, double hornUp, double hornOut, double hornBack,
                 int spineSpikes, double spikeLen, int shoulderSpikes, boolean armPlates, boolean knuckleSpikes,
                 boolean chestPlate, boolean crown, boolean antlers, boolean fins, boolean beard, boolean boulders,
                 boolean shells, boolean shards, boolean crest, boolean wings, double spikeThick) {

        static Style of(String kind) {
            return switch (kind) {
                // broad, low, half a hill: no spikes, boulders on the back, stubby horns, arms like tree trunks
                case "earth" -> new Style(1.25, 1.2, 1.25, 1.15, -28, 1.25, 0.95, 1.2, 1.25, 1.2,
                        1.05, 0.05, 0.9, 1.2, 0.45, 0.4, 1.0, 0.2, 0, 0, 0, false, false,
                        false, false, false, false, false, true, false, false, false, false, 1.0);
                // tall, lean, angular, a crowned pharaoh of the dunes with blade-like forearms
                case "sandstone" -> new Style(0.85, 0.85, 0.75, 0.9, -8, 0.8, 1.1, 0.9, 0.8, 0.9,
                        1.1, -0.04, 1.15, 0.8, 0.9, 1.6, 0.3, -0.3, 3, 0.7, 2, true, false,
                        true, true, false, false, false, false, false, false, true, false, 0.8);
                // a walking glacier: long backswept icicle horns, shards everywhere, a spiked crown, a thin waist
                case "ice" -> new Style(0.95, 0.9, 0.85, 1.05, -16, 0.9, 1.0, 0.95, 0.9, 0.95,
                        1.0, -0.02, 1.05, 1.0, 1.5, 0.6, 0.7, 1.4, 9, 1.4, 4, true, true,
                        true, true, false, false, false, false, false, true, false, false, 0.8);
                // the sea titan: barrel chest, shells on the shoulders, fins down the spine and forearms, a beard of tendrils
                case "prismarine" -> new Style(1.15, 1.15, 1.1, 1.2, -18, 1.1, 1.05, 1.15, 1.05, 1.3,
                        1.1, 0.0, 1.1, 1.1, 0.6, 0.2, 1.4, 0.6, 0, 0, 0, false, false,
                        true, false, false, true, true, false, true, false, true, false, 1.0);
                // the ent: hunched, long knuckle-dragging arms, branching antlers, a beard of roots, soft shoulders
                case "moss" -> new Style(1.05, 1.05, 1.15, 1.1, -30, 1.0, 1.25, 1.1, 1.05, 1.1,
                        1.0, 0.06, 0.85, 1.3, 1.2, 1.1, 0.8, 0.5, 4, 0.9, 0, false, false,
                        false, false, true, false, true, false, false, false, false, false, 1.1);
                // the last one: everything the others have, larger, and wings of obsidian plate
                case "titan" -> new Style(1.1, 1.05, 1.0, 1.25, -14, 1.1, 1.05, 1.15, 1.05, 1.1,
                        1.15, -0.02, 1.2, 1.1, 1.6, 1.0, 0.9, 1.0, 9, 1.5, 4, true, true,
                        true, true, false, false, false, false, false, true, true, true, 1.0);
                // stone: the base beast
                default -> new Style(1.0, 1.0, 1.0, 1.0, -20, 1.0, 1.0, 1.0, 1.0, 1.0,
                        1.0, 0.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 7, 1.0, 3, true, true,
                        true, false, false, false, false, false, false, false, false, false, 1.0);
            };
        }
    }

    /** The stone beast's label grid (the tools use this one). */
    public static VoxelGrid beastGrid(int seed, int height) {
        return beastGrid("stone", seed, height);
    }

    /** A kind's label grid: which cell belongs to which part, plus cores and eyes. No Minecraft types - testable headless. */
    public static VoxelGrid beastGrid(String kind, int seed, int height) {
        int h = Math.max(8, Math.min(96, height));
        Random rnd = new Random(seed * 0x9E3779B97F4A7C15L + h * 31L);
        Style st = Style.of(kind);
        double horn = (0.8 + 0.5 * rnd.nextDouble()) * st.hornLen();
        double shoulder = (0.9 + 0.25 * rnd.nextDouble()) * st.shoulder();
        double forearm = (0.9 + 0.2 * rnd.nextDouble()) * st.armThick();
        double jaw = (0.9 + 0.3 * rnd.nextDouble()) * st.jaw();
        int spineSpikes = st.spineSpikes() == 0 ? 0 : Math.max(2, st.spineSpikes() - 1 + rnd.nextInt(3));
        double lean = st.lean() - 6 * rnd.nextDouble();
        double tw = st.torsoW(), td = st.torsoD(), belly = st.belly();
        double thick = st.spikeThick();

        List<Prim> torso = new ArrayList<>();
        torso.add(Prim.ell(0, 0.50, 0.02, 0.18 * belly, 0.15, 0.14 * belly));
        torso.add(Prim.ell(0, 0.67, 0.07, 0.26 * tw, 0.14, 0.16 * td, lean));
        torso.add(Prim.ell(0.27 * tw, 0.72, 0.03, 0.12 * shoulder, 0.10 * shoulder, 0.12 * shoulder));
        torso.add(Prim.ell(-0.27 * tw, 0.72, 0.03, 0.12 * shoulder, 0.10 * shoulder, 0.12 * shoulder));
        if (st.chestPlate()) torso.add(Prim.ell(0, 0.58, -0.10 * td, 0.12 * tw, 0.09, 0.06));
        torso.add(Prim.cap(0, 0.70, 0.0, 0, 0.78 - st.neckDrop(), -0.09, 0.07));        // neck
        torso.add(Prim.ell(0, 0.36, 0.02, 0.16 * tw, 0.08, 0.12 * td));                    // pelvis
        for (int i = 0; i < spineSpikes; i++) {
            double t = spineSpikes == 1 ? 0 : i / (double) (spineSpikes - 1);
            double sy = 0.76 - 0.30 * t, sz = 0.17 * td - 0.03 * t, sx = (i % 2 == 1 ? 0.04 : -0.04) * (0.6 + 0.8 * rnd.nextDouble());
            double len = (0.18 - 0.10 * t) * (0.8 + 0.4 * rnd.nextDouble()) * st.spikeLen();
            torso.add(Prim.cap(sx, sy, sz, sx * 1.6, sy + len * 0.7, sz + len * 0.9, 0.045 * thick, 0.0));
        }
        if (st.shoulderSpikes() > 0) {
            for (double sx : new double[]{0.11, -0.11}) {
                for (int i = 0; i < st.shoulderSpikes(); i++) {
                    double t = st.shoulderSpikes() == 1 ? 0 : i / (double) (st.shoulderSpikes() - 1);
                    double sy = 0.70 - 0.16 * t, sz = 0.16 * td - 0.02 * t, len = 0.09 * (0.8 + 0.5 * rnd.nextDouble()) * st.spikeLen();
                    torso.add(Prim.cap(sx, sy, sz, sx * 1.3, sy + len * 0.6, sz + len, 0.03 * thick, 0.0));
                }
            }
            for (int s = 1; s >= -1; s -= 2) {
                double k = (0.85 + 0.3 * rnd.nextDouble()) * st.spikeLen();
                torso.add(Prim.cap(s * 0.29 * tw, 0.80, 0.03, s * 0.36 * tw, 0.80 + 0.14 * k, 0.07, 0.035 * thick, 0.0));
                torso.add(Prim.cap(s * 0.24 * tw, 0.81, 0.00, s * 0.26 * tw, 0.81 + 0.11 * k, 0.02, 0.03 * thick, 0.0));
                torso.add(Prim.cap(s * 0.33 * tw, 0.76, 0.08, s * 0.42 * tw, 0.84, 0.13, 0.03 * thick, 0.0));
            }
        }
        if (st.boulders()) {
            // lumps of hillside riding on the back and the shoulders
            for (int i = 0; i < 7; i++) {
                double bx = (rnd.nextDouble() - 0.5) * 0.44 * tw, by = 0.52 + rnd.nextDouble() * 0.28, bz = 0.12 + rnd.nextDouble() * 0.10;
                double r = 0.05 + rnd.nextDouble() * 0.06;
                torso.add(Prim.ell(bx, by, bz, r * 1.2, r, r * 1.1));
            }
        }
        if (st.shells()) {
            torso.add(Prim.ell(0.30 * tw, 0.78, 0.03, 0.14, 0.09, 0.14));
            torso.add(Prim.ell(-0.30 * tw, 0.78, 0.03, 0.14, 0.09, 0.14));
        }
        if (st.fins()) {
            // a dorsal fin down the spine and a crest on each shoulder: thin, tall, long
            torso.add(Prim.ell(0, 0.72, 0.16 * td, 0.02, 0.16, 0.10));
            torso.add(Prim.ell(0, 0.54, 0.16 * td, 0.02, 0.12, 0.08));
        }
        if (st.wings()) {
            // plates of obsidian sweeping up and back off the shoulder blades
            for (int s = 1; s >= -1; s -= 2) {
                torso.add(Prim.cap(s * 0.18, 0.74, 0.12, s * 0.50, 1.02, 0.30, 0.06, 0.02));
                torso.add(Prim.cap(s * 0.22, 0.66, 0.13, s * 0.46, 0.84, 0.34, 0.05, 0.015));
                torso.add(Prim.cap(s * 0.34, 0.88, 0.21, s * 0.62, 1.10, 0.36, 0.035, 0.0));
            }
        }
        if (st.shards()) {
            // shards of ice jutting from the back and the hips
            for (int i = 0; i < 10; i++) {
                double sx = (rnd.nextDouble() - 0.5) * 0.4 * tw, sy = 0.40 + rnd.nextDouble() * 0.38, sz = 0.10 + rnd.nextDouble() * 0.08;
                double len = 0.08 + rnd.nextDouble() * 0.14;
                torso.add(Prim.cap(sx, sy, sz, sx * 1.8, sy + len * (0.3 + rnd.nextDouble() * 0.6), sz + len, 0.03, 0.0));
            }
        }

        double hs = st.headScale(), hy = -st.neckDrop();
        List<Prim> head = new ArrayList<>();
        head.add(Prim.ell(0, 0.77 + hy, -0.13, 0.12 * hs, 0.10 * hs, 0.13 * hs));
        head.add(Prim.cap(-0.12 * hs, 0.82 + hy, -0.22, 0.12 * hs, 0.82 + hy, -0.22, 0.04 * st.brow()));      // brow
        head.add(Prim.ell(0, 0.745 + hy, -0.24 * jaw, 0.10 * hs, 0.045, 0.08));                             // upper jaw
        head.add(Prim.cap(0, 0.63 + hy, -0.12, 0, 0.615 + hy, -0.27 * jaw, 0.075, 0.05));                    // lower jaw
        double hu = st.hornUp(), ho = st.hornOut(), hb = st.hornBack();
        if (!st.antlers()) {
            head.add(Prim.cap(0.10 * hs, 0.84 + hy, -0.10, 0.10 * hs + 0.11 * horn * ho, 0.84 + hy + 0.15 * horn * hu, -0.10 + 0.10 * horn * hb, 0.04, 0.0));
            head.add(Prim.cap(-0.10 * hs, 0.84 + hy, -0.10, -0.10 * hs - 0.11 * horn * ho, 0.84 + hy + 0.15 * horn * hu, -0.10 + 0.10 * horn * hb, 0.04, 0.0));
        } else {
            // antlers: a beam each side that forks twice
            for (int s = 1; s >= -1; s -= 2) {
                double bx = s * 0.09 * hs, by = 0.85 + hy, bz = -0.08;
                double tx = s * 0.22, ty = by + 0.22 * horn, tz = -0.02;
                head.add(Prim.cap(bx, by, bz, tx, ty, tz, 0.035, 0.02));
                head.add(Prim.cap(bx + (tx - bx) * 0.45, by + (ty - by) * 0.45, bz, s * 0.12, ty + 0.10 * horn, -0.12, 0.02, 0.0));
                head.add(Prim.cap(tx, ty, tz, tx + s * 0.08, ty + 0.12 * horn, tz + 0.06, 0.022, 0.0));
                head.add(Prim.cap(tx, ty, tz, tx + s * 0.02, ty + 0.14 * horn, tz - 0.08, 0.022, 0.0));
            }
        }
        if (st.crown()) {
            // a crown of spikes around the top of the skull
            int n = 6;
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                double cx = Math.sin(a) * 0.09 * hs, cz = -0.13 + Math.cos(a) * 0.10 * hs;
                double len = 0.09 + 0.05 * rnd.nextDouble();
                head.add(Prim.cap(cx, 0.85 + hy, cz, cx * 1.5, 0.85 + hy + len, cz + (cz + 0.13) * 0.4, 0.025 * thick, 0.0));
            }
        }
        if (st.crest()) {
            // a fin-crest from the brow back over the skull
            head.add(Prim.ell(0, 0.90 + hy, -0.10, 0.02, 0.10, 0.13));
        }
        if (st.beard()) {
            // tendrils / roots hanging from the jaw
            for (int i = 0; i < 6; i++) {
                double bx = (i - 2.5) * 0.03, len = 0.08 + rnd.nextDouble() * 0.08;
                head.add(Prim.cap(bx, 0.62 + hy, -0.24 * jaw, bx * 1.3, 0.62 + hy - len, -0.24 * jaw - 0.03, 0.018, 0.012));
            }
        }
        for (double tx : new double[]{-0.06, -0.02, 0.02, 0.06}) {
            head.add(Prim.cap(tx * hs, 0.70 + hy, -0.29 * jaw, tx * hs, 0.665 + hy, -0.29 * jaw, 0.014, 0.0));
        }
        for (double tx : new double[]{-0.045, 0.0, 0.045}) {
            head.add(Prim.cap(tx * hs, 0.66 + hy, -0.29 * jaw, tx * hs, 0.69 + hy, -0.29 * jaw, 0.014, 0.0));
        }

        double at = st.armThick(), al = st.armLen(), fist = st.fist();
        List<Prim> rightArm = new ArrayList<>();
        // the elbow stays where the core is; the forearm reaches as far down as the style says
        double handY = 0.085 - 0.10 * (al - 1.0), handZ = -0.25 - 0.06 * (al - 1.0);
        rightArm.add(Prim.cap(0.27 * tw, 0.70, 0.03, 0.37, 0.40, -0.05, 0.08 * at, 0.07 * at));
        rightArm.add(Prim.cap(0.37, 0.40, -0.05, 0.31, handY + 0.035, handZ + 0.04, 0.075 * forearm, 0.09 * forearm));
        rightArm.add(Prim.ell(0.30, handY, handZ, 0.095 * fist, 0.08 * fist, 0.10 * fist));
        if (st.armPlates()) {
            rightArm.add(Prim.cap(0.42, 0.34, -0.06, 0.50, 0.38, -0.08, 0.03 * thick, 0.0));
            rightArm.add(Prim.cap(0.40, 0.26, -0.11, 0.47, 0.28, -0.14, 0.03 * thick, 0.0));
        }
        if (st.knuckleSpikes()) {
            rightArm.add(Prim.cap(0.28, handY + 0.075, handZ - 0.03, 0.28, handY + 0.135, handZ - 0.06, 0.025, 0.0));
            rightArm.add(Prim.cap(0.34, handY + 0.065, handZ - 0.01, 0.36, handY + 0.125, handZ - 0.04, 0.025, 0.0));
        }
        if (st.fins()) {
            rightArm.add(Prim.ell(0.36, 0.26, -0.13, 0.02, 0.08, 0.09));
        }
        if (st.shards()) {
            for (int i = 0; i < 4; i++) {
                double t = 0.2 + 0.6 * rnd.nextDouble();
                double px = 0.37 - 0.06 * t, py = 0.40 - 0.28 * t, pz = -0.05 - 0.16 * t;
                rightArm.add(Prim.cap(px, py, pz, px + 0.10, py + 0.03, pz - 0.03, 0.025, 0.0));
            }
        }

        double lt = st.legThick(), ft = st.foot();
        List<Prim> rightLeg = new ArrayList<>();
        rightLeg.add(Prim.cap(0.12, 0.36, 0.02, 0.15, 0.19, -0.07, 0.09 * lt, 0.075 * lt));
        rightLeg.add(Prim.cap(0.15, 0.19, -0.07, 0.13, 0.06, -0.01, 0.07 * lt, 0.065 * lt));
        rightLeg.add(Prim.ell(0.13, 0.04, -0.06, 0.085 * ft, 0.045, 0.12 * ft));
        rightLeg.add(Prim.cap(0.10, 0.04, -0.17 * ft, 0.09, 0.05, -0.23 * ft, 0.025, 0.0));
        rightLeg.add(Prim.cap(0.16, 0.04, -0.17 * ft, 0.17, 0.05, -0.23 * ft, 0.025, 0.0));
        if (st.shards()) rightLeg.add(Prim.cap(0.15, 0.20, -0.14, 0.15, 0.24, -0.22, 0.025, 0.0));

        List<Prim> leftArm = new ArrayList<>();
        for (Prim p : rightArm) leftArm.add(p.mirror());
        List<Prim> leftLeg = new ArrayList<>();
        for (Prim p : rightLeg) leftLeg.add(p.mirror());

        double H = h;
        int x0 = (int) Math.floor(-0.66 * H), x1 = (int) Math.ceil(0.66 * H);
        int y0 = 0, y1 = (int) Math.ceil(1.16 * H);
        int z0 = (int) Math.floor(-0.48 * H), z1 = (int) Math.ceil(0.48 * H);
        VoxelGrid g = new VoxelGrid(x0, y0, z0, x1 - x0, y1 - y0, z1 - z0, h, rnd.nextLong());

        paint(g, H, head, L_HEAD);
        paint(g, H, rightArm, L_RARM);
        paint(g, H, leftArm, L_LARM);
        paint(g, H, rightLeg, L_RLEG);
        paint(g, H, leftLeg, L_LLEG);
        paint(g, H, torso, L_TORSO);

        for (double[] c : BEAST_CORES) {
            mark(g, H, c[0], c[1], c[2], 1.4, S_CORE, (int) c[3], false);
        }
        mark(g, H, 0.055 * hs, 0.795 + hy, -0.245, 0.9, S_EYE, L_HEAD, true);
        mark(g, H, -0.055 * hs, 0.795 + hy, -0.245, 0.9, S_EYE, L_HEAD, true);
        return g;
    }

    /** The beast's cores (weak points): chest, right/left elbow, right/left knee - x, y, z in units of height, then the part label. */
    private static final double[][] BEAST_CORES = {{0, 0.60, -0.155, L_TORSO}, {0.37, 0.40, -0.12, L_RARM}, {-0.37, 0.40, -0.12, L_LARM},
            {0.15, 0.19, -0.145, L_RLEG}, {-0.15, 0.19, -0.145, L_LLEG}};

    /** The part each core sits in, in the order of {@link #beastCores}. */
    public static PartDef.Kind beastCoreKind(int i) {
        if (i < 0 || i >= BEAST_CORES.length) return PartDef.Kind.TORSO;
        return BEAST_KINDS[(int) BEAST_CORES[i][3]];
    }

    /** Core centres in body blocks for a beast of this height. */
    public static List<double[]> beastCores(int height) {
        int h = Math.max(8, Math.min(96, height));
        List<double[]> out = new ArrayList<>();
        for (double[] c : BEAST_CORES) out.add(new double[]{c[0] * h, c[1] * h, c[2] * h});
        return out;
    }

    /** Pivots of the beast's parts, in units of the body height, indexed by label. */
    private static final double[][] BEAST_PIVOTS = {null, {0, 0.36, 0.0}, {0, 0.72, -0.05}, {0.27, 0.70, 0.03}, {-0.27, 0.70, 0.03},
            {0.12, 0.36, 0.02}, {-0.12, 0.36, 0.02}};
    private static final PartDef.Kind[] BEAST_KINDS = {null, PartDef.Kind.TORSO, PartDef.Kind.HEAD, PartDef.Kind.RIGHT_ARM,
            PartDef.Kind.LEFT_ARM, PartDef.Kind.RIGHT_LEG, PartDef.Kind.LEFT_LEG};

    public static ColossusBody beast(Palette palette, int seed, int height) {
        VoxelGrid g = beastGrid(palette.kind, seed, height);
        Random rnd = new Random(g.fillSeed);
        // the veins get a stream of their own, so a style that goes without them leaves the fill and the weathering
        // - and so the hit boxes - exactly as they are on the plain giant
        Random veinRnd = new Random(g.fillSeed * 31L + 17L);
        double H = g.height;
        BlockState core = palette.core.defaultBlockState();
        BlockState eye = palette.eye.defaultBlockState();
        ColossusStyle style = palette.style(); // a supporter's cosmetic: other blocks, other glow, the same shape
        List<PartDef> parts = new ArrayList<>();
        for (int id = 1; id <= 6; id++) {
            int[] b = g.bounds(id);
            if (b == null) continue;
            double[] pv = BEAST_PIVOTS[id];
            PartDef part = new PartDef(BEAST_KINDS[id], g.x0 + b[0], g.y0 + b[1], g.z0 + b[2],
                    b[3] - b[0] + 1, b[4] - b[1] + 1, b[5] - b[2] + 1,
                    (float) (pv[0] * H), (float) (pv[1] * H), (float) (pv[2] * H));
            for (int i = b[0]; i <= b[3]; i++)
                for (int j = b[1]; j <= b[4]; j++)
                    for (int k = b[2]; k <= b[5]; k++) {
                        if (g.label(i, j, k) != id) continue;
                        int sp = g.special(i, j, k);
                        BlockState s = sp == S_CORE ? core : sp == S_EYE ? eye : palette.pick(rnd);
                        part.set(i - b[0], j - b[1], k - b[2], s);
                    }
            weather(part, rnd, 0.025f, part.isLeg());
            if (style.veins) runes(part, palette, veinRnd, H);
            parts.add(part);
        }
        halos(parts, beastCores(g.height), palette);
        StyleDecor.apply(parts, palette, H, veinRnd);
        return new ColossusBody(palette, seed, g.height, parts, beastCores(g.height));
    }

    /**
     * A ring of glow around every core: the surface cells a block or two out from each core's
     * centre. The renderer groups glow near a core with it, so the halo dies when the core is broken.
     */
    private static void halos(List<PartDef> parts, List<double[]> cores, Palette palette) {
        BlockState glow = palette.core.defaultBlockState();
        for (double[] c : cores) {
            for (PartDef p : parts) {
                for (int y = 0; y < p.sy; y++)
                    for (int z = 0; z < p.sz; z++)
                        for (int x = 0; x < p.sx; x++) {
                            BlockState s = p.get(x, y, z);
                            if (s == null || isCore(p, s) || !p.isSurface(x, y, z)) continue;
                            double dx = p.ox + x + 0.5 - c[0], dy = p.oy + y + 0.5 - c[1], dz = p.oz + z + 0.5 - c[2];
                            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                            if (d >= 1.4 && d <= 2.45) p.set(x, y, z, glow);
                        }
            }
        }
    }

    /**
     * Glowing runes: veins of the palette's glow block crawling over the surface - random walks
     * from a few seeds along the outside of each part (mirrored across the body's centre line on
     * the torso and the head, so the chest and the face carry a symmetric mark), plus a ring around
     * every core. The renderer lights anything that emits light, and veins that lie near a core
     * belong to it and go dark with it.
     */
    private static void runes(PartDef p, Palette palette, Random rnd, double H) {
        BlockState glow = palette.core.defaultBlockState();
        int walks = switch (p.kind) {
            case TORSO -> 7;
            case HEAD -> 3;
            case LEFT_ARM, RIGHT_ARM -> 4;
            default -> 3;
        };
        int len = Math.max(5, (int) (H * 0.26));
        boolean mirror = p.kind == PartDef.Kind.TORSO || p.kind == PartDef.Kind.HEAD;
        int mirrorX = (int) Math.round(-p.ox * 2 - 1); // cell x of the mirror of x: (-(ox + x + 1) - ox) -> 2*(-ox) - 1 - x
        tattoos(p, glow, rnd, H, mirrorX);
        for (int w = 0; w < walks; w++) {
            int[] c = randomSurface(p, rnd);
            if (c == null) return;
            int dx = 0, dy = rnd.nextBoolean() ? 1 : -1, dz = 0;
            for (int i = 0; i < len; i++) {
                if (p.get(c[0], c[1], c[2]) != null && !isCore(p, p.get(c[0], c[1], c[2]))) {
                    p.set(c[0], c[1], c[2], glow);
                    if (mirror) {
                        int mx = mirrorX - c[0];
                        if (p.inside(mx, c[1], c[2]) && p.get(mx, c[1], c[2]) != null && !isCore(p, p.get(mx, c[1], c[2]))) p.set(mx, c[1], c[2], glow);
                    }
                }
                // step to a neighbouring surface cell, keeping the direction most of the time (long strokes, not blots)
                if (rnd.nextInt(7) == 0) {
                    int r = rnd.nextInt(3);
                    dx = r == 0 ? (rnd.nextBoolean() ? 1 : -1) : 0;
                    dy = r == 1 ? (rnd.nextBoolean() ? 1 : -1) : (r == 0 ? 0 : dy);
                    dz = r == 2 ? (rnd.nextBoolean() ? 1 : -1) : 0;
                    if (dx == 0 && dy == 0 && dz == 0) dy = 1;
                }
                int[] next = {c[0] + dx, c[1] + dy, c[2] + dz};
                if (!p.inside(next[0], next[1], next[2]) || p.get(next[0], next[1], next[2]) == null || !p.isSurface(next[0], next[1], next[2])) {
                    // slide sideways to stay on the surface
                    int[] alt = null;
                    for (int tries = 0; tries < 6 && alt == null; tries++) {
                        int ax = c[0] + rnd.nextInt(3) - 1, ay = c[1] + rnd.nextInt(3) - 1, az = c[2] + rnd.nextInt(3) - 1;
                        if (p.inside(ax, ay, az) && p.get(ax, ay, az) != null && p.isSurface(ax, ay, az) && !isCore(p, p.get(ax, ay, az))) alt = new int[]{ax, ay, az};
                    }
                    if (alt == null) break;
                    next = alt;
                }
                c = next;
            }
        }
    }

    /**
     * The deliberate marks, on top of the veins: bands of glow around the wrists, elbows, knees
     * and ankles and a belt around the waist; a sigil on the chest (a diamond with a line falling
     * from it); a stripe up the forehead. All of it on the outside skin only, symmetric where the
     * body is.
     */
    private static void tattoos(PartDef p, BlockState glow, Random rnd, double H, int mirrorX) {
        switch (p.kind) {
            case LEFT_ARM, RIGHT_ARM -> {
                band(p, glow, (int) (p.sy * 0.16));
                band(p, glow, (int) (p.sy * 0.50));
                if (rnd.nextBoolean()) band(p, glow, (int) (p.sy * 0.50) + 2);
            }
            case LEFT_LEG, RIGHT_LEG -> {
                band(p, glow, (int) (p.sy * 0.12));
                band(p, glow, (int) (p.sy * 0.56));
            }
            case TORSO -> {
                band(p, glow, (int) (p.sy * 0.27));
                // the chest sigil: a diamond ring on the front, a line falling from it to the belt
                int r = Math.max(2, (int) Math.round(H * 0.07));
                int cy = (int) (p.sy * 0.48); // mid-chest: below the chin, above the belt
                double cx = mirrorX / 2.0;
                for (int dy = -r; dy <= r; dy++) {
                    int dx = r - Math.abs(dy);
                    frontMark(p, glow, (int) Math.floor(cx - dx), cy + dy);
                    frontMark(p, glow, (int) Math.ceil(cx + dx), cy + dy);
                }
                for (int y = cy - r - 1; y > (int) (p.sy * 0.27) + 1; y--) {
                    frontMark(p, glow, (int) Math.floor(cx), y);
                    frontMark(p, glow, (int) Math.ceil(cx), y);
                }
                // and a line over each shoulder, out from the diamond's top corner
                for (int i = 1; i <= r + 2; i++) {
                    frontMark(p, glow, (int) Math.floor(cx) - r - i, cy + r + Math.min(i, 2));
                    frontMark(p, glow, (int) Math.ceil(cx) + r + i, cy + r + Math.min(i, 2));
                }
            }
            case HEAD -> {
                double cx = mirrorX / 2.0;
                for (int y = (int) (p.sy * 0.72); y < p.sy - 1; y++) {
                    frontMark(p, glow, (int) Math.floor(cx), y);
                    frontMark(p, glow, (int) Math.ceil(cx), y);
                }
                // a stroke under each eye, running down and out
                int ey = (int) (p.sy * 0.55);
                for (int i = 0; i < 3; i++) {
                    frontMark(p, glow, (int) Math.floor(cx) - 2 - i, ey - i);
                    frontMark(p, glow, (int) Math.ceil(cx) + 2 + i, ey - i);
                }
            }
            default -> {
            }
        }
    }

    /** All the outside cells in one horizontal slice of the part become glow - a ring. */
    private static void band(PartDef p, BlockState glow, int y) {
        if (y < 0 || y >= p.sy) return;
        for (int z = 0; z < p.sz; z++)
            for (int x = 0; x < p.sx; x++) {
                BlockState s = p.get(x, y, z);
                if (s != null && p.isSurface(x, y, z) && !isCore(p, s)) p.set(x, y, z, glow);
            }
    }

    /** The first cell from the front (-z) in the column (x, y) becomes glow. */
    private static void frontMark(PartDef p, BlockState glow, int x, int y) {
        if (x < 0 || x >= p.sx || y < 0 || y >= p.sy) return;
        for (int z = 0; z < p.sz; z++) {
            BlockState s = p.get(x, y, z);
            if (s == null) continue;
            if (!isCore(p, s)) p.set(x, y, z, glow);
            return;
        }
    }

    private static int[] randomSurface(PartDef p, Random rnd) {
        for (int tries = 0; tries < 60; tries++) {
            int x = rnd.nextInt(p.sx), y = rnd.nextInt(p.sy), z = rnd.nextInt(p.sz);
            BlockState s = p.get(x, y, z);
            if (s != null && p.isSurface(x, y, z) && !isCore(p, s)) return new int[]{x, y, z};
        }
        return null;
    }

    /** Fills every still-empty cell whose centre lies inside one of the primitives with the label. */
    private static void paint(VoxelGrid g, double H, List<Prim> prims, int id) {
        for (Prim p : prims) {
            int i0 = Math.max(0, (int) Math.floor(p.minX() * H) - g.x0 - 1), i1 = Math.min(g.nx - 1, (int) Math.ceil(p.maxX() * H) - g.x0 + 1);
            int j0 = Math.max(0, (int) Math.floor(p.minY() * H) - g.y0 - 1), j1 = Math.min(g.ny - 1, (int) Math.ceil(p.maxY() * H) - g.y0 + 1);
            int k0 = Math.max(0, (int) Math.floor(p.minZ() * H) - g.z0 - 1), k1 = Math.min(g.nz - 1, (int) Math.ceil(p.maxZ() * H) - g.z0 + 1);
            for (int i = i0; i <= i1; i++) {
                double cx = (g.x0 + i + 0.5) / H;
                for (int j = j0; j <= j1; j++) {
                    double cy = (g.y0 + j + 0.5) / H;
                    for (int k = k0; k <= k1; k++) {
                        if (g.label(i, j, k) != 0) continue;
                        if (p.contains(cx, cy, (g.z0 + k + 0.5) / H)) g.setLabel(i, j, k, id);
                    }
                }
            }
        }
    }

    /** Marks the cells within radius (in blocks) of a point as special; adds them to the part when empty (or always, for eyes). */
    private static void mark(VoxelGrid g, double H, double px, double py, double pz, double radiusBlocks, int kind, int partId, boolean force) {
        double r2 = (radiusBlocks / H) * (radiusBlocks / H);
        int ci = (int) Math.floor(px * H) - g.x0, cj = (int) Math.floor(py * H) - g.y0, ck = (int) Math.floor(pz * H) - g.z0;
        int reach = (int) Math.ceil(radiusBlocks) + 1;
        for (int i = Math.max(0, ci - reach); i <= Math.min(g.nx - 1, ci + reach); i++)
            for (int j = Math.max(0, cj - reach); j <= Math.min(g.ny - 1, cj + reach); j++)
                for (int k = Math.max(0, ck - reach); k <= Math.min(g.nz - 1, ck + reach); k++) {
                    double dx = (g.x0 + i + 0.5) / H - px, dy = (g.y0 + j + 0.5) / H - py, dz = (g.z0 + k + 0.5) / H - pz;
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    if (force || g.label(i, j, k) == 0) g.setLabel(i, j, k, partId);
                    if (g.label(i, j, k) != 0) g.setSpecial(i, j, k, kind);
                }
    }

    private static void fillRandom(PartDef p, Palette palette, Random rnd) {
        for (int y = 0; y < p.sy; y++)
            for (int z = 0; z < p.sz; z++)
                for (int x = 0; x < p.sx; x++)
                    p.set(x, y, z, palette.pick(rnd));
    }

    /**
     * Knocks a few surface cells off so the silhouette looks eroded. Cores are never removed,
     * the bottom layer of a leg (the sole) is kept flat when keepSole is set, and a cell is
     * never removed when that would expose the part's interior all the way through.
     */
    private static void weather(PartDef p, Random rnd, float chance, boolean keepSole) {
        List<int[]> victims = new ArrayList<>();
        for (int y = 0; y < p.sy; y++)
            for (int z = 0; z < p.sz; z++)
                for (int x = 0; x < p.sx; x++) {
                    if (keepSole && y == 0) continue;
                    BlockState s = p.get(x, y, z);
                    if (s == null) continue;
                    if (!p.isSurface(x, y, z)) continue;
                    // only edges (two open sides) erode - flat faces stay intact
                    int open = 0;
                    if (p.get(x + 1, y, z) == null) open++;
                    if (p.get(x - 1, y, z) == null) open++;
                    if (p.get(x, y + 1, z) == null) open++;
                    if (p.get(x, y - 1, z) == null) open++;
                    if (p.get(x, y, z + 1) == null) open++;
                    if (p.get(x, y, z - 1) == null) open++;
                    if (open < 2) continue;
                    if (rnd.nextFloat() < chance * open) victims.add(new int[]{x, y, z});
                }
        for (int[] v : victims) {
            BlockState s = p.get(v[0], v[1], v[2]);
            if (s == null) continue;
            if (isCore(p, s)) continue;
            p.set(v[0], v[1], v[2], null);
        }
    }

    private static boolean isCore(PartDef p, BlockState s) {
        // cores and eyes are light sources; body blocks never are
        return s.getLightEmission() > 0;
    }
}
