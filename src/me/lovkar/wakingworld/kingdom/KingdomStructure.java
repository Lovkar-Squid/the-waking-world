package me.lovkar.wakingworld.kingdom;

import com.mojang.serialization.MapCodec;
import me.lovkar.wakingworld.worldgen.RuinPiece;
import me.lovkar.wakingworld.worldgen.RuinStructure;
import me.lovkar.wakingworld.worldgen.Terrain;
import me.lovkar.wakingworld.worldgen.WakingStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A kingdom: a walled town a hundred and twenty blocks across on a broad, level, dry site. The
 * ring ({@link KingdomWallPiece}) levels the ground, lays the roads and raises the curtain wall
 * with its towers and gatehouses; the castle ({@link KeepPiece}) stands in the middle; between
 * them two rings of intact houses ({@link RuinPiece} in its lived-in mode), the markets flanking
 * the south road, the chapel, two farms - and the people: guards, traders, the king. Rare: one
 * per hundred chunks or so, in plains, savannas, snowy plains and meadows, never beside a village.
 * The structure asks for {@code beard_thin} terrain adaptation so the ground under the whole
 * ring is filled and cut to the town's level before the pieces are laid.
 */
public class KingdomStructure extends Structure {
    public static final MapCodec<KingdomStructure> CODEC = simpleCodec(KingdomStructure::new);

    public KingdomStructure(Structure.StructureSettings settings) {
        super(settings);
    }

    /** The sample offsets over the town's disc (r 73, every 12 blocks), nearest the middle first, so a bad site fails early. */
    private static final int[][] SAMPLES;
    static {
        List<int[]> pts = new ArrayList<>();
        for (int dx = -72; dx <= 72; dx += 12) {
            for (int dz = -72; dz <= 72; dz += 12) {
                if (dx * dx + dz * dz <= 73 * 73) pts.add(new int[]{dx, dz});
            }
        }
        pts.sort(java.util.Comparator.comparingInt(a -> a[0] * a[0] + a[1] * a[1]));
        SAMPLES = pts.toArray(new int[0][]);
    }

    /**
     * Level, dry ground across the whole disc, judged cheaply first: the biome at the middle and four
     * points out must be the kingdom's, then the preliminary surface is read at ~115 points from the
     * middle outward - at most 8 % wet and 85 % within ten blocks of the median, giving up as soon
     * as the budget is spent - and only then the real ground is read at nine points for the exact
     * height. The median, or MIN_VALUE. ({@code /locate} runs this for every candidate cell it looks
     * at, so the cheap tests come first.)
     */
    static int kingdomSite(GenerationContext context, int x, int z) {
        return kingdomSite(context, x, z, null);
    }

    /** As above; when {@code why} is given, why[0] is set to the stage that failed (1 biome, 2 middle wet, 3 wet, 4 uneven, 5 real ground) or 0. */
    public static int kingdomSite(GenerationContext context, int x, int z, int[] why) {
        if (why != null) why[0] = 0;
        if (!Terrain.biomesOk(context, x, z, 32)) { if (why != null) why[0] = 1; return Integer.MIN_VALUE; }
        int sea = context.chunkGenerator().getSeaLevel();
        int h0 = Terrain.fastDry(context, x, z);
        if (h0 == Integer.MIN_VALUE || h0 < sea + 1) { if (why != null) why[0] = 2; return Integer.MIN_VALUE; }
        int total = SAMPLES.length;
        int wetBudget = total * 8 / 100, offBudget = total * 15 / 100; // the terrain adaptation levels a dozen blocks either way, the ring clears and fills the rest
        List<Integer> hs = new ArrayList<>(total);
        int wet = 0, off = 0;
        for (int[] s : SAMPLES) {
            int h = Terrain.fastDry(context, x + s[0], z + s[1]);
            if (h == Integer.MIN_VALUE) wet++;
            else {
                hs.add(h);
                if (Math.abs(h - h0) > 11) off++;
            }
            if (wet > wetBudget || wet + off > offBudget) { if (why != null) why[0] = wet > wetBudget ? 3 : 4; return Integer.MIN_VALUE; }
        }
        Collections.sort(hs);
        int median = hs.get(hs.size() / 2);
        off = 0;
        for (int h : hs) if (Math.abs(h - median) > 10) off++;
        if (wet + off > offBudget) { if (why != null) why[0] = 4; return Integer.MIN_VALUE; }
        // the real ground at the middle and eight points round the inner town: all dry, and the exact median
        List<Integer> real = new ArrayList<>(9);
        for (int[] s : new int[][]{{0, 0}, {40, 0}, {-40, 0}, {0, 40}, {0, -40}, {28, 28}, {-28, 28}, {28, -28}, {-28, -28}}) {
            int h = Terrain.dryHeight(context, x + s[0], z + s[1]);
            if (h == Integer.MIN_VALUE || Math.abs(h - median) > 12) { if (why != null) why[0] = 5; return Integer.MIN_VALUE; }
            real.add(h);
        }
        Collections.sort(real);
        return real.get(real.size() / 2);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
        int y = kingdomSite(context, x, z);
        if (y == Integer.MIN_VALUE) return Optional.empty();
        if (y < context.chunkGenerator().getSeaLevel() + 1) return Optional.empty();
        int style = RuinStructure.styleOf(context, x, y, z);
        BlockPos origin = new BlockPos(x, y, z);
        RandomSource rng = context.random();
        return Optional.of(new GenerationStub(origin, builder -> layout(builder, origin, style, rng)));
    }

    /** Angles are in degrees, 0 = east (+x), 90 = south (+z); r is the distance from the middle. */
    private static int[] at(double angleDeg, double r) {
        double a = Math.toRadians(angleDeg);
        return new int[]{(int) Math.round(Math.cos(a) * r), (int) Math.round(Math.sin(a) * r)};
    }

    private void layout(StructurePiecesBuilder builder, BlockPos origin, int style, RandomSource rng) {
        int cx = origin.getX(), cy = origin.getY(), cz = origin.getZ();
        long seed = rng.nextLong();
        List<int[]> people = new ArrayList<>();
        List<RuinPiece> houses = new ArrayList<>();

        // the inner ring, between the bailey and the ring road: houses facing out to the road, the two
        // markets flanking the south road, the chapel and the big house on the north side
        for (int k = 0; k < 8; k++) {
            double a = 22.5 + 45 * k;
            if (k == 1 || k == 2) continue; // the markets
            RuinPiece.Kind kind = RuinPiece.Kind.COTTAGE;
            int r = 33;
            int v = RuinPiece.V_INTACT;
            if (k == 5) { // 247.5: the chapel, long side along the radius
                kind = RuinPiece.Kind.CHAPEL;
                r = 31;
            } else if (k == 6 || rng.nextInt(3) == 0) {
                v |= RuinPiece.V_BIG;
            }
            int[] p = at(a, r);
            Rotation rot = RuinStructure.facing(p[0], p[1]); // the front towards the ring road
            houses.add(new RuinPiece(kind, new BlockPos(cx + p[0], cy, cz + p[1]), rot, style, v, 0f, rng.nextLong()));
            int[] door = at(a, r + (kind == RuinPiece.Kind.CHAPEL ? 8 : 5));
            people.add(new int[]{door[0], door[1], 0, k == 5 ? TownsfolkEntity.SCRIBE : townsfolkFor(k, rng)});
        }
        for (int side = -1; side <= 1; side += 2) {
            int mx = side * 13, mz = 30;
            houses.add(new RuinPiece(RuinPiece.Kind.MARKET, new BlockPos(cx + mx, cy, cz + mz), RuinStructure.facing(-mx, 0), style, RuinPiece.V_INTACT, 0f, rng.nextLong()));
        }
        // the market's people stand between the stalls and the south road
        people.add(new int[]{5, 28, 0, TownsfolkEntity.SURVEYOR});
        people.add(new int[]{-5, 33, 0, TownsfolkEntity.PROVISIONER});
        people.add(new int[]{6, 35, 0, TownsfolkEntity.CHANDLER});

        // the outer ring, between the ring road and the wall: houses facing in to the road, farms east and west
        double[] outer = {30, 60, 120, 150, 210, 240, 300, 330};
        for (int i = 0; i < outer.length; i++) {
            int[] p = at(outer[i], 47);
            int v = RuinPiece.V_INTACT | (rng.nextInt(10) < 4 ? RuinPiece.V_BIG : 0);
            Rotation rot = RuinStructure.facing(-p[0], -p[1]);
            houses.add(new RuinPiece(RuinPiece.Kind.COTTAGE, new BlockPos(cx + p[0], cy, cz + p[1]), rot, style, v, 0f, rng.nextLong()));
            int[] door = at(outer[i], 42);
            people.add(new int[]{door[0], door[1], 0, townsfolkFor(i + 3, rng)});
        }
        houses.add(new RuinPiece(RuinPiece.Kind.FARM, new BlockPos(cx + 48, cy, cz), Rotation.CLOCKWISE_90, style, RuinPiece.V_INTACT, 0f, rng.nextLong()));
        houses.add(new RuinPiece(RuinPiece.Kind.FARM, new BlockPos(cx - 48, cy, cz), Rotation.COUNTERCLOCKWISE_90, style, RuinPiece.V_INTACT, 0f, rng.nextLong()));
        people.add(new int[]{45, 3, 0, TownsfolkEntity.PROVISIONER});
        people.add(new int[]{-45, -3, 0, TownsfolkEntity.PROVISIONER});
        // a few more townsfolk about the roads, and spearmen walking the outer band
        people.add(new int[]{3, 44, 0, TownsfolkEntity.SCRIBE});
        people.add(new int[]{-3, -44, 0, TownsfolkEntity.RELIC_MONGER});
        for (double a : new double[]{0, 90, 180, 270}) {
            int[] p = at(a + 12, 47);
            people.add(new int[]{p[0], p[1], 1, GuardEntity.SPEARMAN});
        }

        // the ring first (it levels the ground the rest stands on), then the castle, then the houses
        builder.addPiece(new KingdomWallPiece(origin, seed, people));
        builder.addPiece(new KeepPiece(origin, rng.nextLong()));
        for (RuinPiece h : houses) builder.addPiece(h);
    }

    private static int townsfolkFor(int i, RandomSource rng) {
        int[] common = {TownsfolkEntity.PROVISIONER, TownsfolkEntity.CHANDLER, TownsfolkEntity.SMITH, TownsfolkEntity.SCRIBE, TownsfolkEntity.PROVISIONER, TownsfolkEntity.CHANDLER};
        if (rng.nextInt(8) == 0) return TownsfolkEntity.RELIC_MONGER;
        return common[Math.floorMod(i, common.length)];
    }

    @Override
    public StructureType<?> type() {
        return WakingStructures.KINGDOM.get();
    }
}
