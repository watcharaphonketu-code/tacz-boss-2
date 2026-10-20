package com.taczbosses;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.levelgen.Heightmap;

/** World-level progress: which boss is next, when it spawns, which one is alive. */
public class BossProgress extends SavedData {
    int next = 0;
    long nextSpawn = -1;
    long lastSeen = 0;
    int loops = 0;
    boolean done = false;
    UUID active = null;

    public static BossProgress get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(BossProgress::load, BossProgress::new, "taczbosses_progress");
    }

    public static BossProgress load(CompoundTag t) {
        BossProgress p = new BossProgress();
        p.next = t.getInt("next");
        p.nextSpawn = t.getLong("nextSpawn");
        p.lastSeen = t.getLong("lastSeen");
        p.loops = t.getInt("loops");
        p.done = t.getBoolean("done");
        if (t.hasUUID("active")) p.active = t.getUUID("active");
        return p;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.putInt("next", next);
        t.putLong("nextSpawn", nextSpawn);
        t.putLong("lastSeen", lastSeen);
        t.putInt("loops", loops);
        t.putBoolean("done", done);
        if (active != null) t.putUUID("active", active);
        return t;
    }

    /** Called once per second from the overworld tick. */
    public static void tick(ServerLevel ow) {
        BossProgress p = get(ow);
        long now = ow.getGameTime();
        if (p.nextSpawn < 0) {
            p.nextSpawn = now + BossConfig.FIRST_DAY.get() * 24000L;
            p.setDirty();
        }
        if (p.done) return;
        if (p.active != null) {
            Entity e = ow.getEntity(p.active);
            if (e instanceof BossEntity b && b.isAlive()) {
                p.lastSeen = now;
            } else if (now - p.lastSeen > 6000) { // boss vanished (killed by command / lost): retry later
                p.active = null;
                p.nextSpawn = now + 24000L;
                p.setDirty();
            }
            return;
        }
        if (now >= p.nextSpawn) p.spawn(ow);
    }

    private void spawn(ServerLevel ow) {
        List<ServerPlayer> ps = ow.players().stream().filter(pl -> pl.isAlive() && !pl.isSpectator()).toList();
        if (ps.isEmpty()) return;
        RandomSource r = ow.random;
        ServerPlayer pl = ps.get(r.nextInt(ps.size()));
        BlockPos spot = null;
        for (int i = 0; i < 12 && spot == null; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 40 + r.nextDouble() * 16;
            int x = (int) (pl.getX() + Math.cos(a) * d), z = (int) (pl.getZ() + Math.sin(a) * d);
            if (!ow.hasChunkAt(new BlockPos(x, 64, z))) continue;
            int y = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (!ow.getFluidState(pos.below()).isEmpty()) continue;
            spot = pos;
        }
        if (spot == null) spot = pl.blockPosition().offset(8, 0, 8);
        BossEntity boss = ModEntities.BOSSES.get(next).get().create(ow);
        if (boss == null) return;
        boss.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, r.nextFloat() * 360f, 0f);
        boss.setLoops(loops);
        if (BossConfig.GLOW_SECONDS.get() > 0)
            boss.addEffect(new MobEffectInstance(MobEffects.GLOWING, BossConfig.GLOW_SECONDS.get() * 20, 0, false, false));
        ow.addFreshEntity(boss);
        active = boss.getUUID();
        lastSeen = ow.getGameTime();
        setDirty();
        ow.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("§c[บอส " + (next + 1) + "/5] ").append(Component.translatable("entity.taczbosses.boss_" + (next + 1)))
                .append(Component.literal(BossConfig.ANNOUNCE.get()
                    ? " §fปรากฏตัวแถวพิกัด X≈" + Math.round(spot.getX() / 50.0) * 50 + " Z≈" + Math.round(spot.getZ() / 50.0) * 50
                    : " §fปรากฏตัวแล้ว!")), false);
        ow.playSound(null, pl.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4f, 1f);
    }

    /** Called by BossEntity.die(). Spawn-egg bosses (not the tracked one) do not advance progress. */
    public static void onDeath(ServerLevel ow, BossEntity b) {
        BossProgress p = get(ow);
        if (p.active == null || !p.active.equals(b.getUUID())) return;
        p.active = null;
        p.next++;
        if (p.next >= 5) {
            if (BossConfig.LOOP.get()) { p.next = 0; p.loops++; }
            else p.done = true;
        }
        p.nextSpawn = ow.getGameTime() + BossConfig.GAP_DAYS.get() * 24000L;
        p.setDirty();
        ow.getServer().getPlayerList().broadcastSystemMessage(
            Component.literal("§a[บอส] ").append(Component.translatable("entity.taczbosses.boss_" + (b.idx + 1)))
                .append(Component.literal(p.done ? " §fถูกกำจัด! ครบทั้ง 5 ตัวแล้ว" : " §fถูกกำจัด! ตัวถัดไปจะมาใน " + BossConfig.GAP_DAYS.get() + " วัน")), false);
    }
}
