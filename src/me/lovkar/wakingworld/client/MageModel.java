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

/**
 * The mage, built rather than skinned.
 *
 * <p>He could have been a humanoid model in a robe texture like the kingdom's people, and he would
 * have been forgettable. He is the only person in the mod you can choose to fight, so he is worth
 * the geometry: a deep hood with nothing in it but two lights, a mantle across the shoulders, sleeves
 * that widen into cuffs so the hands read as hands from across a room, a robe that falls into a hem
 * wide enough to hide that he has no feet, a cloak that swings, a staff with a crystal that turns on
 * its own above the head of it, and a rune stone that orbits his free hand and never stops.</p>
 *
 * <p>Two things carry the whole performance. The <b>cast</b> value comes down from the server as he
 * winds up a spell: the arms come up, the staff drops level, the crystal spins faster and the cloak
 * lifts - so a player can see a spell coming and get out of the way, which is what makes the fight
 * a fight rather than a damage race. And the <b>hover</b>: he is never quite still, because a thing
 * that is never quite still is alive.</p>
 */
public class MageModel extends HierarchicalModel<MageEntity> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "dark_mage"), "main");

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
    }
}
