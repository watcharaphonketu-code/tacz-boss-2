package com.taczbosses;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TaczBosses.MODID);
    public static final List<RegistryObject<EntityType<BossEntity>>> BOSSES = new ArrayList<>();

    static {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            final BossDef d = BossDefs.ALL.get(i);
            BOSSES.add(ENTITIES.register("boss_" + (i + 1), () ->
                EntityType.Builder.<BossEntity>of((t, l) -> new BossEntity(t, l, idx), MobCategory.MONSTER)
                    .sized((float) (0.6 * d.scale()), (float) (1.8 * d.scale()))
                    .clientTrackingRange(16)
                    .fireImmune()
                    .build("boss_" + (idx + 1))));
        }
    }
}
