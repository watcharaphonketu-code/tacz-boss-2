package com.taczbosses;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class BossEvents {
    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl)) return;
        if (sl.dimension() != Level.OVERWORLD || sl.getGameTime() % 20 != 0) return;
        BossProgress.tick(sl);
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof Level lv) || lv.isClientSide()) return;
        if (!BossEntity.isLocked(lv, e.getPos())) return;
        if (BossConfig.OPS_EXEMPT.get() && e.getPlayer().hasPermissions(2)) return;
        e.setCanceled(true);
        e.getPlayer().displayClientMessage(Component.literal("§cพื้นที่นี้ถูกล็อกจนกว่าบอสจะตาย"), true);
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Player p) || p.level().isClientSide) return;
        for (BossEntity b : BossEntity.ACTIVE) {
            if (b.level() == p.level() && b.distanceToSqr(p) < 128.0 * 128.0) b.playerDied();
        }
    }

    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate e) {
        Level lv = e.getLevel();
        e.getAffectedBlocks().removeIf(p -> BossEntity.isLocked(lv, p));
    }
}
