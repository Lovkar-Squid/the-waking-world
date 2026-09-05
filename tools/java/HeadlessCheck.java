import me.lovkar.wakingworld.body.ColossusBody;
import me.lovkar.wakingworld.body.Palette;
import me.lovkar.wakingworld.body.PartDef;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Boots the vanilla registries (no game) and builds bodies with real BlockStates - catches static-init and palette bugs. */
public class HeadlessCheck {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        for (String name : Palette.presetNames()) {
            Palette p = Palette.preset(name);
            String wire = p.serialize();
            Palette back = Palette.parse(wire);
            if (!back.serialize().equals(wire)) throw new IllegalStateException("round trip failed for " + name);
            ColossusBody body = ColossusBody.build(p, 7, 40);
            int cores = 0;
            for (PartDef part : body.parts)
                for (int x = 0; x < part.sx; x++) for (int y = 0; y < part.sy; y++) for (int z = 0; z < part.sz; z++) {
                    var s = part.get(x, y, z);
                    if (s != null && s.getLightEmission() > 0) cores++;
                }
            System.out.println(name + ": kind=" + p.kind + " bar=" + p.barColor() + " parts=" + body.parts.size()
                    + " cells=" + body.totalCells() + " glowing=" + cores + " key=" + Long.toHexString(body.key()));
        }
        // hit boxes: sliced boxes should be much tighter than one box per part
        ColossusBody body = ColossusBody.build(Palette.STONE, 7, 40);
        double whole = 0, sliced = 0;
        for (PartDef part : body.parts) { var b = part.box(); whole += b.getXsize() * b.getYsize() * b.getZsize(); }
        for (ColossusBody.HitBox hb : body.hitBoxes()) { var b = hb.box(); sliced += b.getXsize() * b.getYsize() * b.getZsize(); }
        System.out.println("hit boxes: " + body.hitBoxes().size() + " boxes, volume " + (int) sliced + " vs " + (int) whole
                + " for whole parts (" + Math.round(100 * sliced / whole) + "%), cells " + body.totalCells());
        // a sampled-style palette through the wire format
        Palette custom = Palette.parse("terrain|minecraft:magma_block|minecraft:magma_block|minecraft:stone*50,minecraft:dirt*30,minecraft:grass_block*10");
        System.out.println("terrain: kind=" + custom.kind + " bar=" + custom.barColor() + " -> " + custom.serialize());
        checkPoseTransform(body);
        checkPosedBody(body);
        System.out.println("OK");
    }

    /** PosedBody (matrix per part) must agree with ColossusPose.transform, and a whole-body sweep must be cheap. */
    private static void checkPosedBody(ColossusBody body) {
        java.util.Random rnd = new java.util.Random(5);
        PartDef torso = body.part(PartDef.Kind.TORSO);
        double worst = 0;
        int cells = 0;
        long best = Long.MAX_VALUE;
        for (int n = 0; n < 30; n++) {
            me.lovkar.wakingworld.body.ColossusPose pose = me.lovkar.wakingworld.body.ColossusPose.walking(rnd.nextFloat() * 40f, rnd.nextFloat(), rnd.nextFloat() * 60f, rnd.nextFloat() * 30f, body.height);
            pose.attack(1 + rnd.nextInt(10), rnd.nextFloat(), 0.4f, true);
            long t0 = System.nanoTime();
            me.lovkar.wakingworld.body.PosedBody posed = new me.lovkar.wakingworld.body.PosedBody(body, pose);
            cells = 0;
            double[] v = new double[3];
            for (PartDef part : body.parts) {
                for (int x = 0; x < part.sx; x++) for (int y = 0; y < part.sy; y++) for (int z = 0; z < part.sz; z++) {
                    if (part.get(x, y, z) == null || !part.isSurface(x, y, z)) continue;
                    v[0] = part.ox + x + 0.5; v[1] = part.oy + y + 0.5; v[2] = part.oz + z + 0.5;
                    posed.apply(part, v);
                    cells++;
                    if (n == 0) {
                        double[] w = {part.ox + x + 0.5, part.oy + y + 0.5, part.oz + z + 0.5};
                        pose.transform(w, part, torso);
                        worst = Math.max(worst, Math.abs(v[0] - w[0]) + Math.abs(v[1] - w[1]) + Math.abs(v[2] - w[2]));
                    }
                }
            }
            best = Math.min(best, System.nanoTime() - t0);
        }
        System.out.println("posed body: " + cells + " surface cells swept in " + String.format("%.3f", best / 1e6) + " ms (best of 30), matrix vs transform worst difference " + String.format("%.6f", worst));
        if (worst > 1e-6) throw new IllegalStateException("PosedBody disagrees with ColossusPose.transform");
    }

    /**
     * The hit boxes follow the animation with ColossusPose.transform; the renderer follows it with a
     * PoseStack. Replay the renderer's exact matrix chain with JOML for random poses and make sure
     * both put every part's corner in the same place.
     */
    private static void checkPoseTransform(ColossusBody body) {
        java.util.Random rnd = new java.util.Random(11);
        PartDef torso = body.part(PartDef.Kind.TORSO);
        double worst = 0;
        int checked = 0;
        for (int n = 0; n < 200; n++) {
            me.lovkar.wakingworld.body.ColossusPose pose = me.lovkar.wakingworld.body.ColossusPose.walking(
                    rnd.nextFloat() * 40f, rnd.nextFloat(), rnd.nextFloat() * 120f - 60f, rnd.nextFloat() * 60f - 20f, body.height);
            pose.attack(1 + rnd.nextInt(9), rnd.nextFloat(), 0.4f + rnd.nextFloat() * 0.3f, rnd.nextBoolean());
            for (ColossusBody.HitBox hb : body.hitBoxes()) {
                PartDef def = body.part(hb.kind());
                // the renderer's PoseStack chain (ColossusRenderer.render), body yaw left out on both sides
                org.joml.Matrix4f m = new org.joml.Matrix4f();
                boolean onTorso = def.kind != PartDef.Kind.TORSO && !def.isLeg();
                float bob = def.isLeg() ? 0f : pose.bob;
                if (torso != null && (def.kind == PartDef.Kind.TORSO || onTorso)) {
                    m.translate(torso.px, torso.py + bob, torso.pz);
                    m.rotate(new org.joml.Quaternionf().rotationY(pose.torsoYaw));
                    m.rotate(new org.joml.Quaternionf().rotationX(-pose.torsoPitch));
                    m.translate(def.px - torso.px, def.py - torso.py, def.pz - torso.pz);
                } else {
                    m.translate(def.px, def.py + bob, def.pz);
                }
                switch (def.kind) {
                    case HEAD -> {
                        m.rotate(new org.joml.Quaternionf().rotationY(-pose.headYaw));
                        m.rotate(new org.joml.Quaternionf().rotationX(-pose.headPitch));
                    }
                    case TORSO -> { }
                    case LEFT_ARM, RIGHT_ARM -> {
                        float spread = def.kind == PartDef.Kind.RIGHT_ARM ? pose.armSpread : -pose.armSpread;
                        m.rotate(new org.joml.Quaternionf().rotationZ(spread));
                        m.rotate(new org.joml.Quaternionf().rotationX(pose.limbAngle(def.kind)));
                    }
                    default -> m.rotate(new org.joml.Quaternionf().rotationX(pose.limbAngle(def.kind)));
                }
                var box = hb.box();
                for (int i = 0; i < 8; i++) {
                    double x = (i & 1) == 0 ? box.minX : box.maxX, y = (i & 2) == 0 ? box.minY : box.maxY, z = (i & 4) == 0 ? box.minZ : box.maxZ;
                    // renderer vertices are pre-translated into pivot space
                    org.joml.Vector4f r = m.transform(new org.joml.Vector4f((float) (x - def.px), (float) (y - def.py), (float) (z - def.pz), 1f));
                    double[] v = {x, y, z};
                    pose.transform(v, def, torso);
                    worst = Math.max(worst, Math.abs(v[0] - r.x) + Math.abs(v[1] - r.y) + Math.abs(v[2] - r.z));
                    checked++;
                }
            }
        }
        System.out.println("pose transform vs renderer matrices: " + checked + " corners, worst difference " + String.format("%.5f", worst));
        if (worst > 0.01) throw new IllegalStateException("hit boxes and renderer disagree about the pose");
    }
}
