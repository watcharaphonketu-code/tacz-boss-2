package com.taczbosses;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TaczBosses.MODID);
    public static final List<RegistryObject<Item>> EGGS = new ArrayList<>();
    private static final int[][] COLORS = {
        {0x54623A, 0xE24B3A}, {0x968250, 0xE24B3A}, {0x46484C, 0xE24B3A}, {0x6E3C32, 0xE24B3A}, {0x28325A, 0xE24B3A}
    };

    static {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            EGGS.add(ITEMS.register("boss_" + (i + 1) + "_spawn_egg", () ->
                new ForgeSpawnEggItem(() -> ModEntities.BOSSES.get(idx).get(), COLORS[idx][0], COLORS[idx][1], new Item.Properties())));
        }
    }
}
