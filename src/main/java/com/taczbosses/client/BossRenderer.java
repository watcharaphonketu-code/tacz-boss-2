package com.taczbosses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.taczbosses.BossDefs;
import com.taczbosses.BossEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

public class BossRenderer extends GeoEntityRenderer<BossEntity> {
    public BossRenderer(EntityRendererProvider.Context ctx, int idx) {
        super(ctx, new BossModel());
        double s = BossDefs.ALL.get(idx).scale();
        withScale((float) (0.9 * s));                       // geo model is 2 blocks tall; the player is 1.8
        this.shadowRadius = (float) (0.6 * s);
        addRenderLayer(new ItemArmorGeoLayer<>(this));     // helmet / chest / legs / boots from the other mod
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, BossEntity e) {
                return switch (bone.getName()) {
                    case "weapon_right" -> e.getMainHandItem();
                    case "weapon_left" -> e.getOffhandItem();
                    default -> null;
                };
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, BossEntity e) {
                return bone.getName().equals("weapon_left") ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, BossEntity e,
                                              MultiBufferSource buffer, float partialTick, int light, int overlay) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
                if (stack.getItem() instanceof ShieldItem) {
                    poseStack.translate(0, 0.125, bone.getName().equals("weapon_left") ? 0.25 : -0.25);
                    if (bone.getName().equals("weapon_left")) poseStack.mulPose(Axis.YP.rotationDegrees(180f));
                }
                super.renderStackForBone(poseStack, bone, stack, e, buffer, partialTick, light, overlay);
            }
        });
    }
}
