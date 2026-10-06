package com.spege.insanetweaks.client.renderer.entity;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Corrupted Sapling: four splayed roots, a three-segment trunk that twists as it rises, two
 * branches tipped with leaf clusters, and a thorned pod on top. About 22.5 px tall at scale 1;
 * {@link RenderCorruptedSapling} scales it to the synced hitbox height.
 *
 * <p>Texture map (64x64, {@code textures/entity/corrupted_sapling.png}): every box uses the
 * standard 1.12 cube layout at the offset given in its constructor call — parts of the same shape
 * share one patch.
 */
@SideOnly(Side.CLIENT)
public class ModelCorruptedSapling extends ModelBase {

    /** Model height in pixels at scale 1, base (y = 24) to the top of the pod. */
    public static final float HEIGHT_PX = 22.5F;

    private final ModelRenderer[] roots = new ModelRenderer[4];
    private final ModelRenderer trunkLower;
    private final ModelRenderer trunkMid;
    private final ModelRenderer trunkUpper;
    private final ModelRenderer branchLeft;
    private final ModelRenderer branchRight;
    private final ModelRenderer pod;

    public ModelCorruptedSapling() {
        this.textureWidth = 64;
        this.textureHeight = 64;

        for (int i = 0; i < 4; i++) {
            ModelRenderer root = new ModelRenderer(this, 36, 0);
            root.addBox(-0.5F, -1.0F, -4.0F, 1, 2, 3);
            root.setRotationPoint(0.0F, 23.0F, 0.0F);
            root.rotateAngleY = (float) (Math.PI / 2.0D * i + 0.4D);
            root.rotateAngleX = 0.35F;
            this.roots[i] = root;
        }

        this.trunkLower = new ModelRenderer(this, 0, 0);
        this.trunkLower.addBox(-2.0F, -6.0F, -2.0F, 4, 6, 4);
        this.trunkLower.setRotationPoint(0.0F, 24.0F, 0.0F);

        this.trunkMid = new ModelRenderer(this, 16, 0);
        this.trunkMid.addBox(-1.5F, -6.0F, -1.5F, 3, 6, 3);
        this.trunkMid.setRotationPoint(0.0F, -5.5F, 0.0F);
        this.trunkMid.rotateAngleY = 0.45F;
        this.trunkMid.rotateAngleZ = 0.12F;
        this.trunkLower.addChild(this.trunkMid);

        this.trunkUpper = new ModelRenderer(this, 28, 0);
        this.trunkUpper.addBox(-1.0F, -5.0F, -1.0F, 2, 5, 2);
        this.trunkUpper.setRotationPoint(0.0F, -5.5F, 0.0F);
        this.trunkUpper.rotateAngleY = 0.45F;
        this.trunkUpper.rotateAngleZ = -0.2F;
        this.trunkMid.addChild(this.trunkUpper);

        this.branchLeft = branch(-1.0F, 0.9F, 0.2F);
        this.branchRight = branch(1.0F, -0.8F, -0.3F);
        this.trunkMid.addChild(this.branchLeft);
        this.trunkMid.addChild(this.branchRight);

        this.pod = new ModelRenderer(this, 16, 16);
        this.pod.addBox(-3.0F, -6.0F, -3.0F, 6, 6, 6);
        this.pod.setRotationPoint(0.0F, -4.5F, 0.0F);
        this.trunkUpper.addChild(this.pod);
        for (int i = 0; i < 4; i++) {
            ModelRenderer thorn = new ModelRenderer(this, 40, 16);
            thorn.addBox(-0.5F, -3.0F, -0.5F, 1, 3, 1);
            thorn.setRotationPoint(i < 2 ? -2.5F : 2.5F, -3.0F, i % 2 == 0 ? -2.5F : 2.5F);
            thorn.rotateAngleZ = i < 2 ? -0.7F : 0.7F;
            thorn.rotateAngleX = i % 2 == 0 ? 0.7F : -0.7F;
            this.pod.addChild(thorn);
        }
    }

    /** A thin limb leaving the mid trunk sideways, with a leaf cluster at its tip. */
    private ModelRenderer branch(float side, float tiltZ, float tiltX) {
        ModelRenderer limb = new ModelRenderer(this, 44, 0);
        limb.addBox(-0.5F, -5.0F, -0.5F, 1, 5, 1);
        limb.setRotationPoint(side, -3.0F, 0.0F);
        limb.rotateAngleZ = tiltZ;
        limb.rotateAngleX = tiltX;

        ModelRenderer leaves = new ModelRenderer(this, 0, 16);
        leaves.addBox(-2.0F, -3.0F, -2.0F, 4, 3, 4);
        leaves.setRotationPoint(0.0F, -4.5F, 0.0F);
        leaves.rotateAngleZ = -tiltZ * 0.6F;
        limb.addChild(leaves);
        return limb;
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scale) {
        this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        for (ModelRenderer root : this.roots) {
            root.render(scale);
        }
        this.trunkLower.render(scale);
    }

    /** Slow sway of the branches and a lazy turn of the pod, so it reads as alive. */
    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, float scale, Entity entity) {
        float sway = (float) Math.sin(ageInTicks * 0.05F) * 0.08F;
        this.branchLeft.rotateAngleZ = 0.9F + sway;
        this.branchRight.rotateAngleZ = -0.8F - sway;
        this.pod.rotateAngleY = ageInTicks * 0.01F;
    }
}
