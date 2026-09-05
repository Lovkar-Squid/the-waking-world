package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.EmberWraithEntity;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** The zombie's shape in charred skin with the coals showing through. */
public class EmberWraithRenderer extends HumanoidMobRenderer<EmberWraithEntity, ZombieModel<EmberWraithEntity>> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/entity/ember_wraith.png");

    public EmberWraithRenderer(EntityRendererProvider.Context context) {
        super(context, new ZombieModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(EmberWraithEntity entity) {
        return SKIN;
    }

    @Override
    protected int getBlockLightLevel(EmberWraithEntity entity, net.minecraft.core.BlockPos pos) {
        return 15; // it glows from within
    }
}
