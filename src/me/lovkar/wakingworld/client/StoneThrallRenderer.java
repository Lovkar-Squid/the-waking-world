package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.StoneThrallEntity;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The zombie's shape in the thrall's stone skin. */
public class StoneThrallRenderer extends HumanoidMobRenderer<StoneThrallEntity, ZombieModel<StoneThrallEntity>> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/entity/stone_thrall.png");
    private static final ResourceLocation HOLLOW = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/entity/hollow_thrall.png");

    public StoneThrallRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(StoneThrallEntity entity) {
        return entity.variant() == StoneThrallEntity.HOLLOW ? HOLLOW : SKIN;
    }
}
