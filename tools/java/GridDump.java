import me.lovkar.wakingworld.body.ColossusShapes;
import me.lovkar.wakingworld.body.VoxelGrid;

import java.io.FileWriter;
import java.io.IOException;

/** Dumps a kind's label grid as JSON for previews: java GridDump <seed> <height> <out.json> [kind]. */
public class GridDump {
    public static void main(String[] args) throws IOException {
        int seed = Integer.parseInt(args[0]);
        int height = Integer.parseInt(args[1]);
        String kind = args.length > 3 ? args[3] : "stone";
        long t = System.nanoTime();
        VoxelGrid g = ColossusShapes.beastGrid(kind, seed, height);
        long ms = (System.nanoTime() - t) / 1_000_000;
        StringBuilder sb = new StringBuilder();
        sb.append("{\"x0\":").append(g.x0).append(",\"y0\":").append(g.y0).append(",\"z0\":").append(g.z0)
          .append(",\"nx\":").append(g.nx).append(",\"ny\":").append(g.ny).append(",\"nz\":").append(g.nz)
          .append(",\"height\":").append(g.height).append(",\"cells\":[");
        boolean first = true;
        for (int i = 0; i < g.nx; i++)
            for (int j = 0; j < g.ny; j++)
                for (int k = 0; k < g.nz; k++) {
                    int l = g.label(i, j, k);
                    if (l == 0) continue;
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('[').append(g.x0 + i).append(',').append(g.y0 + j).append(',').append(g.z0 + k)
                      .append(',').append(l).append(',').append(g.special(i, j, k)).append(']');
                }
        sb.append("]}");
        try (FileWriter w = new FileWriter(args[2])) { w.write(sb.toString()); }
        System.out.println("seed=" + seed + " height=" + height + " filled=" + g.filled() + " grid=" + g.nx + "x" + g.ny + "x" + g.nz
                + " torso=" + g.count(1) + " head=" + g.count(2) + " arms=" + (g.count(3) + g.count(4)) + " legs=" + (g.count(5) + g.count(6))
                + " in " + ms + " ms");
    }
}
