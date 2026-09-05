package me.lovkar.wakingworld.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/**
 * What the people who left behind the Dead Letters left behind: a cottage with the roof fallen
 * in, a watchtower, a well, a graveyard with a crypt under it, a pilgrims' camp - or a whole
 * hamlet of them around a well. {@code {"type": "wakingworld:ruin", "kind": "cottage"}}; the
 * hamlet lays out several pieces ({@link RuinPiece}) around its origin, each on its own ground.
 * The building style follows the biome: oak and cobble, spruce in the cold, sandstone and acacia
 * in the dry lands.
 */
public class RuinStructure extends Structure {
    public static final MapCodec<RuinStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Codec.STRING.fieldOf("kind").forGetter(s -> s.kind)
    ).apply(i, RuinStructure::new));

    public final String kind;

    public RuinStructure(Structure.StructureSettings settings, String kind) {
        super(settings);
        this.kind = kind;
    }

    /** The surface block's y at (x, z), or Integer.MIN_VALUE when it is under water. */
    public static int dryHeight(GenerationContext context, int x, int z) {
        return Terrain.dryHeight(context, x, z);
    }

    /**
     * Level, dry ground under a footprint of {@code reach} blocks around (x, z): every second column
     * is sampled; all must be dry and within {@code maxSlope} of each other. The median height, or
     * MIN_VALUE for a bad site. Steep or wet ground gets no building rather than a building on stilts.
     */
    public static int siteHeight(GenerationContext context, int x, int z, int reach, int maxSlope) {
        // the cheap look first: the preliminary surface must already be dry and level-ish over the footprint
        if (!Terrain.quickLevel(context, x, z, reach, Math.max(2, reach / 2), maxSlope + 4)) return Integer.MIN_VALUE;
        java.util.List<Integer> hs = new java.util.ArrayList<>();
        int step = reach >= 4 ? 2 : 1;
        for (int dx = -reach; dx <= reach; dx += step) {
            for (int dz = -reach; dz <= reach; dz += step) {
                int h = dryHeight(context, x + dx, z + dz);
                if (h == Integer.MIN_VALUE) return Integer.MIN_VALUE;
                hs.add(h);
            }
        }
        java.util.Collections.sort(hs);
        if (hs.get(hs.size() - 1) - hs.get(0) > maxSlope) return Integer.MIN_VALUE;
        return hs.get(hs.size() / 2);
    }

    /** A hamlet wants a broad stretch of mostly dry, mostly level ground: 85 % of a 60-block square within six blocks of the middle height. */
    static int hamletSite(GenerationContext context, int x, int z) {
        // judged on the preliminary surface (cheap), middle outward, giving up as soon as the budget is spent
        int h0 = Terrain.fastDry(context, x, z);
        if (h0 == Integer.MIN_VALUE) return Integer.MIN_VALUE;
        java.util.List<int[]> pts = new java.util.ArrayList<>();
        for (int dx = -30; dx <= 30; dx += 6) for (int dz = -30; dz <= 30; dz += 6) pts.add(new int[]{dx, dz});
        pts.sort(java.util.Comparator.comparingInt(a -> a[0] * a[0] + a[1] * a[1]));
        int total = pts.size(), wetBudget = total * 8 / 100, offBudget = total * 15 / 100;
        java.util.List<Integer> hs = new java.util.ArrayList<>(total);
        int wet = 0, off = 0;
        for (int[] s : pts) {
            int h = Terrain.fastDry(context, x + s[0], z + s[1]);
            if (h == Integer.MIN_VALUE) wet++;
            else {
                hs.add(h);
                if (Math.abs(h - h0) > 7) off++;
            }
            if (wet > wetBudget || wet + off > offBudget) return Integer.MIN_VALUE;
        }
        java.util.Collections.sort(hs);
        int median = hs.get(hs.size() / 2);
        off = 0;
        for (int h : hs) if (Math.abs(h - median) > 6) off++;
        if (off + wet > offBudget) return Integer.MIN_VALUE;
        return dryHeight(context, x, z);
    }

    public static int styleOf(GenerationContext context, int x, int y, int z) {
        Holder<Biome> b = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), context.randomState().sampler());
        if (b.is(BiomeTags.IS_BADLANDS) || b.is(BiomeTags.IS_SAVANNA) || b.is(BiomeTags.HAS_DESERT_PYRAMID)) return RuinPiece.STYLE_DRY;
        if (b.is(BiomeTags.IS_TAIGA) || b.value().coldEnoughToSnow(new BlockPos(x, y, z))) return RuinPiece.STYLE_COLD;
        return RuinPiece.STYLE_OAK;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        RandomSource rng = context.random();
        if (!Terrain.biomesOk(context, x, z, 0)) return Optional.empty(); // the biome first: it is the cheapest question
        if ("hamlet".equals(kind)) {
            if (hamletSite(context, x, z) == Integer.MIN_VALUE) return Optional.empty();
            int y = siteHeight(context, x, z, 4, 3);
            if (y == Integer.MIN_VALUE) return Optional.empty();
            int style = styleOf(context, x, y, z);
            BlockPos origin = new BlockPos(x, y, z);
            return Optional.of(new GenerationStub(origin, builder -> hamlet(context, builder, origin, style, rng)));
        }
        RuinPiece.Kind k = RuinPiece.Kind.of(kind);
        int y = siteHeight(context, x, z, k.reach, k.maxSlope);
        if (y == Integer.MIN_VALUE) return Optional.empty();
        int style = styleOf(context, x, y, z);
        BlockPos origin = new BlockPos(x, y, z);
        Rotation rot = Rotation.getRandom(rng);
        float decay = 0.35F + rng.nextFloat() * 0.55F;
        long seed = rng.nextLong();
        int v = k == RuinPiece.Kind.COTTAGE && rng.nextInt(10) < 3 ? RuinPiece.V_BIG : 0;
        return Optional.of(new GenerationStub(origin, builder -> builder.addPiece(new RuinPiece(k, origin, rot, style, v, decay, seed))));
    }

    /**
     * The hamlet: a well and square in the middle, an inner ring of cottages facing it with the
     * chapel among them, the market off the square, an outer ring of houses and farms, a palisade
     * with two gates and a tower at one of them, the graveyard and a camp outside. One or two of
     * the buildings hold a letter.
     */
    private void hamlet(GenerationContext context, StructurePiecesBuilder builder, BlockPos origin, int style, RandomSource rng) {
        int cx = origin.getX(), cz = origin.getZ();
        java.util.List<RuinPiece> letterable = new java.util.ArrayList<>();
        java.util.List<RuinPiece> all = new java.util.ArrayList<>();
        all.add(new RuinPiece(RuinPiece.Kind.WELL, origin, Rotation.NONE, style, RuinPiece.V_HAMLET, 0.3F + rng.nextFloat() * 0.3F, rng.nextLong()));
        long palSeed = rng.nextLong();
        double[] gates = RuinPiece.palisadeGates(palSeed);
        double start = rng.nextDouble() * Math.PI * 2;
        // the inner ring: six to eight slots, one of them the chapel
        int inner = 6 + rng.nextInt(3);
        int chapelSlot = rng.nextInt(inner);
        for (int i = 0; i < inner; i++) {
            double a = start + i * (Math.PI * 2 / inner) + (rng.nextDouble() - 0.5) * 0.4;
            int r = 13 + rng.nextInt(4);
            int hx = cx + (int) Math.round(Math.cos(a) * r), hz = cz + (int) Math.round(Math.sin(a) * r);
            if (i == chapelSlot) {
                int hy = siteHeight(context, hx, hz, 8, 4);
                if (hy != Integer.MIN_VALUE) {
                    RuinPiece p = new RuinPiece(RuinPiece.Kind.CHAPEL, new BlockPos(hx, hy, hz), facing(cx - hx, cz - hz), style, RuinPiece.V_HAMLET, 0.3F + rng.nextFloat() * 0.4F, rng.nextLong());
                    all.add(p);
                    letterable.add(p);
                }
                continue;
            }
            int v = RuinPiece.V_HAMLET | (rng.nextInt(10) < 3 ? RuinPiece.V_BIG : 0);
            int hy = Integer.MIN_VALUE;
            for (int attempt = 0; attempt < 3 && hy == Integer.MIN_VALUE; attempt++) {
                if (attempt > 0) {
                    double a2 = a + (rng.nextDouble() - 0.5) * 0.3;
                    int r2 = 12 + rng.nextInt(6);
                    hx = cx + (int) Math.round(Math.cos(a2) * r2);
                    hz = cz + (int) Math.round(Math.sin(a2) * r2);
                }
                hy = siteHeight(context, hx, hz, (v & RuinPiece.V_BIG) != 0 ? 6 : 5, 3);
            }
            if (hy == Integer.MIN_VALUE) continue;
            RuinPiece p = new RuinPiece(RuinPiece.Kind.COTTAGE, new BlockPos(hx, hy, hz), facing(cx - hx, cz - hz), style, v, 0.3F + rng.nextFloat() * 0.6F, rng.nextLong());
            all.add(p);
            letterable.add(p);
        }
        // the market off the square, between two inner slots
        {
            double a = start + Math.PI / inner;
            int mx = cx + (int) Math.round(Math.cos(a) * 11), mz = cz + (int) Math.round(Math.sin(a) * 11);
            int my = siteHeight(context, mx, mz, 8, 4);
            if (my != Integer.MIN_VALUE) {
                RuinPiece p = new RuinPiece(RuinPiece.Kind.MARKET, new BlockPos(mx, my, mz), facing(cx - mx, cz - mz), style, RuinPiece.V_HAMLET, 0.3F + rng.nextFloat() * 0.5F, rng.nextLong());
                all.add(p);
                letterable.add(p);
            }
        }
        // the outer ring: houses and farms
        int outer = 5 + rng.nextInt(3);
        for (int i = 0; i < outer; i++) {
            double a = start + Math.PI / outer + i * (Math.PI * 2 / outer) + (rng.nextDouble() - 0.5) * 0.4;
            int r = 22 + rng.nextInt(4);
            int hx = cx + (int) Math.round(Math.cos(a) * r), hz = cz + (int) Math.round(Math.sin(a) * r);
            boolean farm = rng.nextInt(10) < 4;
            boolean big = !farm && rng.nextInt(10) < 3;
            int hy = Integer.MIN_VALUE;
            for (int attempt = 0; attempt < 3 && hy == Integer.MIN_VALUE; attempt++) {
                if (attempt > 0) {
                    double a2 = a + (rng.nextDouble() - 0.5) * 0.3;
                    int r2 = 21 + rng.nextInt(6);
                    hx = cx + (int) Math.round(Math.cos(a2) * r2);
                    hz = cz + (int) Math.round(Math.sin(a2) * r2);
                }
                hy = siteHeight(context, hx, hz, farm ? 8 : big ? 6 : 5, 3);
            }
            if (hy == Integer.MIN_VALUE) continue;
            RuinPiece p = new RuinPiece(farm ? RuinPiece.Kind.FARM : RuinPiece.Kind.COTTAGE, new BlockPos(hx, hy, hz), facing(cx - hx, cz - hz), style,
                    RuinPiece.V_HAMLET | (big ? RuinPiece.V_BIG : 0), 0.3F + rng.nextFloat() * 0.6F, rng.nextLong());
            all.add(p);
            if (!farm) letterable.add(p);
        }
        // the palisade, a tower at the first gate, the camp outside the second, the graveyard on its own side
        all.add(new RuinPiece(RuinPiece.Kind.PALISADE, origin, Rotation.NONE, style, RuinPiece.V_HAMLET, 0.3F + rng.nextFloat() * 0.5F, palSeed));
        {
            double a = gates[1];
            int tx = cx + (int) Math.round(Math.cos(a) * (gates[0] + 4) + Math.cos(a + Math.PI / 2) * 5), tz = cz + (int) Math.round(Math.sin(a) * (gates[0] + 4) + Math.sin(a + Math.PI / 2) * 5);
            int ty = siteHeight(context, tx, tz, 4, 3);
            if (ty != Integer.MIN_VALUE) all.add(new RuinPiece(RuinPiece.Kind.WATCHTOWER, new BlockPos(tx, ty, tz), facing(cx - tx, cz - tz), style, RuinPiece.V_HAMLET, 0.3F + rng.nextFloat() * 0.5F, rng.nextLong()));
        }
        if (rng.nextInt(10) < 6) {
            double a = gates[2];
            int px = cx + (int) Math.round(Math.cos(a) * (gates[0] + 9)), pz = cz + (int) Math.round(Math.sin(a) * (gates[0] + 9));
            int py = siteHeight(context, px, pz, 7, 4);
            if (py != Integer.MIN_VALUE) all.add(new RuinPiece(RuinPiece.Kind.CAMP, new BlockPos(px, py, pz), Rotation.getRandom(rng), style, RuinPiece.V_HAMLET, 0.3F + rng.nextFloat() * 0.4F, rng.nextLong()));
        }
        {
            double a = gates[1] + Math.PI / 2 + (rng.nextDouble() - 0.5) * 0.6;
            int gx = cx + (int) Math.round(Math.cos(a) * (gates[0] + 10)), gz = cz + (int) Math.round(Math.sin(a) * (gates[0] + 10));
            int gy = siteHeight(context, gx, gz, 8, 5);
            if (gy != Integer.MIN_VALUE) all.add(new RuinPiece(RuinPiece.Kind.GRAVEYARD, new BlockPos(gx, gy, gz), facing(cx - gx, cz - gz), style, RuinPiece.V_HAMLET, 0.4F + rng.nextFloat() * 0.4F, rng.nextLong()));
        }
        // one or two letters in the whole hamlet
        int letters = letterable.isEmpty() ? 0 : 1 + rng.nextInt(2);
        java.util.Set<RuinPiece> chosen = new java.util.HashSet<>();
        while (chosen.size() < Math.min(letters, letterable.size())) chosen.add(letterable.get(rng.nextInt(letterable.size())));
        for (RuinPiece p : all) builder.addPiece(chosen.contains(p) ? p.withLetter() : p);
    }

    /** The rotation that turns a piece's front (local +z, south) towards (dx, dz). */
    public static Rotation facing(int dx, int dz) {
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90; // +z -> east / west
        return dz > 0 ? Rotation.NONE : Rotation.CLOCKWISE_180;
    }

    @Override
    public StructureType<?> type() {
        return WakingStructures.RUIN.get();
    }
}
