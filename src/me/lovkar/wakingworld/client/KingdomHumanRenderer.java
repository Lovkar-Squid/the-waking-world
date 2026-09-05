package me.lovkar.wakingworld.client;

import com.mojang.blaze3d.vertex.PoseStack;
import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.kingdom.GuardEntity;
import me.lovkar.wakingworld.kingdom.KingEntity;
import me.lovkar.wakingworld.kingdom.TownsfolkEntity;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

/**
 * The kingdom's people, drawn as the player is: a 64x64 skin on the humanoid model, the armour
 * they wear over it, what they hold in their hands. Guards draw their bows when they have a target.
 */
public class KingdomHumanRenderer<T extends Mob> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
    private static final String BASE = "textures/entity/kingdom/";
    private static final ResourceLocation[] GUARDS = {tex("guard_archer"), tex("guard_knight"), tex("guard_spearman")};
    private static final ResourceLocation[] TOWNSFOLK = new ResourceLocation[TownsfolkEntity.PROFESSIONS.length];
    private static final ResourceLocation KING = tex("king");

    static {
        for (int i = 0; i < TOWNSFOLK.length; i++) TOWNSFOLK[i] = tex("townsfolk_" + TownsfolkEntity.PROFESSIONS[i]);
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, BASE + name + ".png");
    }

    /** The king's model sits: he never leaves his throne. */
    public static final class SeatedModel<T extends Mob> extends HumanoidModel<T> {
        public SeatedModel(net.minecraft.client.model.geom.ModelPart root) {
            super(root);
        }

        @Override
        public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            this.riding = true;
            super.setupAnim(entity, limbSwing, 0F, ageInTicks, netHeadYaw, headPitch);
        }
    }

    public KingdomHumanRenderer(EntityRendererProvider.Context context, boolean seated) {
        super(context, seated ? new SeatedModel<>(context.bakeLayer(ModelLayers.ZOMBIE)) : new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
        this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)), context.getModelManager()));
        this.addLayer(new GarbLayer<>(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        if (entity instanceof GuardEntity g) return GUARDS[Math.floorMod(g.kind(), GUARDS.length)];
        if (entity instanceof TownsfolkEntity t) return TOWNSFOLK[Math.floorMod(t.profession(), TOWNSFOLK.length)];
        return KING;
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack main = entity.getMainHandItem(), off = entity.getOffhandItem();
        HumanoidModel.ArmPose mainPose = HumanoidModel.ArmPose.EMPTY, offPose = HumanoidModel.ArmPose.EMPTY;
        if (!main.isEmpty()) {
            mainPose = HumanoidModel.ArmPose.ITEM;
            if (main.getItem() instanceof BowItem && entity.isAggressive()) mainPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            if (main.getItem() instanceof CrossbowItem) mainPose = HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
        if (!off.isEmpty()) offPose = off.getItem() instanceof ShieldItem ? HumanoidModel.ArmPose.BLOCK : HumanoidModel.ArmPose.ITEM;
        if (entity instanceof KingEntity) {
            mainPose = HumanoidModel.ArmPose.EMPTY;
            offPose = HumanoidModel.ArmPose.EMPTY;
        }
        boolean rightMain = entity.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        this.model.rightArmPose = rightMain ? mainPose : offPose;
        this.model.leftArmPose = rightMain ? offPose : mainPose;
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
}
