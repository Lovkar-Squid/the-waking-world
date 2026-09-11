import me.lovkar.wakingworld.kingdom.HouseBuilder;
import me.lovkar.wakingworld.kingdom.KingdomBuild;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Draws every house design on flat ground (and one on a slope) and writes the courses as JSON for
 * tools/plan_iso.py - the way to look at a design without a game. Usage: HousePreview out.json [facing]
 */
public class HousePreview {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Direction facing = args.length > 1 ? Direction.byName(args[1]) : Direction.SOUTH;
        String[] kinds = {"cottage", "longhouse", "townhouse", "smithy", "tavern", "chapel", "well", "lamp", "garden", "tree"};
        HouseBuilder.Terrain flat = (x, z) -> 63;
        HouseBuilder.Terrain slope = (x, z) -> 63 + Math.floorDiv(x, 4);
        StringBuilder out = new StringBuilder("{\n");
        int col = 0;
        for (String kind : kinds) {
            for (int pal = 0; pal < (kind.equals("cottage") ? HouseBuilder.Palette.ALL.length : 1); pal++) {
                KingdomBuild.Plan plan = new KingdomBuild.Plan();
                HouseBuilder.Terrain terrain = kind.equals("longhouse") ? slope : flat;
                BlockPos doorstep = new BlockPos(col * 18, 64, 0);
                HouseBuilder.Frame f = new HouseBuilder.Frame(plan, terrain, doorstep, facing);
                HouseBuilder.Palette p = HouseBuilder.Palette.of(pal);
                switch (kind) {
                    case "cottage" -> HouseBuilder.cottage(f, p);
                    case "longhouse" -> HouseBuilder.longhouse(f, p);
                    case "townhouse" -> HouseBuilder.townhouse(f, p);
                    case "smithy" -> HouseBuilder.smithy(f, p);
                    case "tavern" -> HouseBuilder.tavern(f, p);
                    case "chapel" -> HouseBuilder.chapel(f, p);
                    case "well" -> HouseBuilder.well(f, p);
                    case "lamp" -> HouseBuilder.lamp(f, p);
                    case "garden" -> HouseBuilder.garden(f, p, 3, 5);
                    default -> HouseBuilder.tree(f, p, true);
                }
                // last write wins, like the mason's stable sort within one height
                Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
                plan.forEach((pos, st) -> { cells.remove(pos); cells.put(pos, st); });
                String name = kind + (pal > 0 ? "_" + pal : "");
                out.append("  \"").append(name).append("\": {\"doorstep\": [").append(doorstep.getX()).append(",").append(doorstep.getY()).append(",").append(doorstep.getZ())
                        .append("], \"palette\": \"").append(p.name()).append("\", \"blocks\": [\n");
                boolean first = true;
                for (Map.Entry<BlockPos, BlockState> e : cells.entrySet()) {
                    BlockPos pos = e.getKey();
                    BlockState st = e.getValue();
                    if (!first) out.append(",\n");
                    first = false;
                    out.append("    [").append(pos.getX()).append(",").append(pos.getY()).append(",").append(pos.getZ()).append(",\"")
                            .append(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath()).append("\",{");
                    boolean fp = true;
                    for (Property<?> prop : st.getProperties()) {
                        if (!fp) out.append(",");
                        fp = false;
                        out.append("\"").append(prop.getName()).append("\":\"").append(st.getValue(prop).toString().toLowerCase()).append("\"");
                    }
                    out.append("}]");
                }
                out.append("\n  ]},\n");
                System.out.println(name + ": " + cells.size() + " blocks (" + plan.size() + " courses), " + p.name());
                col++;
            }
        }
        out.setLength(out.length() - 2);
        out.append("\n}\n");
        try (PrintWriter w = new PrintWriter(args[0], "UTF-8")) { w.print(out); }
    }
}
