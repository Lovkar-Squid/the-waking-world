package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.entity.RuneSentinelEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.resources.ResourceLocation;

/** The skeleton's shape in the sentinel's rune-marked stone. */
public class RuneSentinelRenderer extends SkeletonRenderer<RuneSentinelEntity> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/entity/rune_sentinel.png");

    public RuneSentinelRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(RuneSentinelEntity entity) {
        return SKIN;
    }
}
