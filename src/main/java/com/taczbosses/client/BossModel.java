package com.taczbosses.client;

import com.taczbosses.BossEntity;
import com.taczbosses.TaczBosses;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BossModel extends GeoModel<BossEntity> {
    @Override public ResourceLocation getModelResource(BossEntity e) { return new ResourceLocation(TaczBosses.MODID, "geo/entity/boss.geo.json"); }
    @Override public ResourceLocation getTextureResource(BossEntity e) { return new ResourceLocation(TaczBosses.MODID, "textures/entity/boss_" + (e.idx + 1) + ".png"); }
    @Override public ResourceLocation getAnimationResource(BossEntity e) { return new ResourceLocation(TaczBosses.MODID, "animations/entity/boss.animation.json"); }
}
