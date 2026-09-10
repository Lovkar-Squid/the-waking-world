package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.WakingWorld;
import me.lovkar.wakingworld.mage.MageEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class MageModel extends HierarchicalModel<MageEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("wakingworld", "dark_mage"), "main");

    private final ModelPart root, head, body, mantle, skirt, hem, cloak, rightArm, leftArm, staff, crystal, orb;

    public MageModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.mantle = body.getChild("mantle");
        this.skirt = body.getChild("skirt");
        this.hem = skirt.getChild("hem");
        this.cloak = body.getChild("cloak");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.staff = rightArm.getChild("staff");
        this.crystal = staff.getChild("crystal");
        this.orb = root.getChild("orb");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -9.0F, -4.5F, 9.0F, 9.0F, 9.0F), PartPose.ZERO);
        // the hood's point, laid back over the crown
        head.addOrReplaceChild("peak", CubeListBuilder.create()
                .texOffs(36, 0).addBox(-3.0F, -6.0F, -1.0F, 6.0F, 7.0F, 9.0F, new CubeDeformation(-0.35F)),
                PartPose.offsetAndRotation(0.0F, -6.5F, 2.0F, 0.55F, 0.0F, 0.0F));

        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 20).addBox(-4.0F, 0.0F, -2.5F, 8.0F, 13.0F, 5.0F), PartPose.ZERO);
        body.addOrReplaceChild("mantle", CubeListBuilder.create()
                .texOffs(66, 0).addBox(-6.0F, 0.0F, -4.0F, 12.0F, 5.0F, 8.0F, new CubeDeformation(-0.25F)),
                PartPose.offset(0.0F, -0.5F, 0.0F));
        PartDefinition skirt = body.addOrReplaceChild("skirt", CubeListBuilder.create()
                .texOffs(28, 20).addBox(-5.0F, 0.0F, -3.5F, 10.0F, 7.0F, 7.0F), PartPose.offset(0.0F, 12.5F, 0.0F));
        skirt.addOrReplaceChild("hem", CubeListBuilder.create()
                .texOffs(64, 20).addBox(-6.5F, 0.0F, -4.5F, 13.0F, 5.0F, 9.0F), PartPose.offset(0.0F, 6.5F, 0.0F));
        body.addOrReplaceChild("cloak", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-5.0F, 0.0F, 0.0F, 10.0F, 22.0F, 1.0F), PartPose.offset(0.0F, 0.5F, 3.4F));

        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(24, 40).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(-0.15F))
                .texOffs(42, 40).addBox(-2.5F, 8.0F, -2.5F, 5.0F, 4.0F, 5.0F), PartPose.offset(-5.5F, 1.5F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(64, 40).addBox(-2.0F, -1.5F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(-0.15F))
                .texOffs(82, 40).addBox(-2.5F, 8.0F, -2.5F, 5.0F, 4.0F, 5.0F), PartPose.offset(5.5F, 1.5F, 0.0F));

        PartDefinition staff = rightArm.addOrReplaceChild("staff", CubeListBuilder.create()
                .texOffs(104, 40).addBox(-0.5F, -14.0F, -0.5F, 1.0F, 26.0F, 1.0F), PartPose.offset(0.0F, 9.5F, -2.4F));
        staff.addOrReplaceChild("crystal", CubeListBuilder.create()
                .texOffs(0, 66).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.offset(0.0F, -15.5F, 0.0F));

        root.addOrReplaceChild("orb", CubeListBuilder.create()
                .texOffs(18, 66).addBox(-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F), PartPose.offset(6.5F, 7.0F, -3.0F));

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(MageEntity mage, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        float cast = mage.castLight();
        boolean roused = mage.roused();
        float breath = Mth.sin(age * 0.045F);
        float sway = Mth.sin(age * 0.031F);

        // never quite still: he floats a finger's width and the robe never settles
        root.y = 0.4F * breath;
        root.zRot = 0.012F * sway;

        head.yRot = netHeadYaw * ((float) Math.PI / 180F);
        head.xRot = headPitch * ((float) Math.PI / 180F) + (roused ? -0.06F : 0.10F);
        head.z = 0.3F * breath;

        body.xRot = roused ? -0.10F + cast * 0.14F : 0.05F;
        mantle.xRot = 0.04F * breath;
        skirt.xRot = 0.02F * sway + limbSwingAmount * 0.06F;
        hem.xRot = 0.03F * Mth.cos(age * 0.07F) + Mth.cos(limbSwing * 0.5F) * 0.09F * limbSwingAmount;
        hem.zRot = 0.02F * sway;
        cloak.xRot = 0.10F + limbSwingAmount * 0.35F + 0.04F * breath + cast * 0.30F;

        // idle: the hands are folded in front of him and the staff rests. cast: both come up
        float lift = cast * 1.75F;
        rightArm.xRot = -0.55F - lift + 0.05F * breath;
        rightArm.zRot = 0.22F + cast * 0.55F;
        leftArm.xRot = -0.55F - lift * 0.85F + 0.05F * breath;
        leftArm.zRot = -0.22F - cast * 0.75F;
        if (limbSwingAmount > 0.02F) {
            rightArm.xRot += Mth.cos(limbSwing * 0.5F) * 0.22F * limbSwingAmount;
            leftArm.xRot -= Mth.cos(limbSwing * 0.5F) * 0.22F * limbSwingAmount;
        }

        staff.xRot = -0.18F + cast * 1.30F;                     // it comes level with the world as he casts
        staff.zRot = -0.12F * breath;
        crystal.yRot = age * (0.09F + cast * 0.55F);
        crystal.xRot = age * (0.055F + cast * 0.30F);
        crystal.y = -15.5F + Mth.sin(age * 0.09F) * (0.7F + cast * 1.2F);

        // the rune stone he never puts down, circling the free hand
        float t = age * (0.06F + cast * 0.22F);
        orb.x = 6.5F + Mth.cos(t) * 2.6F;
        orb.z = -3.0F + Mth.sin(t) * 2.6F;
        orb.y = 7.0F + Mth.sin(age * 0.11F) * 1.1F - cast * 3.0F;
        orb.yRot = t * 1.7F;
        orb.xRot = t * 1.1F;
        faces(mage, age, breath, sway);
    }

    private void faces(MageEntity var1, float var2, float var3, float var4) {
        int var5 = Math.max(1, Math.min(4, var1.stage()));
        float var6 = var1.changing();
        float var7 = var1.shrinking();
        float var8 = var5 <= 1 ? 0.0F : (float)(var5 - 1) / 3.0F;
        float var9 = var6 > 0.0F ? Mth.sin(var6 * (float) Math.PI) : 0.0F;
        float var10 = Math.min(1.6F, var8 + var9 * 0.9F);
        float var11 = 1.0F - var7;
        float var12 = var5 == 1 && var6 <= 0.0F ? 0.06F : 0.0F;
        this.body.xRot += var12;
        this.body.y = -var10 * 0.8F * var11;
        this.head.y = -(var10 * 3.6F + Mth.sin(var2 * 0.037F) * var10 * 1.2F) * var11;
        this.head.x = Mth.cos(var2 * 0.019F) * var10 * 0.9F * var11;
        this.head.xRot += var5 == 1 && var6 <= 0.0F ? 0.07F : -var10 * 0.34F;
        this.head.zRot = Mth.sin(var2 * 0.023F) * var10 * 0.16F;
        this.mantle.y = -0.5F - var10 * 1.6F * var11;
        this.mantle.zRot = Mth.sin(var2 * 0.041F) * var10 * 0.2F;
        this.mantle.yRot = var2 * 0.012F * var10;
        this.skirt.y = 12.5F + var10 * 2.4F * var11;
        this.skirt.xRot = this.skirt.xRot + Mth.sin(var2 * 0.028F) * var10 * 0.1F;
        this.hem.y = 6.5F + var10 * 4.0F * var11;
        this.hem.xRot = this.hem.xRot + Mth.cos(var2 * 0.033F) * var10 * 0.16F;
        this.hem.zRot = this.hem.zRot + Mth.sin(var2 * 0.026F) * var10 * 0.14F;
        this.cloak.xRot = this.cloak.xRot + var10 * 0.32F + Mth.sin(var2 * 0.048F) * var10 * 0.18F;
        float var13 = var10 * 3.4F * var11;
        this.rightArm.x = -5.5F * var11 - var13;
        this.leftArm.x = 5.5F * var11 + var13;
        this.rightArm.y = 1.5F - var10 * 1.2F + Mth.sin(var2 * 0.043F) * var10 * 1.6F;
        this.leftArm.y = 1.5F - var10 * 1.2F + Mth.sin(var2 * 0.037F + 2.1F) * var10 * 1.6F;
        this.rightArm.zRot = this.rightArm.zRot + var10 * 0.62F + Mth.sin(var2 * 0.035F) * var10 * 0.22F;
        this.leftArm.zRot = this.leftArm.zRot - (var10 * 0.62F + Mth.sin(var2 * 0.031F + 1.3F) * var10 * 0.22F);
        this.rightArm.xRot -= var10 * 0.3F;
        this.leftArm.xRot -= var10 * 0.24F;
        this.rightArm.yRot = Mth.sin(var2 * 0.024F) * var10 * 0.5F;
        this.leftArm.yRot = -Mth.sin(var2 * 0.021F) * var10 * 0.5F;
        this.staff.z = -2.4F - var10 * 3.0F * var11;
        this.staff.y = 9.5F - var10 * 2.0F * var11;
        this.staff.zRot = this.staff.zRot + var10 * 0.5F + Mth.sin(var2 * 0.052F) * var10 * 0.3F;
        this.staff.yRot = var2 * 0.03F * var10;
        float var14 = 1.0F + var10 * 1.4F;
        this.orb.x = (6.5F + Mth.cos(var2 * (0.06F + var10 * 0.09F)) * 2.6F * var14 + var10 * 2.0F) * var11;
        this.orb.z = (-3.0F + Mth.sin(var2 * (0.06F + var10 * 0.09F)) * 2.6F * var14) * var11;
        this.orb.y = (7.0F - var10 * 6.0F + Mth.sin(var2 * 0.11F) * 1.1F * var14) * var11;
    }
}
