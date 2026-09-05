package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.lovkar.wakingworld.kingdom.GuardEntity;
import me.lovkar.wakingworld.kingdom.KingEntity;
import me.lovkar.wakingworld.kingdom.TownsfolkEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Mob;

import java.util.EnumMap;
import java.util.Map;

/** Draws a person's {@link GarbModel} over the skin (and under nothing: the garb goes on last, over the armour). */
public class GarbLayer<T extends Mob> extends RenderLayer<T, HumanoidModel<T>> {
    private final Map<GarbModel.Kind, GarbModel<T>> models = new EnumMap<>(GarbModel.Kind.class);

    public GarbLayer(RenderLayerParent<T, HumanoidModel<T>> parent, EntityModelSet set) {
        super(parent);
        for (GarbModel.Kind k : GarbModel.Kind.values()) models.put(k, new GarbModel<>(set.bakeLayer(k.layer)));
    }

    static GarbModel.Kind kindOf(Mob entity) {
        if (entity instanceof GuardEntity g) {
            int k = g.kind();
            return k == GuardEntity.ARCHER ? GarbModel.Kind.ARCHER : k == GuardEntity.SPEARMAN ? GarbModel.Kind.SPEARMAN : GarbModel.Kind.KNIGHT;
        }
        if (entity instanceof TownsfolkEntity t) {
            GarbModel.Kind[] byProfession = {GarbModel.Kind.SURVEYOR, GarbModel.Kind.RELIC_MONGER, GarbModel.Kind.SMITH, GarbModel.Kind.PROVISIONER, GarbModel.Kind.CHANDLER, GarbModel.Kind.SCRIBE};
            return byProfession[Math.floorMod(t.profession(), byProfession.length)];
        }
        if (entity instanceof KingEntity) return GarbModel.Kind.KING;
        return null;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
        GarbModel.Kind kind = kindOf(entity);
        if (kind == null || entity.isInvisible()) return;
        GarbModel<T> garb = models.get(kind);
        getParentModel().copyPropertiesTo(garb);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(kind.texture));
        garb.renderToBuffer(pose, vc, light, LivingEntityRenderer.getOverlayCoords(entity, 0.0F));
    }
}
