package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.lovkar.wakingworld.ritual.AltarBlockEntity;
import me.lovkar.wakingworld.ritual.Rites;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the offerings floating in a slow ring over the altar; while the rite runs they spin
 * faster, climb and close in on the beam that the altar throws up into the sky in the kind's
 * colour. Over the Titan's altar the six lesser altars answer first, each with a beam in its land's
 * colour, then the arena's eight pillars light with beams of their own, one by one, as the
 * ceremony reaches them.
 */
public class AltarRenderer implements BlockEntityRenderer<AltarBlockEntity> {
    private final ItemRenderer items;
    private final net.minecraft.client.renderer.block.BlockRenderDispatcher blocks;

    public AltarRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
        this.blocks = context.getBlockRenderDispatcher();
    }

    /** The glowing block that floats over an altar of a kind. */
    static net.minecraft.world.level.block.state.BlockState heartBlock(String kind) {
        return switch (kind) {
            case "ice", "prismarine" -> net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState();
            case "sandstone" -> net.minecraft.world.level.block.Blocks.GLOWSTONE.defaultBlockState();
            case "moss" -> net.minecraft.world.level.block.Blocks.SHROOMLIGHT.defaultBlockState();
            case "titan" -> net.minecraft.world.level.block.Blocks.CRYING_OBSIDIAN.defaultBlockState();
            case "earth" -> net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState();
            default -> net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState();
        };
    }

    /** The rune stones that circle it. */
    static net.minecraft.world.level.block.state.BlockState stoneBlock(String kind) {
        return switch (kind) {
            case "earth" -> net.minecraft.world.level.block.Blocks.PACKED_MUD.defaultBlockState();
            case "sandstone" -> net.minecraft.world.level.block.Blocks.CHISELED_SANDSTONE.defaultBlockState();
            case "ice" -> net.minecraft.world.level.block.Blocks.BLUE_ICE.defaultBlockState();
            case "prismarine" -> net.minecraft.world.level.block.Blocks.DARK_PRISMARINE.defaultBlockState();
            case "moss" -> net.minecraft.world.level.block.Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
            case "titan" -> net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState();
            default -> net.minecraft.world.level.block.Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
        };
    }

    @Override
    public void render(AltarBlockEntity altar, float partialTick, PoseStack pose, MultiBufferSource buffer, int light, int overlay) {
        float spin = Mth.lerp(partialTick, altar.spinO, altar.spin);
        int total = Math.max(1, altar.riteTotal());
        float p = altar.rite() > 0 ? 1f - (altar.rite() - partialTick) / (float) total : 0f;
        long time = altar.getLevel() == null ? 0 : altar.getLevel().getGameTime();
        int color = Rites.color(altar.kind());

        // the offerings
        int n = 0;
        for (ItemStack s : altar.offerings()) if (!s.isEmpty()) n++;
        if (n > 0) {
            int i = 0;
            for (ItemStack s : altar.offerings()) {
                if (s.isEmpty()) continue;
                float a = (float) Math.toRadians(spin + i * 360f / n);
                float r = 0.55f * (1f - p * p);
                float bob = 0.08f * Mth.sin((spin + i * 40f) * 0.05f);
                float y = 1.25f + bob + p * p * 2.2f;
                pose.pushPose();
                pose.translate(0.5 + Mth.cos(a) * r, y, 0.5 + Mth.sin(a) * r);
                pose.mulPose(Axis.YP.rotationDegrees(spin * 2f + i * 60f));
                float sc = 0.45f * (1f - 0.5f * p * p);
                pose.scale(sc, sc, sc);
                items.renderStatic(s, ItemDisplayContext.GROUND, p > 0 ? LightTexture.FULL_BRIGHT : light, overlay, pose, buffer, altar.getLevel(), (int) altar.getBlockPos().asLong());
                pose.popPose();
                i++;
            }
        }

        // the altar's heart: the kind's glowing block floats over the altar and turns, and four rune stones circle it
        {
            float pulse = 0.5f + 0.5f * Mth.sin((time + partialTick) * 0.08f);
            boolean ready = altar.complete() && altar.rite() == 0;
            float heartY = 1.55f + 0.06f * Mth.sin((time + partialTick) * 0.05f) + p * 1.2f;
            float heartScale = (ready ? 0.42f + 0.06f * pulse : 0.34f) * (1f + p * 0.8f);
            pose.pushPose();
            pose.translate(0.5, heartY, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(spin * (ready ? 2.5f : 0.9f)));
            pose.mulPose(Axis.XP.rotationDegrees(ready ? spin * 0.7f : 20f));
            pose.scale(heartScale, heartScale, heartScale);
            pose.translate(-0.5, -0.5, -0.5);
            blocks.renderSingleBlock(heartBlock(Rites.base(altar.kind())), pose, buffer, LightTexture.FULL_BRIGHT, overlay);
            pose.popPose();
            int stones = 4;
            for (int k = 0; k < stones; k++) {
                float a = (float) Math.toRadians(-spin * (0.6f + 0.4f * n) + k * 360f / stones);
                float r = 1.15f - 0.35f * p;
                float y = 1.0f + 0.25f * Mth.sin((time + partialTick) * 0.06f + k * 1.6f) + p * 1.6f;
                pose.pushPose();
                pose.translate(0.5 + Mth.cos(a) * r, y, 0.5 + Mth.sin(a) * r);
                pose.mulPose(Axis.YP.rotationDegrees(spin * 1.5f + k * 90f));
                pose.mulPose(Axis.ZP.rotationDegrees(15f));
                float sc = 0.2f;
                pose.scale(sc, sc, sc);
                pose.translate(-0.5, -0.5, -0.5);
                blocks.renderSingleBlock(stoneBlock(Rites.base(altar.kind())), pose, buffer, k < n || ready || p > 0 ? LightTexture.FULL_BRIGHT : light, overlay);
                pose.popPose();
            }
            // ready and waiting for the horn: a thin beam already stands
            if (ready) BeaconRenderer.renderBeaconBeam(pose, buffer, BeaconRenderer.BEAM_LOCATION, partialTick, 1f, time, 1, 96, 0xFF000000 | color, 0.06f + 0.02f * pulse, 0.12f);
        }

        // the rite's beam
        if (altar.rite() > 0) {
            int argb = 0xFF000000 | color;
            float radius = 0.15f + 0.35f * p, glow = 0.3f + 0.5f * p;
            BeaconRenderer.renderBeaconBeam(pose, buffer, BeaconRenderer.BEAM_LOCATION, partialTick, 1f, time, 1, 220, argb, radius, glow);
            if ("titan".equals(altar.kind())) {
                int elapsed = total - altar.rite();
                // the six lesser altars' answers: a beam in the colour of each land, from its altar on the ring
                for (int i = 0; i < Rites.LANDS.length; i++) {
                    if (elapsed < me.lovkar.wakingworld.ritual.Ceremony.lesserAnswer(i)) continue;
                    int[] o = me.lovkar.wakingworld.worldgen.TitanArenaPiece.lesserOffset(i);
                    pose.pushPose();
                    pose.translate(o[0], me.lovkar.wakingworld.worldgen.TitanArenaPiece.LESSER_DY + 1, o[1]);
                    BeaconRenderer.renderBeaconBeam(pose, buffer, BeaconRenderer.BEAM_LOCATION, partialTick, 1f, time + i * 11, 0, 120, 0xFF000000 | Rites.color(Rites.LANDS[i]), 0.08f + 0.1f * p, 0.18f + 0.22f * p);
                    pose.popPose();
                }
                for (int k = 0; k < 8; k++) {
                    if (elapsed < me.lovkar.wakingworld.ritual.Ceremony.PILLAR_LIGHT + k * 12) continue;
                    double ang = Math.toRadians(22.5 + 45 * k);
                    pose.pushPose();
                    pose.translate(Math.cos(ang) * 42, 27, Math.sin(ang) * 42);
                    BeaconRenderer.renderBeaconBeam(pose, buffer, BeaconRenderer.BEAM_LOCATION, partialTick, 1f, time + k * 7, 0, 160, 0xFFB266FF, 0.12f + 0.15f * p, 0.25f + 0.3f * p);
                    pose.popPose();
                }
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(AltarBlockEntity altar) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
