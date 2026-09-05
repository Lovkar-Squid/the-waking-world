package me.lovkar.wakingworld.client;

import me.lovkar.wakingworld.WakingWorld;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * What the kingdom's people wear over their skins, in three dimensions: cloaks, hats, hoods,
 * quivers and satchels, the king's crown and mantle. A humanoid model whose own parts are empty -
 * only the garb has cubes, hung on the head and the body so it moves with them - drawn by
 * {@link GarbLayer} over the skin, the way armour is. One model and one 64x64 texture per kind
 * ({@code textures/entity/kingdom/garb/<kind>.png}, painted by tools/textures/garb.py; the cube
 * texture offsets here and the boxes there must agree).
 */
public class GarbModel<T extends Mob> extends HumanoidModel<T> {
    public enum Kind {
        ARCHER("archer"), KNIGHT("knight"), SPEARMAN("spearman"),
        SURVEYOR("surveyor"), RELIC_MONGER("relic_monger"), SMITH("smith"), PROVISIONER("provisioner"), CHANDLER("chandler"), SCRIBE("scribe"),
        KING("king");

        public final String name;
        public final ModelLayerLocation layer;
        public final ResourceLocation texture;

        Kind(String name) {
            this.name = name;
            this.layer = new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "garb_" + name), "main");
            this.texture = ResourceLocation.fromNamespaceAndPath(WakingWorld.MODID, "textures/entity/kingdom/garb/" + name + ".png");
        }
    }

    public GarbModel(ModelPart root) {
        super(root);
    }

    /** The humanoid skeleton with nothing on it but this kind's garb. */
    public static LayerDefinition createLayer(Kind kind) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
        switch (kind) {
            case ARCHER -> {
                cloak(body, 14);
                // the quiver on the back, over the right shoulder
                body.addOrReplaceChild("quiver", CubeListBuilder.create().texOffs(30, 0).addBox(-4.5F, -1.0F, 2.2F, 3.0F, 8.0F, 2.0F), PartPose.rotation(0.0F, 0.0F, -0.25F));
            }
            case KNIGHT -> {
                cloak(body, 14);
                plume(head);
            }
            case SPEARMAN -> {
                cloak(body, 12);
                plume(head);
            }
            case SURVEYOR -> {
                // a wide-brimmed hat and a satchel on the hip
                head.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 32).addBox(-6.0F, -9.0F, -6.0F, 12.0F, 1.0F, 12.0F), PartPose.rotation(-0.06F, 0.0F, 0.04F));
                head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 46).addBox(-3.5F, -13.0F, -3.5F, 7.0F, 4.0F, 7.0F), PartPose.rotation(-0.06F, 0.0F, 0.04F));
                body.addOrReplaceChild("satchel", CubeListBuilder.create().texOffs(30, 0).addBox(1.0F, 7.5F, 2.2F, 4.0F, 4.0F, 2.0F), PartPose.ZERO);
                body.addOrReplaceChild("strap", CubeListBuilder.create().texOffs(44, 0).addBox(-4.4F, 0.0F, -2.4F, 1.0F, 8.0F, 0.4F), PartPose.rotation(0.0F, 0.0F, -0.55F));
            }
            case RELIC_MONGER -> {
                cloak(body, 16);
                // a deep hood over the skin's own
                head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 32).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.9F)), PartPose.ZERO);
            }
            case SMITH -> {
                // rolled leather apron bib, thick at the chest
                body.addOrReplaceChild("bib", CubeListBuilder.create().texOffs(30, 0).addBox(-3.5F, 1.0F, -2.6F, 7.0F, 6.0F, 1.0F), PartPose.ZERO);
                body.addOrReplaceChild("tools", CubeListBuilder.create().texOffs(46, 0).addBox(2.5F, 8.0F, -2.8F, 2.0F, 4.0F, 1.0F), PartPose.ZERO);
            }
            case PROVISIONER -> {
                // a soft cap and a shoulder sack
                head.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 32).addBox(-4.0F, -10.5F, -4.0F, 8.0F, 3.0F, 8.0F, new CubeDeformation(0.6F)), PartPose.rotation(0.0F, 0.0F, 0.08F));
                body.addOrReplaceChild("sack", CubeListBuilder.create().texOffs(30, 0).addBox(-5.0F, 0.5F, 2.2F, 5.0F, 6.0F, 3.0F), PartPose.rotation(0.0F, 0.0F, 0.2F));
            }
            case CHANDLER -> {
                head.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 32).addBox(-4.0F, -10.0F, -4.0F, 8.0F, 2.0F, 8.0F, new CubeDeformation(0.5F)), PartPose.rotation(0.1F, 0.0F, -0.1F));
                // a bandolier of candles across the chest
                body.addOrReplaceChild("candles", CubeListBuilder.create().texOffs(30, 0).addBox(-4.5F, 2.0F, -2.6F, 9.0F, 2.0F, 1.0F), PartPose.rotation(0.0F, 0.0F, 0.35F));
            }
            case SCRIBE -> {
                head.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(0, 32).addBox(-5.0F, -9.5F, -5.0F, 10.0F, 2.0F, 10.0F), PartPose.rotation(0.0F, 0.0F, 0.12F));
                body.addOrReplaceChild("scroll", CubeListBuilder.create().texOffs(30, 0).addBox(-1.5F, 0.5F, 2.2F, 3.0F, 9.0F, 3.0F), PartPose.rotation(0.0F, 0.0F, 0.3F));
            }
            case KING -> {
                cloak(body, 20);
                // the mantle over the shoulders, and the crown with its points
                body.addOrReplaceChild("mantle", CubeListBuilder.create().texOffs(0, 22).addBox(-6.0F, -1.0F, -3.0F, 12.0F, 3.0F, 6.0F), PartPose.ZERO);
                head.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 32).addBox(-4.5F, -10.0F, -4.5F, 9.0F, 2.0F, 9.0F), PartPose.ZERO);
                float[][] points = {{-4.5F, -4.5F}, {3.5F, -4.5F}, {-4.5F, 3.5F}, {3.5F, 3.5F}, {-0.5F, -4.5F}};
                for (int i = 0; i < points.length; i++) {
                    head.addOrReplaceChild("point" + i, CubeListBuilder.create().texOffs(40 + i * 4, 32).addBox(points[i][0], -12.0F, points[i][1], 1.0F, 2.0F, 1.0F), PartPose.ZERO);
                }
            }
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** A cloak hanging from the shoulders down the back, {@code length} deep. */
    private static void cloak(PartDefinition body, int length) {
        body.addOrReplaceChild("cloak", CubeListBuilder.create().texOffs(0, 0).addBox(-5.0F, 0.0F, 2.0F, 10.0F, (float) length, 1.0F), PartPose.rotation(0.08F, 0.0F, 0.0F));
    }

    /** A plume standing up from the helmet. */
    private static void plume(PartDefinition head) {
        head.addOrReplaceChild("plume", CubeListBuilder.create().texOffs(30, 0).addBox(-0.5F, -13.0F, -3.0F, 1.0F, 5.0F, 5.0F), PartPose.rotation(-0.15F, 0.0F, 0.0F));
    }
}
