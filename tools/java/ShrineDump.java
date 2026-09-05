import me.lovkar.wakingworld.worldgen.ShrinePiece;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.FileWriter;
import java.io.IOException;

/** Dumps a shrine shape as preview JSON (block names): java ShrineDump <kind> <seed> <out.json>. */
public class ShrineDump {
    public static void main(String[] args) throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        String kind = args[0];
        long seed = Long.parseLong(args[1]);
        StringBuilder sb = new StringBuilder("{\"cells\":[");
        boolean first = true;
        int n = 0;
        for (int dy = -1; dy <= 14; dy++)
            for (int dz = -9; dz <= 9; dz++)
                for (int dx = -9; dx <= 9; dx++) {
                    BlockState s = ShrinePiece.shapeOf(kind, seed, dx, dy, dz);
                    if (s == null || s.isAir() || s.is(Blocks.WATER)) continue;
                    if (!first) sb.append(',');
                    first = false;
                    sb.append("[").append(dx).append(',').append(dy).append(',').append(dz).append(",\"").append(s.getBlock().getDescriptionId().replace("block.minecraft.", "")).append("\"]");
                    n++;
                }
        sb.append("]}");
        try (FileWriter w = new FileWriter(args[2])) { w.write(sb.toString()); }
        System.out.println(kind + ": " + n + " blocks");
    }
}
