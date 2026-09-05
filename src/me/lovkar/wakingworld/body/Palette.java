package me.lovkar.wakingworld.body;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * What a colossus is made of: a weighted list of body blocks plus the blocks for its glowing cores
 * and eyes. The interesting palettes are not the presets but {@link #fromTerrain}: the giant is
 * built out of whatever the land around its spawn point is made of - stone and moss in the hills,
 * sandstone in the desert, packed ice in the tundra, prismarine out of the sea. A palette
 * serialises to one string so the server can hand it to clients in a synced entity field.
 */
public final class Palette {
    private static final int MAX_ENTRIES = 8;
    // Static initialisers run in textual order: everything the preset constants below need must come first.
    private static final Map<String, Palette> PRESETS = new HashMap<>();
    private static final Map<String, String> PRESET_KINDS = Map.of(
            "stone", "stone", "earth", "earth", "sandstone", "sandstone", "ice", "ice", "prismarine", "prismarine", "moss", "moss", "titan", "titan");

    public static final Palette STONE = preset("stone", new Object[][]{
            {Blocks.STONE, 40}, {Blocks.COBBLESTONE, 18}, {Blocks.MOSSY_COBBLESTONE, 12}, {Blocks.ANDESITE, 12},
            {Blocks.TUFF, 8}, {Blocks.DEEPSLATE, 6}, {Blocks.GRAVEL, 4}}, Blocks.MAGMA_BLOCK, Blocks.MAGMA_BLOCK);
    public static final Palette EARTH = preset("earth", new Object[][]{
            {Blocks.DIRT, 30}, {Blocks.COARSE_DIRT, 18}, {Blocks.ROOTED_DIRT, 10}, {Blocks.GRAVEL, 12}, {Blocks.PACKED_MUD, 10},
            {Blocks.STONE, 12}, {Blocks.GRASS_BLOCK, 8}}, Blocks.MAGMA_BLOCK, Blocks.MAGMA_BLOCK);
    public static final Palette SANDSTONE = preset("sandstone", new Object[][]{
            {Blocks.SANDSTONE, 40}, {Blocks.SMOOTH_SANDSTONE, 16}, {Blocks.CUT_SANDSTONE, 12}, {Blocks.CHISELED_SANDSTONE, 6},
            {Blocks.SAND, 12}, {Blocks.RED_SANDSTONE, 6}, {Blocks.TERRACOTTA, 8}}, Blocks.MAGMA_BLOCK, Blocks.GLOWSTONE);
    public static final Palette ICE = preset("ice", new Object[][]{
            {Blocks.PACKED_ICE, 45}, {Blocks.BLUE_ICE, 20}, {Blocks.SNOW_BLOCK, 20}, {Blocks.ICE, 10}, {Blocks.STONE, 5}},
            Blocks.SEA_LANTERN, Blocks.SEA_LANTERN);
    public static final Palette PRISMARINE = preset("prismarine", new Object[][]{
            {Blocks.PRISMARINE, 40}, {Blocks.PRISMARINE_BRICKS, 20}, {Blocks.DARK_PRISMARINE, 15}, {Blocks.MOSSY_STONE_BRICKS, 10},
            {Blocks.GRAVEL, 5}, {Blocks.TUBE_CORAL_BLOCK, 5}, {Blocks.WET_SPONGE, 5}}, Blocks.SEA_LANTERN, Blocks.SEA_LANTERN);
    public static final Palette MOSS = preset("moss", new Object[][]{
            {Blocks.MOSS_BLOCK, 35}, {Blocks.MOSSY_COBBLESTONE, 20}, {Blocks.ROOTED_DIRT, 12}, {Blocks.MUD, 8}, {Blocks.PACKED_MUD, 8},
            {Blocks.MOSSY_STONE_BRICKS, 12}, {Blocks.COARSE_DIRT, 5}}, Blocks.SHROOMLIGHT, Blocks.SHROOMLIGHT);
    /** The last colossus, woken in the End with the relics of the others. */
    public static final Palette TITAN = preset("titan", new Object[][]{
            {Blocks.OBSIDIAN, 30}, {Blocks.END_STONE, 22}, {Blocks.END_STONE_BRICKS, 12}, {Blocks.PURPUR_BLOCK, 10},
            {Blocks.DEEPSLATE, 12}, {Blocks.POLISHED_DEEPSLATE, 8}, {Blocks.BLACKSTONE, 6}}, Blocks.CRYING_OBSIDIAN, Blocks.SEA_LANTERN);

    private final String name;
    private final List<Weighted> body;
    private final int total;
    public final Block core;
    public final Block eye;
    /** stone, sandstone, ice, prismarine, moss or earth - drives the boss-bar colour and the display name. */
    public final String kind;
    private String serialized;

    private Palette(String name, List<Weighted> body, Block core, Block eye) {
        this.name = name;
        this.body = List.copyOf(body);
        this.core = core;
        this.eye = eye;
        int t = 0;
        for (Weighted x : this.body) t += x.weight;
        this.total = Math.max(1, t);
        this.kind = PRESET_KINDS.containsKey(name) ? PRESET_KINDS.get(name) : kindOf(this.body);
    }

    /** The boss bar wears the giant's colour: magma-cored earth and stone red, sand yellow, ice white, sea blue, moss green. */
    public BossEvent.BossBarColor barColor() {
        switch (kind) {
            case "sandstone": return BossEvent.BossBarColor.YELLOW;
            case "ice": return BossEvent.BossBarColor.WHITE;
            case "prismarine": return BossEvent.BossBarColor.BLUE;
            case "moss": return BossEvent.BossBarColor.GREEN;
            case "stone": return BossEvent.BossBarColor.RED;
            case "titan": return BossEvent.BossBarColor.PURPLE;
            default: return BossEvent.BossBarColor.RED;
        }
    }

    /** Material class of a sampled palette, from the same categories the glow blocks use. */
    private static String kindOf(List<Weighted> list) {
        int cold = 0, sand = 0, wood = 0, sea = 0, stone = 0, all = 0;
        for (Weighted w : list) {
            all += w.weight;
            String id = BuiltInRegistries.BLOCK.getKey(w.block).getPath();
            if (id.contains("ice") || id.contains("snow")) cold += w.weight;
            else if (id.contains("sand") || id.contains("terracotta")) sand += w.weight;
            else if (id.contains("log") || id.contains("wood") || id.contains("moss") || id.contains("mud") || id.contains("mushroom")) wood += w.weight;
            else if (id.contains("prismarine") || id.contains("coral") || id.contains("sponge")) sea += w.weight;
            else if (id.contains("stone") || id.contains("deepslate") || id.contains("andesite") || id.contains("diorite")
                    || id.contains("granite") || id.contains("tuff") || id.contains("basalt")) stone += w.weight;
        }
        if (all == 0) return "earth";
        if (cold * 3 > all) return "ice";
        if (sand * 3 > all) return "sandstone";
        if (wood * 3 > all) return "moss";
        if (sea * 3 > all) return "prismarine";
        if (stone * 2 > all) return "stone";
        return "earth";
    }

    private static Palette preset(String name, Object[][] entries, Block core, Block eye) {
        List<Weighted> list = new ArrayList<>();
        for (Object[] e : entries) list.add(new Weighted((Block) e[0], (Integer) e[1]));
        Palette p = new Palette(name, list, core, eye);
        PRESETS.put(name, p);
        return p;
    }

    /** A body block drawn from the weighted list. */
    public BlockState pick(Random random) {
        return pickAt(random.nextInt(total));
    }

    /** The same, from Minecraft's own random. */
    public BlockState pick(net.minecraft.util.RandomSource random) {
        return pickAt(random.nextInt(total));
    }

    private BlockState pickAt(int r) {
        for (Weighted x : body) {
            r -= x.weight;
            if (r < 0) return x.block.defaultBlockState();
        }
        return body.get(0).block.defaultBlockState();
    }

    /** "stone", "sandstone", ... or "terrain" for a sampled one. */
    public String name() {
        return name;
    }

    public static Palette preset(String name) {
        return name == null ? null : PRESETS.get(name.toLowerCase(Locale.ROOT));
    }

    public static List<String> presetNames() {
        return List.of("stone", "earth", "sandstone", "ice", "prismarine", "moss", "titan");
    }

    // ---- the land itself -----------------------------------------------------------------------

    /**
     * Builds a palette from the blocks around a position: the top layers of every column in the
     * radius, the surface layer counted at a third (a plains giant should be earth and stone with
     * grass on it, not a lawn), full opaque cubes only. Cores and eyes follow the material: ice and
     * snow glow cold, sand and terracotta glow like glowstone, moss and wood like shroomlight,
     * everything else like magma. Falls back to the presets over water or where nothing usable is found.
     */
    public static Palette fromTerrain(Level level, BlockPos center, int radius) {
        Map<Block, Integer> counts = new HashMap<>();
        int water = 0, columns = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                if (dx * dx + dz * dz > radius * radius) continue;
                columns++;
                int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX() + dx, center.getZ() + dz) - 1;
                pos.set(center.getX() + dx, top, center.getZ() + dz);
                BlockState surface = level.getBlockState(pos);
                if (!surface.getFluidState().isEmpty()) {
                    water++;
                    continue;
                }
                for (int dy = 0; dy < 8; dy++) {
                    pos.setY(top - dy);
                    if (pos.getY() <= level.getMinBuildHeight()) break;
                    BlockState s = level.getBlockState(pos);
                    if (!usable(s)) continue;
                    int w = dy == 0 ? 1 : 3;
                    counts.merge(s.getBlock(), w, Integer::sum);
                }
            }
        }
        if (columns > 0 && water * 2 > columns) return PRISMARINE;
        List<Map.Entry<Block, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        List<Weighted> list = new ArrayList<>();
        int sum = 0;
        for (Map.Entry<Block, Integer> e : entries) {
            if (list.size() >= MAX_ENTRIES) break;
            list.add(new Weighted(e.getKey(), e.getValue()));
            sum += e.getValue();
        }
        if (list.isEmpty() || sum < 20) return STONE;
        // normalise weights to keep the serialised form short
        List<Weighted> scaled = new ArrayList<>();
        for (Weighted w : list) scaled.add(new Weighted(w.block, Math.max(1, Math.round(w.weight * 100f / sum))));
        Block[] glow = glowFor(scaled);
        return new Palette("terrain", scaled, glow[0], glow[1]);
    }

    private static boolean usable(BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        if (!s.canOcclude()) return false;                       // no slabs, paths, snow layers, plants
        if (s.is(BlockTags.LEAVES) || s.is(Blocks.BEDROCK)) return false;
        if (s.getLightEmission() > 0) return false;              // light sources are reserved for cores
        if (s.hasBlockEntity()) return false;                    // chests, spawners...
        return true;
    }

    private static Block[] glowFor(List<Weighted> list) {
        int cold = 0, sand = 0, wood = 0, all = 0;
        for (Weighted w : list) {
            all += w.weight;
            String id = BuiltInRegistries.BLOCK.getKey(w.block).getPath();
            if (id.contains("ice") || id.contains("snow")) cold += w.weight;
            if (id.contains("sand") || id.contains("terracotta") || id.contains("red_sand")) sand += w.weight;
            if (id.contains("log") || id.contains("wood") || id.contains("moss") || id.contains("mud") || id.contains("mushroom")) wood += w.weight;
        }
        if (cold * 3 > all) return new Block[]{Blocks.SEA_LANTERN, Blocks.SEA_LANTERN};
        if (sand * 3 > all) return new Block[]{Blocks.MAGMA_BLOCK, Blocks.GLOWSTONE};
        if (wood * 3 > all) return new Block[]{Blocks.SHROOMLIGHT, Blocks.SHROOMLIGHT};
        return new Block[]{Blocks.MAGMA_BLOCK, Blocks.MAGMA_BLOCK};
    }

    // ---- wire format: "name|core|eye|block*weight,block*weight,..." --------------------------------

    public String serialize() {
        String s = this.serialized;
        if (s == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(name).append('|').append(id(core)).append('|').append(id(eye)).append('|');
            for (int i = 0; i < body.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(id(body.get(i).block)).append('*').append(body.get(i).weight);
            }
            s = sb.toString();
            this.serialized = s;
        }
        return s;
    }

    public static Palette parse(String s) {
        try {
            String[] head = s.split("\\|", 4);
            Palette preset = PRESETS.get(head[0]);
            if (preset != null && preset.serialize().equals(s)) return preset;
            Block core = block(head[1]), eye = block(head[2]);
            List<Weighted> list = new ArrayList<>();
            for (String e : head[3].split(",")) {
                int star = e.lastIndexOf('*');
                Block b = block(e.substring(0, star));
                if (b == Blocks.AIR) continue;
                list.add(new Weighted(b, Math.max(1, Integer.parseInt(e.substring(star + 1)))));
            }
            if (list.isEmpty()) return STONE;
            return new Palette(head[0], list, core, eye);
        } catch (RuntimeException ex) {
            return STONE;
        }
    }

    private static String id(Block b) {
        return BuiltInRegistries.BLOCK.getKey(b).toString();
    }

    private static Block block(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(rl);
    }

    /** Stable hash of the palette for the client mesh cache key. */
    public int hash() {
        return serialize().hashCode();
    }

    @Override
    public String toString() {
        return serialize();
    }

    private record Weighted(Block block, int weight) {
    }
}
