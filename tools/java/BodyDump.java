import me.lovkar.wakingworld.body.ColossusBody;
import me.lovkar.wakingworld.body.ColossusShapes;
import me.lovkar.wakingworld.body.Palette;
import me.lovkar.wakingworld.body.PartDef;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;

import java.io.FileWriter;
import java.io.IOException;

/** Dumps a built body (real blocks: runes and all) as preview JSON: java BodyDump <kind> <seed> <height> <out.json>. Special 7 = glow, 8 = eye. */
public class BodyDump {
    public static void main(String[] args) throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        String kind = args[0];
        int seed = Integer.parseInt(args[1]);
        int height = Integer.parseInt(args[2]);
        Palette palette = Palette.preset(kind);
        ColossusBody body = ColossusShapes.beast(palette, seed, height);
        StringBuilder sb = new StringBuilder("{\"height\":" + height + ",\"cells\":[");
        boolean first = true;
        int glow = 0, total = 0;
        for (PartDef p : body.parts) {
            int label = switch (p.kind) { case TORSO -> 1; case HEAD -> 2; case RIGHT_ARM -> 3; case LEFT_ARM -> 4; case RIGHT_LEG -> 5; default -> 6; };
            for (int y = 0; y < p.sy; y++)
                for (int z = 0; z < p.sz; z++)
                    for (int x = 0; x < p.sx; x++) {
                        BlockState s = p.get(x, y, z);
                        if (s == null) continue;
                        total++;
                        int special = 0;
                        if (s.getLightEmission() > 0) { special = s.getBlock() == palette.eye && palette.eye != palette.core ? 8 : 7; glow++; }
                        if (!first) sb.append(',');
                        first = false;
                        sb.append('[').append(p.ox + x).append(',').append(p.oy + y).append(',').append(p.oz + z).append(',').append(label).append(',').append(special).append(']');
                    }
        }
        sb.append("]}");
        try (FileWriter w = new FileWriter(args[3])) { w.write(sb.toString()); }
        System.out.println(kind + ": cells=" + total + " glow=" + glow);
    }
}
