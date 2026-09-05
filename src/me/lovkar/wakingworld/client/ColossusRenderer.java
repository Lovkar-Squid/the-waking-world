package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import me.lovkar.wakingworld.body.ColossusBody;
import me.lovkar.wakingworld.body.ColossusPose;
import me.lovkar.wakingworld.body.PartDef;
import me.lovkar.wakingworld.entity.Attack;
import me.lovkar.wakingworld.entity.ColossusEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Draws a colossus: the baked block geometry of each part, posed. The body is rotated the way
 * living entities are (180 - body yaw); the torso pitches and twists, the head and arms ride on
 * it, legs and arms swing about their pivots with the walk animation and the attack overlay.
 * Cores are drawn full-bright and pulse with the phase; broken cores go dark. Light is sampled
 * per part where that part actually is - the head of a 40-block giant is in the sky even when its
 * feet stand in a shaded valley.
 */
public class ColossusRenderer extends EntityRenderer<ColossusEntity> {
    private static final float[] NO_SHADE = {1f, 1f, 1f, 1f};
    private final int[] light = new int[4];
    private final int[] fullBright = {LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT, LightTexture.FULL_BRIGHT};

    public ColossusRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 3.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ColossusEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(ColossusEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return frustum.isVisible(entity.getBoundingBoxForCulling()) || entity.noCulling;
    }

    @Override
    public void render(ColossusEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        ColossusBody body = entity.body();
        BakedBody baked = BakedBody.get(body);

        float bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTick, entity.yHeadRotO, entity.yHeadRot) - bodyYaw);
        float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
        ColossusPose pose = ColossusPose.walking(entity.stride(partialTick),
                entity.walkAnimation.speed(partialTick), headYaw, pitch, body.height);
        Attack attack = entity.clientAttack();
        if (attack != Attack.NONE) {
            pose.attack(attack.id, entity.attackProgress(partialTick), attack.impact / (float) attack.duration, entity.stompRightFoot(),
                    attack == Attack.LEAP && !entity.onGround());
        }
        float flinch = entity.flinch(partialTick);
        if (flinch > 0f) pose.flinch(flinch, entity.flinchPower(), body.height);
        float deathTicks = entity.deathTime > 0 ? entity.deathTime + partialTick : 0f;
        if (deathTicks > 0) {
            pose.dying(deathTicks, body.height);
        }
        float rise = entity.wakeProgress(partialTick);
        if (rise < 1f) {
            // coming up out of the ground: head down, shoulders hunched, straightening as it rises
            pose.rising(rise);
        }
        int overlay = OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(entity.hurtTime > 0 || (entity.deathTime > 0 && entity.deathTime < 20)));

        double theta = Math.toRadians(180.0 - bodyYaw);
        double cos = Math.cos(theta), sin = Math.sin(theta);

        poseStack.pushPose();
        if (rise < 1f) {
            float s = rise * rise * (3f - 2f * rise);
            poseStack.translate(0.0, -(1f - s) * (body.height + 2.0), 0.0);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        if (pose.lift != 0f) poseStack.translate(0.0, pose.lift, 0.0);      // the fallen body rests on the ground
        if (pose.fall != 0f) poseStack.mulPose(Axis.XP.rotation(pose.fall)); // toppling forward onto its face
        if (pose.drop != 0f) poseStack.translate(0.0, pose.drop, 0.0);      // down on one knee

        PartDef torsoDef = body.part(PartDef.Kind.TORSO);
        int phase = entity.phase();
        int broken = entity.brokenCores();
        float pulse = phase >= 2 ? 0.75f + 0.25f * Mth.sin((entity.tickCount + partialTick) * (phase >= 3 ? 0.45f : 0.2f)) : 1f;

        for (BakedBody.BakedPart part : baked.parts) {
            PartDef def = part.def;
            if (deathTicks > 0 && ColossusPose.crumbled(def.kind, deathTicks)) continue; // already rubble on the ground
            boolean onTorso = def.kind != PartDef.Kind.TORSO && !def.isLeg();
            float bob = def.isLeg() ? 0f : pose.bob;
            poseStack.pushPose();
            if (torsoDef != null && (def.kind == PartDef.Kind.TORSO || onTorso)) {
                // torso transform: pitch and twist about the hips; the head and arms inherit it
                poseStack.translate(torsoDef.px, torsoDef.py + bob, torsoDef.pz);
                poseStack.mulPose(Axis.YP.rotation(pose.torsoYaw));
                poseStack.mulPose(Axis.XP.rotation(-pose.torsoPitch));
                poseStack.translate(def.px - torsoDef.px, def.py - torsoDef.py, def.pz - torsoDef.pz);
            } else {
                poseStack.translate(def.px, def.py + bob, def.pz);
            }
            switch (def.kind) {
                case HEAD -> {
                    // Minecraft yaw grows clockwise seen from above and pitch grows looking down;
                    // in our right-handed body space (front = -Z) both are negative rotations.
                    poseStack.mulPose(Axis.YP.rotation(-pose.headYaw));
                    poseStack.mulPose(Axis.XP.rotation(-pose.headPitch));
                }
                case TORSO -> { }
                case LEFT_ARM, RIGHT_ARM -> {
                    float spread = def.kind == PartDef.Kind.RIGHT_ARM ? pose.armSpread : -pose.armSpread;
                    poseStack.mulPose(Axis.ZP.rotation(spread));
                    poseStack.mulPose(Axis.XP.rotation(pose.limbAngle(def.kind)));
                }
                default -> poseStack.mulPose(Axis.XP.rotation(pose.limbAngle(def.kind)));
            }

            // light where this part is: pivot + half the part's height, rotated into the world
            double lx = def.px, ly = def.py + bob + (def.oy + def.sy / 2.0 - def.py), lz = def.pz;
            double wx = entity.getX() + lx * cos + lz * sin;
            double wz = entity.getZ() - lx * sin + lz * cos;
            int l = LevelRenderer.getLightColor(entity.level(), BlockPos.containing(wx, entity.getY() + ly, wz));
            light[0] = light[1] = light[2] = light[3] = l;

            PoseStack.Pose last = poseStack.last();
            drawLayers(part.plain, buffer, last, light, overlay, 1f, 1f, 1f);
            for (int g = 0; g < part.glowing.length; g++) {
                boolean isCore = g < body.cores.size();
                // waking: the cores light up one after another as it comes up, the eyes last of all
                float wakeAt = isCore ? 0.45f + 0.1f * g : 0.97f;
                boolean asleep = rise < 1f && rise < wakeAt;
                if (isCore && (broken & (1 << g)) != 0 || asleep) {
                    // dead (or not yet lit) core: dark, lit like the rest of the body
                    drawLayers(part.glowing[g], buffer, last, light, overlay, 0.25f, 0.22f, 0.22f);
                } else if (isCore) {
                    boolean flash = entity.coreFlash(g) > 0;
                    drawLayers(part.glowing[g], buffer, last, fullBright, overlay, 1f, flash ? 1f : pulse, flash ? 1f : pulse);
                } else {
                    drawLayers(part.glowing[g], buffer, last, fullBright, overlay, 1f, 1f, 1f);
                }
            }
            poseStack.popPose();
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    private static void drawLayers(BakedBody.Layer[] layers, MultiBufferSource buffer, PoseStack.Pose pose, int[] light, int overlay,
                                   float mr, float mg, float mb) {
        for (BakedBody.Layer layer : layers) {
            VertexConsumer vc = buffer.getBuffer(layer.type);
            BakedQuad[] quads = layer.quads;
            int[] colors = layer.colors;
            for (int i = 0; i < quads.length; i++) {
                int c = colors[i];
                if (c < 0) {
                    vc.putBulkData(pose, quads[i], NO_SHADE, mr, mg, mb, 1f, light, overlay, true);
                } else {
                    vc.putBulkData(pose, quads[i], NO_SHADE, ((c >> 16) & 255) / 255f * mr, ((c >> 8) & 255) / 255f * mg, (c & 255) / 255f * mb, 1f, light, overlay, true);
                }
            }
        }
    }
}
