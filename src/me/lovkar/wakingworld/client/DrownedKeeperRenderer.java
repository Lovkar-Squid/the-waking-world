package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.client.renderer.entity.DrownedRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Zombie;

/** The drowned's shape and rags over the keeper's dark skin. */
public class DrownedKeeperRenderer extends DrownedRenderer {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/entity/drowned_keeper.png");

    public DrownedKeeperRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie entity) {
        return SKIN;
    }
}
