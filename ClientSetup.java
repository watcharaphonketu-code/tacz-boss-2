package com.taczbosses.client;

import com.taczbosses.ModEntities;
import com.taczbosses.TaczBosses;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaczBosses.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            e.registerEntityRenderer(ModEntities.BOSSES.get(i).get(), ctx -> new BossRenderer(ctx, idx));
        }
    }
}
