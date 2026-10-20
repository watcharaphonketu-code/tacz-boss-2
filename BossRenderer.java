package com.taczbosses.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.taczbosses.BossDefs;
import com.taczbosses.BossEntity;
import com.taczbosses.TaczBosses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Standard humanoid renderer: armor from ANY mod is drawn by vanilla's HumanoidArmorLayer, and the held item
 * (TACZ gun, hammer, katana, shield...) by the normal item-in-hand layer.
 */
public class BossRenderer extends HumanoidMobRenderer<BossEntity, BossHumanoidModel> {
    public BossRenderer(EntityRendererProvider.Context ctx, int idx) {
        super(ctx, new BossHumanoidModel(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5f * (float) BossDefs.ALL.get(idx).scale());
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<BossEntity>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<BossEntity>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(BossEntity e) {
        return new ResourceLocation(TaczBosses.MODID, "textures/entity/boss_" + (e.idx + 1) + ".png");
    }

    @Override
    protected void scale(BossEntity e, PoseStack ps, float partialTick) {
        float s = 0.9375f * (float) e.scale();               // same base scale vanilla uses for players
        ps.scale(s, s, s);
    }

    @Override
    protected void setupRotations(BossEntity e, PoseStack ps, float age, float yaw, float partialTick) {
        super.setupRotations(e, ps, age, yaw + BossAnims.spinDeg(e, age), partialTick);
        if (e.getMode() == 2) ps.translate(0, -0.55 * e.scale(), 0);        // seated
        else if (e.getMode() == 3) ps.translate(0, -0.30 * e.scale(), 0);   // kneeling
    }
}
