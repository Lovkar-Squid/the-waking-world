import me.lovkar.wakingworld.body.ColossusBody;
import me.lovkar.wakingworld.body.ColossusPose;
import me.lovkar.wakingworld.body.Palette;
import me.lovkar.wakingworld.body.PartDef;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/** Prints where the head, chest, knee and hand are through the death pose (body space, front = -Z). */
public class DeathPreview {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ColossusBody b = ColossusBody.build(Palette.STONE, 7, 40);
        PartDef torso = b.part(PartDef.Kind.TORSO);
        int[] ticks = {0, 30, 60, 120, 180, 250, 280, 310, 336, 360, 400, 470};
        grab(b, torso);
        System.out.printf("%5s %8s %8s %6s | %22s | %22s | %22s | %22s%n", "tick", "drop", "fall", "lift", "head", "chest", "right knee", "right hand");
        for (int t : ticks) {
            ColossusPose p = ColossusPose.walking(0, 0, 0, 0, b.height);
            p.dying(t, b.height);
            System.out.printf("%5d %8.2f %8.1f %6.1f | %s | %s | %s | %s%n", t, p.drop, Math.toDegrees(p.fall), p.lift,
                    pt(p, b, torso, PartDef.Kind.HEAD, 0.5), pt(p, b, torso, PartDef.Kind.TORSO, 0.55),
                    pt(p, b, torso, PartDef.Kind.RIGHT_LEG, 0.5), pt(p, b, torso, PartDef.Kind.RIGHT_ARM, 0.0));
        }
    }
    static void grab(ColossusBody b, PartDef torso) {
        System.out.println("grab: right hand (bottom of the arm) through the attack");
        for (int t : new int[]{0, 11, 22, 32, 42, 60, 66, 72, 96}) {
            ColossusPose p = ColossusPose.walking(0, 0, 0, 0, b.height);
            p.attack(10, t / 96f, 22 / 96f, true);
            System.out.printf("  tick %3d drop %6.2f  hand %s%n", t, p.drop, pt(p, b, torso, PartDef.Kind.RIGHT_ARM, 0.06));
        }
    }

    static String pt(ColossusPose p, ColossusBody b, PartDef torso, PartDef.Kind k, double along) {
        PartDef d = b.part(k);
        double[] v = {d.ox + d.sx * 0.5, d.oy + d.sy * along, d.oz + d.sz * 0.5};
        p.transform(v, d, torso);
        double y = v[1] + p.drop, z = v[2];
        double c = Math.cos(p.fall), s = Math.sin(p.fall);
        double ry = y * c - z * s, rz = y * s + z * c;
        return String.format("x%6.1f y%6.1f z%6.1f", v[0], ry + p.lift, rz);
    }
}
