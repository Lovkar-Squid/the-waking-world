package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.body.ColossusBody;
import me.lovkar.wakingworld.body.PartDef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A colossus body turned into geometry, once. Every cell's block model is asked for its quads,
 * faces buried against a neighbouring cell of the same part are dropped, and the surviving quads
 * are copied with their vertices pre-translated to the part's pivot space - so drawing a part is
 * one pose per part and a plain putBulkData per quad, no per-block matrix work. Quads of blocks
 * that emit light (the cores and eyes) are kept apart so they can be drawn full-bright.
 *
 * Geometry goes through the entity render types on the block atlas (like blocks held in a hand
 * do), so the hurt flash overlay works and Iris/Oculus treat the giant as an entity.
 */
public final class BakedBody {
    private static final int MAX_CACHED = 12;
    private static final Map<Long, BakedBody> CACHE = new LinkedHashMap<>();

    public final ColossusBody body;
    public final List<BakedPart> parts;
    public final int quadCount;

    private BakedBody(ColossusBody body, List<BakedPart> parts, int quadCount) {
        this.body = body;
        this.parts = parts;
        this.quadCount = quadCount;
    }

    public static BakedBody get(ColossusBody body) {
        long key = body.key();
        BakedBody baked = CACHE.get(key);
        if (baked == null) {
            baked = bake(body);
            if (CACHE.size() >= MAX_CACHED) {
                Long oldest = CACHE.keySet().iterator().next();
                CACHE.remove(oldest);
            }
            CACHE.put(key, baked);
        }
        return baked;
    }

    /** Drop everything - after a resource reload the sprites in the baked quads are stale. */
    public static void clear() {
        CACHE.clear();
    }

    private static BakedBody bake(ColossusBody body) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BlockColors colors = Minecraft.getInstance().getBlockColors();
        RandomSource random = RandomSource.create();
        List<BakedPart> parts = new ArrayList<>();
        int total = 0;
        int groups = body.cores.size() + 1; // one per core, the last for eyes and any other light
        for (PartDef def : body.parts) {
            Map<RenderType, LayerBuilder> plain = new LinkedHashMap<>();
            List<Map<RenderType, LayerBuilder>> glowing = new ArrayList<>();
            for (int g = 0; g < groups; g++) glowing.add(new LinkedHashMap<>());
            for (int y = 0; y < def.sy; y++) {
                for (int z = 0; z < def.sz; z++) {
                    for (int x = 0; x < def.sx; x++) {
                        BlockState state = def.get(x, y, z);
                        if (state == null) continue;
                        BakedModel model = dispatcher.getBlockModel(state);
                        RenderType layer = layerFor(state);
                        Map<RenderType, LayerBuilder> target = state.getLightEmission() > 0
                                ? glowing.get(coreGroup(body, def.ox + x + 0.5, def.oy + y + 0.5, def.oz + z + 0.5)) : plain;
                        LayerBuilder out = target.computeIfAbsent(layer, k -> new LayerBuilder());
                        float lx = def.localX(x), ly = def.localY(y), lz = def.localZ(z);
                        for (Direction dir : Direction.values()) {
                            BlockState neighbour = def.get(x + dir.getStepX(), y + dir.getStepY(), z + dir.getStepZ());
                            if (neighbour != null && neighbour.canOcclude() && state.canOcclude()) continue;
                            random.setSeed(42L);
                            for (BakedQuad q : model.getQuads(state, dir, random)) {
                                out.add(translate(q, lx, ly, lz), tint(colors, state, q));
                                total++;
                            }
                        }
                        random.setSeed(42L);
                        for (BakedQuad q : model.getQuads(state, null, random)) {
                            out.add(translate(q, lx, ly, lz), tint(colors, state, q));
                            total++;
                        }
                    }
                }
            }
            Layer[][] glow = new Layer[groups][];
            for (int g = 0; g < groups; g++) glow[g] = freeze(glowing.get(g));
            parts.add(new BakedPart(def, freeze(plain), glow));
        }
        return new BakedBody(body, List.copyOf(parts), total);
    }

    /** Which core a glowing cell belongs to (index into body.cores), or cores.size() for eyes and strays. */
    private static int coreGroup(ColossusBody body, double cx, double cy, double cz) {
        int best = body.cores.size();
        double bestD = 2.6 * 2.6;
        for (int i = 0; i < body.cores.size(); i++) {
            double[] c = body.cores.get(i);
            double dx = cx - c[0], dy = cy - c[1], dz = cz - c[2];
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /** Biome-independent tint for grass, foliage and the like (0xRRGGBB); -1 = untinted. */
    private static int tint(BlockColors colors, BlockState state, BakedQuad q) {
        if (q.getTintIndex() < 0) return -1;
        try {
            int c = colors.getColor(state, null, null, q.getTintIndex());
            return c == -1 ? -1 : c & 0xFFFFFF;
        } catch (RuntimeException ex) {
            return -1;
        }
    }

    private static RenderType layerFor(BlockState state) {
        RenderType chunk = ItemBlockRenderTypes.getChunkRenderType(state);
        if (chunk == RenderType.translucent()) return RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS);
        if (chunk == RenderType.cutout() || chunk == RenderType.cutoutMipped()) return RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
        return RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS);
    }

    private static Layer[] freeze(Map<RenderType, LayerBuilder> in) {
        Layer[] out = new Layer[in.size()];
        int i = 0;
        for (Map.Entry<RenderType, LayerBuilder> e : in.entrySet()) {
            out[i++] = e.getValue().build(e.getKey());
        }
        return out;
    }

    private static final class LayerBuilder {
        final List<BakedQuad> quads = new ArrayList<>();
        final List<Integer> colors = new ArrayList<>();

        void add(BakedQuad q, int color) {
            quads.add(q);
            colors.add(color);
        }

        Layer build(RenderType type) {
            int[] c = new int[colors.size()];
            for (int i = 0; i < c.length; i++) c[i] = colors.get(i);
            return new Layer(type, quads.toArray(new BakedQuad[0]), c);
        }
    }

    /** Quads of one render type; colors[i] is the 0xRRGGBB tint of quads[i], or -1 for none. */
    public static final class Layer {
        public final RenderType type;
        public final BakedQuad[] quads;
        public final int[] colors;

        Layer(RenderType type, BakedQuad[] quads, int[] colors) {
            this.type = type;
            this.quads = quads;
            this.colors = colors;
        }
    }

    /** A copy of the quad with every vertex moved by (dx, dy, dz). */
    private static BakedQuad translate(BakedQuad q, float dx, float dy, float dz) {
        int[] src = q.getVertices();
        int[] v = src.clone();
        int stride = v.length / 4;
        for (int i = 0; i < 4; i++) {
            int o = i * stride;
            v[o] = Float.floatToRawIntBits(Float.intBitsToFloat(v[o]) + dx);
            v[o + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(v[o + 1]) + dy);
            v[o + 2] = Float.floatToRawIntBits(Float.intBitsToFloat(v[o + 2]) + dz);
        }
        return new BakedQuad(v, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade());
    }

    /** Geometry of one part, in pivot space: plain layers, and glowing layers per core group (last group = eyes). */
    public static final class BakedPart {
        public final PartDef def;
        public final Layer[] plain;
        public final Layer[][] glowing;

        BakedPart(PartDef def, Layer[] plain, Layer[][] glowing) {
            this.def = def;
            this.plain = plain;
            this.glowing = glowing;
        }
    }
}
