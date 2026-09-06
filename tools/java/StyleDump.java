import me.lovkar.wakingworld.body.ColossusBody;
import me.lovkar.wakingworld.body.ColossusShapes;
import me.lovkar.wakingworld.body.ColossusStyle;
import me.lovkar.wakingworld.body.Palette;
import me.lovkar.wakingworld.body.PartDef;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Dumps a built body in a supporter style as preview JSON with the block id per cell:
 * java StyleDump <kind> <style|none> <seed> <height> <out.json>. Also prints the hit-box check: the
 * slices of the styled body must equal the plain body's (the style is clothes, not shape).
 */
public class StyleDump {
    public static void main(String[] args) throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        String kind = args[0];
        ColossusStyle style = ColossusStyle.byId(args[1]);
        int seed = Integer.parseInt(args[2]);
        int height = Integer.parseInt(args[3]);
        Palette plain = Palette.preset(kind);
        Palette palette = style == null || style == ColossusStyle.NONE ? plain : Palette.styled(plain, style);
        if (!Palette.parse(palette.serialize()).serialize().equals(palette.serialize())) throw new IllegalStateException("palette does not round-trip: " + palette.serialize());
        if (!Palette.parse(palette.serialize()).kind.equals(plain.kind)) throw new IllegalStateException("kind lost: " + Palette.parse(palette.serialize()).kind);
        ColossusBody body = ColossusShapes.beast(palette, seed, height);
        ColossusBody ref = ColossusShapes.beast(plain, seed, height);
        StringBuilder sb = new StringBuilder("{\"height\":" + height + ",\"palette\":\"" + palette.serialize().replace("\"", "") + "\",\"cells\":[");
        boolean first = true;
        int glow = 0, total = 0, decor = 0;
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
                        sb.append('[').append(p.ox + x).append(',').append(p.oy + y).append(',').append(p.oz + z).append(',').append(label).append(',').append(special)
                                .append(",\"").append(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath()).append("\"]");
                    }
        }
        sb.append("]}");
        try (FileWriter w = new FileWriter(args[4])) { w.write(sb.toString()); }
        // the shape check: same part boxes and the same hit boxes
        boolean same = body.parts.size() == ref.parts.size();
        int diffBoxes = 0;
        var a = body.hitBoxes();
        var b = ref.hitBoxes();
        for (int i = 0; same && i < a.size(); i++) if (!a.get(i).box().equals(b.get(i).box())) diffBoxes++;
        for (int i = 0; i < body.parts.size() && same; i++) {
            PartDef x = body.parts.get(i), y = ref.parts.get(i);
            if (x.kind != y.kind || x.ox != y.ox || x.oy != y.oy || x.oz != y.oz || x.sx != y.sx || x.sz != y.sz || x.sy != y.sy) same = false;
        }
        System.out.println(kind + "+" + (style == null ? "none" : style.id) + ": cells=" + total + " glow=" + glow + " decor=" + decor
                + " parts-same=" + same + " hitboxes-differing=" + diffBoxes + "/" + a.size() + " cores=" + body.cores.size());
    }
}
