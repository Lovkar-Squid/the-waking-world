import me.lovkar.wakingworld.body.ColossusBody;
import me.lovkar.wakingworld.body.ColossusPose;
import me.lovkar.wakingworld.body.Palette;
import me.lovkar.wakingworld.body.PartDef;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Where does the right hand end up for a given torso pitch / arm angle / drop / bob? (body space, H = 40) */
public class PoseSearch {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ColossusBody b = ColossusBody.build(Palette.STONE, 7, 40);
        PartDef torso = b.part(PartDef.Kind.TORSO), arm = b.part(PartDef.Kind.RIGHT_ARM), head = b.part(PartDef.Kind.HEAD);
        System.out.printf("arm box y %d..%d pivot (%.1f, %.1f, %.1f); head box y %d..%d z %d..%d%n", arm.oy, arm.oy + arm.sy, arm.px, arm.py, arm.pz, head.oy, head.oy + head.sy, head.oz, head.oz + head.sz);
        System.out.println("-- grab reach (want hand y ~ 0.5):");
        for (float tp : new float[]{0.6f, 0.8f, 1.0f}) for (float a : new float[]{0.8f, 1.0f, 1.2f, 1.4f}) for (float drop : new float[]{0f, -3f, -5f}) {
            ColossusPose p = ColossusPose.walking(0, 0, 0, 0, 40);
            p.torsoPitch = tp; p.rightArm = a; p.drop = drop; p.bob = -2f;
            System.out.printf("  tp %.1f arm %.1f drop %4.1f -> %s%n", tp, a, drop, hand(p, b, torso, arm));
        }
        System.out.println("-- hold in front of the face (want y ~ 28-32, z ~ -14..-18):");
        for (float tp : new float[]{0.0f, 0.1f, 0.25f}) for (float a : new float[]{1.2f, 1.4f, 1.6f, 1.8f}) {
            ColossusPose p = ColossusPose.walking(0, 0, 0, 0, 40);
            p.torsoPitch = tp; p.rightArm = a;
            System.out.printf("  tp %.2f arm %.1f -> %s%n", tp, a, hand(p, b, torso, arm));
        }
    }
    static String hand(ColossusPose p, ColossusBody b, PartDef torso, PartDef arm) {
        double[] v = {arm.ox + arm.sx * 0.5, arm.oy + arm.sy * 0.06, arm.oz + arm.sz * 0.5};
        p.transform(v, arm, torso);
        return String.format("hand x %5.1f y %5.1f z %6.1f", v[0], v[1] + p.drop, v[2]);
    }
}
