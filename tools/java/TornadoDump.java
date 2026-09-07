import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Dump the debris column the client draws for a tornado, exactly as TornadoRenderer builds it:
 * ninety motes on a helix seeded from the entity id. Used for the behind-the-scenes picture -
 * this is the same column the game shows, not a drawing of one.
 */
public class TornadoDump {
    public static void main(String[] args) {
        int id = args.length > 0 ? Integer.parseInt(args[0]) : 314;
        float s = args.length > 1 ? Float.parseFloat(args[1]) : 1.0F;
        float t = args.length > 2 ? Float.parseFloat(args[2]) : 0.0F;
        double r = 3.0 + 5.0 * s;

        String[] debris = {"dirt", "coarse_dirt", "gravel", "sand", "grass_block", "oak_leaves", "stone", "moss_block"};
        RandomSource rnd = RandomSource.create(id * 7919L);
        System.out.println("{\"id\":" + id + ",\"strength\":" + s + ",\"radius\":" + r + ",\"motes\":[");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 90; i++) {
            double h = Math.pow(rnd.nextDouble(), 0.7) * 30.0;
            double widen = 0.30 + 0.75 * (h / 30.0);
            double angle = rnd.nextDouble() * Math.PI * 2;
            double out = widen * (0.55 + rnd.nextDouble() * 0.65);
            float spin = rnd.nextFloat() * 360F;
            String state = debris[rnd.nextInt(debris.length)];

            double a = angle + t * (0.24 - 0.11 * (h / 30.0));
            double ox = Math.cos(a) * out * r * s;
            double oz = Math.sin(a) * out * r * s;
            double oy = h * s;
            float size = (float) Mth.lerp(h / 30.0, 0.85, 0.45);
            if (i > 0) sb.append(",\n");
            sb.append(String.format(java.util.Locale.ROOT,
                    "{\"x\":%.5f,\"y\":%.5f,\"z\":%.5f,\"size\":%.4f,\"spin\":%.2f,\"block\":\"%s\"}",
                    ox, oy, oz, size, spin, state));
        }
        System.out.println(sb);
        System.out.println("]}");
    }
}
