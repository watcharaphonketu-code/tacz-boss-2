package com.taczbosses;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TaczBosses.MODID)
public class TaczBosses {
    public static final String MODID = "taczbosses";

    public TaczBosses() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITIES.register(bus);
        ModItems.ITEMS.register(bus);
        bus.addListener(this::attributes);
        bus.addListener(this::tabs);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BossConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(new BossEvents());
    }

    private void attributes(EntityAttributeCreationEvent e) {
        for (int i = 0; i < 5; i++) {
            e.put(ModEntities.BOSSES.get(i).get(), BossEntity.attributes(i).build());
        }
    }

    private void tabs(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            ModItems.EGGS.forEach(r -> e.accept(r.get()));
        }
    }
}
