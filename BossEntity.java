package com.taczbosses;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.taczbosses.BossDef.MoveDef;
import com.taczbosses.BossDef.MoveType;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;


/**
 * One class drives all five bosses; behaviour comes from BossDefs.
 * Server side: a small state machine (pick a move -> run it -> cooldown). Client side: GeckoLib animation only.
 */
public class BossEntity extends Monster {
    /** Bosses currently loaded on the server; used for the block-lock zone. */
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Set<BossEntity> ACTIVE = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final EntityDataAccessor<Integer> MODE =
            SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.INT);   // 0 melee, 1 gun, 2 sit (minigun), 3 kneel
    private static final EntityDataAccessor<Boolean> BACK =
            SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> FLAGS =      // 1 aiming, 2 rushing, 4 shield up
            SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM_ID =
            SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM_T =
            SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM_W =
            SynchedEntityData.defineId(BossEntity.class, EntityDataSerializers.INT);

    public final int idx;
    public final BossDef def;
    private final ServerBossEvent bar;
    private final int[] cd;

    public BlockPos spawnPos = BlockPos.ZERO;
    private boolean hasSpawn, equipped, lockExpired;
    private int noPlayerTicks, phase = 1, ammo, reloadLeft, swapLock, lostSight, invul, loops;
    private float speedMult = 1f;
    private ItemStack gunStack = ItemStack.EMPTY, gun2Stack = ItemStack.EMPTY, meleeStack = ItemStack.EMPTY;

    // targeting
    private UUID targetId, pendingAttacker;
    private Vec3 lastTargetPos = Vec3.ZERO, targetVel = Vec3.ZERO;
    private final ArrayDeque<Vec3> hist = new ArrayDeque<>();

    // current move
    private MoveDef cur;
    private int curIdx, mt, sub, subT, retreatLeft, strafeLeft, strafeSign = 1, runSign = 1, rounds, grabT;
    private Vec3 dashDir = Vec3.ZERO, leapV = Vec3.ZERO;
    private double dashDist;
    private boolean dashHit, rushing, shielding, sitting;
    private UUID grabbed;
    private final List<double[]> blasts = new ArrayList<>(); // x, y, z, ticksLeft, dmg, radius

    // adaptive AI
    private final int[] miss;                  // how many times in a row each move failed to hit (the boss uses it less)
    private boolean moveHit, coverTried;
    private double habit;                      // running average of the target's sideways speed (blocks/tick)
    private double adj;                        // difficulty director, -0.3 .. +0.3
    private float hpMark = 1f;
    private int fightTicks, cheeseTicks, cheeseCd;
    private Vec3 coverPos;
    private UUID launchTarget;
    private final List<UUID> minions = new ArrayList<>();

    public BossEntity(EntityType<? extends BossEntity> type, Level level, int idx) {
        super(type, level);
        this.idx = idx;
        this.def = BossDefs.ALL.get(idx);
        this.cd = new int[def.moves().size()];
        this.miss = new int[def.moves().size()];
        this.ammo = def.ammo();
        this.xpReward = 500;
        this.bar = new ServerBossEvent(Component.translatable("entity.taczbosses.boss_" + (idx + 1)),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes(int i) {
        BossDef d = BossDefs.ALL.get(i);
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 1000)
            .add(Attributes.MOVEMENT_SPEED, d.walkBps() / 43.17)
            .add(Attributes.FOLLOW_RANGE, 128)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ATTACK_DAMAGE, 5)
            .add(Attributes.ARMOR, 0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(MODE, 1);
        this.entityData.define(BACK, false);
        this.entityData.define(FLAGS, 0);
        this.entityData.define(ANIM_ID, -1);
        this.entityData.define(ANIM_T, 0);
        this.entityData.define(ANIM_W, 0);
    }

    public double scale() { return def.scale(); }
    public void setLoops(int n) { loops = n; }
    private float dmgDivisor() { return Math.max(1f, (float) (def.hp() * BossConfig.HP_MULT.get() * (1 + 0.5 * loops) / 1000.0)); }
    private float dmgMult() { return (float) (BossConfig.DMG_MULT.get() * (1 + 0.25 * loops)); }
    private void setMode(int m) { if (entityData.get(MODE) != m) entityData.set(MODE, m); }
    private void setBack(boolean b) { if (entityData.get(BACK) != b) entityData.set(BACK, b); }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (!level().isClientSide) {
            if (!hasSpawn) { spawnPos = blockPosition(); hasSpawn = true; }
            ACTIVE.add(this);
        }
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        ACTIVE.remove(this);
        bar.removeAllPlayers();
    }

    @Override public void startSeenByPlayer(ServerPlayer p) { super.startSeenByPlayer(p); bar.addPlayer(p); }
    @Override public void stopSeenByPlayer(ServerPlayer p) { super.stopSeenByPlayer(p); bar.removePlayer(p); }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean startRiding(Entity e, boolean force) { return false; }       // never boards boats / minecarts
    @Override public boolean isPushedByFluid() { return false; }                          // water flow cannot push the boss
    @Override public void makeStuckInBlock(BlockState s, Vec3 v) { }                      // cobwebs do not slow the boss
    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.IRON_GOLEM_DEATH; }

    @Override
    public void die(DamageSource src) {
        super.die(src);
        if (level() instanceof ServerLevel sl) {
            for (UUID id : minions) { Entity e = sl.getEntity(id); if (e != null) e.discard(); }
            BossProgress.onDeath(sl.getServer().overworld(), this);
        }
    }

    public static boolean isLocked(Level lv, BlockPos pos) {
        int r = BossConfig.LOCK_RADIUS.get();
        if (r <= 0) return false;
        for (BossEntity b : ACTIVE) {
            if (b.level() == lv && b.isAlive() && !b.lockExpired && b.spawnPos.distSqr(pos) <= (double) r * r) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ equipment

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return it == null ? Items.AIR : it;
    }

    /** TACZ guns are one item (tacz:modern_kinetic_gun) with the gun id in NBT; other ids are plain items. */
    private static ItemStack gunStack(String id) {
        if (id.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation rl = new ResourceLocation(id);
        if (ForgeRegistries.ITEMS.containsKey(rl)) return new ItemStack(item(id));
        Item gun = item("tacz:modern_kinetic_gun");
        if (gun == Items.AIR) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(gun);
        CompoundTag t = s.getOrCreateTag();
        t.putString("GunId", id);
        t.putString("GunFireMode", "AUTO");
        t.putInt("GunCurrentAmmoCount", 30);
        return s;
    }

    private static ItemStack stackOf(String id) {
        if (id.isEmpty()) return ItemStack.EMPTY;
        Item it = item(id);
        return it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
    }

    /** Given the helmet id, find the matching chest/legs/boots of the same set. */
    private static ItemStack armorPiece(String helmetId, EquipmentSlot slot) {
        ResourceLocation rl = new ResourceLocation(helmetId);
        if (slot == EquipmentSlot.HEAD) return stackOf(helmetId);
        String base = rl.getPath().replaceAll("_?helmet$", "");
        String[] names = switch (slot) {
            case CHEST -> new String[] {"chestplate", "chest", "tunic", "body", "jacket"};
            case LEGS -> new String[] {"leggings", "legs", "pants", "trousers"};
            default -> new String[] {"boots", "shoes", "feet"};
        };
        for (String n : names) {
            Item it = item(rl.getNamespace() + ":" + base + "_" + n);
            if (it != Items.AIR) return new ItemStack(it);
        }
        if (item(helmetId) instanceof ArmorItem h0) {                   // same armor material = same set
            for (var e : ForgeRegistries.ITEMS.getEntries()) {
                if (e.getValue() instanceof ArmorItem a && a.getEquipmentSlot() == slot
                        && a.getMaterial().getName().equals(h0.getMaterial().getName())) return new ItemStack(a);
            }
        }
        for (var e : ForgeRegistries.ITEMS.getEntries()) {
            ResourceLocation k = e.getKey().location();
            if (k.getNamespace().equals(rl.getNamespace()) && k.getPath().startsWith(base)
                    && e.getValue() instanceof ArmorItem a && a.getEquipmentSlot() == slot) return new ItemStack(a);
        }
        log("armor piece not found for " + helmetId + " slot " + slot);
        return ItemStack.EMPTY;
    }

    private static void log(String s) { LOGGER.warn("[taczbosses] {}", s); }

    private void equip() {
        equipped = true;
        gunStack = gunStack(def.gun());
        gun2Stack = gunStack(def.gun2());
        meleeStack = stackOf(def.melee());
        for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
            { ItemStack ap = armorPiece(def.helmet(), s); setItemSlot(s, ap); LOGGER.info("[taczbosses] boss {} {} = {}", idx + 1, s.getName(), ap); }
        if (!def.offhand().isEmpty()) setItemSlot(EquipmentSlot.OFFHAND, stackOf(def.offhand()));
        holdGun();
        for (EquipmentSlot s : EquipmentSlot.values()) setDropChance(s, 2.0f);   // >1 = always drops what it holds/wears
    }

    private void holdGun() {
        setItemSlot(EquipmentSlot.MAINHAND, gunStack.isEmpty() ? meleeStack.copy() : gunStack.copy());
        setMode(1);
    }

    private void holdGun2() {
        setItemSlot(EquipmentSlot.MAINHAND, gun2Stack.isEmpty() ? gunStack.copy() : gun2Stack.copy());
        setMode(1);
    }

    private void holdMelee() {
        setItemSlot(EquipmentSlot.MAINHAND, meleeStack.isEmpty() ? gunStack.copy() : meleeStack.copy());
        setMode(0);
    }

    // ------------------------------------------------------------------ damage rules

    @Override
    public boolean hurt(DamageSource src, float amt) {
        if (level().isClientSide || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(src, amt);
        if (invul > 0) return false;
        Entity att = src.getEntity();
        float a = amt;
        if (rushing) a *= 0.1f;                                      // shotgun rush / dash: 90% damage reduction
        if (shielding && att != null && frontal(att)) a *= 0.2f;     // shield held up
        if (sitting) a *= 1.5f;                                      // minigun pose: exposed
        a /= dmgDivisor();                                           // lets 1000 max HP stand in for the real total
        if (att instanceof Player p && !p.isSpectator()) pendingAttacker = p.getUUID();
        this.invulnerableTime = 0;                                   // every bullet counts, not one per 10 ticks
        return super.hurt(src, a);
    }

    private boolean frontal(Entity att) {
        Vec3 look = Vec3.directionFromRotation(0, getYRot());
        Vec3 to = att.position().subtract(position()).multiply(1, 0, 1);
        return to.lengthSqr() > 1e-6 && look.dot(to.normalize()) > 0.35;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("Phase", phase);
        t.putInt("Ammo", ammo);
        t.putInt("Loops", loops);
        t.putLong("SpawnPos", spawnPos.asLong());
        t.putBoolean("HasSpawn", hasSpawn);
        t.putBoolean("LockExpired", lockExpired);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        phase = Math.max(1, t.getInt("Phase"));
        if (t.contains("Ammo")) ammo = t.getInt("Ammo");
        loops = t.getInt("Loops");
        hasSpawn = t.getBoolean("HasSpawn");
        if (hasSpawn) spawnPos = BlockPos.of(t.getLong("SpawnPos"));
        lockExpired = t.getBoolean("LockExpired");
        speedMult = phase == 2 ? 1.1f : phase >= 3 ? 1.4f : 1f;
    }

    // ------------------------------------------------------------------ server AI

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bar.setProgress(Mth.clamp(getHealth() / getMaxHealth(), 0f, 1f));
        if (!equipped) equip();
        for (int i = 0; i < cd.length; i++) if (cd[i] > 0) { cd[i]--; if (adj > 0 && cd[i] > 0 && random.nextFloat() < adj) cd[i]--; }
        if (swapLock > 0) swapLock--;
        tickLock();
        tickBlasts();
        boolean aim = false;
        if (cur != null) {
            switch (cur.type()) {
                case SHOOT, SNIPE, MINIGUN -> aim = true;
                case SHOTGUN_RUSH -> aim = sub >= 2;
                case LAUNCH_SHOT -> aim = sub == 1;
                default -> { }
            }
        }
        int fl = (aim ? 1 : 0) | (rushing ? 2 : 0) | (shielding ? 4 : 0);
        if (entityData.get(FLAGS) != fl) entityData.set(FLAGS, fl);
        if (invul > 0) { invul--; stopMove(); return; }

        Player tg = resolveTarget();
        if (tg == null) {
            cur = null; sitting = false; shielding = false; rushing = false; setBack(false);
            if (entityData.get(MODE) >= 2) setMode(1);
            stopMove();
            return;
        }
        trackTarget(tg);
        checkPhase();
        directorTick();
        antiCheese(tg);

        if (reloadLeft > 0) { doReload(tg); return; }
        if (cur == null) pickMove(tg); else runMove(tg);
    }

    private void tickLock() {
        if (tickCount % 20 != 0) return;
        if (level().getNearestPlayer(this, BossConfig.LOCK_RADIUS.get()) == null) noPlayerTicks += 20; else noPlayerTicks = 0;
        if (noPlayerTicks > BossConfig.LOCK_TIMEOUT_MIN.get() * 1200) lockExpired = true;
    }

    private void checkPhase() {
        float f = getHealth() / getMaxHealth();
        if (phase < 2 && f <= def.p2()) {
            phase = 2;
            speedMult = 1.1f;
            heal((float) (getMaxHealth() * BossConfig.PHASE2_HEAL.get()));
            roar();
        } else if (phase < 3 && def.p3() > 0 && f <= def.p3()) {
            phase = 3;
            speedMult = 1.4f;
            roar();
        }
    }

    private void roar() {
        invul = 40;
        cur = null; sub = 0; sitting = false; shielding = false; rushing = false;
        triggerAnim("action", "dash_warn");
        playSound(SoundEvents.ENDER_DRAGON_GROWL, 3f, 0.8f);
        parts(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        if (hasGun()) holdGun(); else holdMelee();
    }

    private boolean hasGun() {
        return !(idx == 4 && phase >= 3) && !gunStack.isEmpty();
    }

    // ---- targeting

    private boolean validPlayer(Player p) { return p.isAlive() && !p.isSpectator(); }

    private Player resolveTarget() {
        Player t = targetId == null ? null : level().getPlayerByUUID(targetId);
        if (t != null && (!validPlayer(t) || distanceTo(t) > BossConfig.SIGHT.get())) t = null;
        if (t != null) {
            if (hasLineOfSight(t)) lostSight = 0; else lostSight++;
            if (lostSight > 60) t = null;                              // hid behind blocks / left sight: drop the lock
        }
        boolean committed = cur != null && switch (cur.type()) {
            case SHOOT, SNIPE, RUNAROUND, CHASE, MINIGUN -> false;      // these may switch target when someone else shoots
            default -> true;                                            // every real attack keeps its target until it ends
        };
        if (!committed && pendingAttacker != null && swapLock <= 0) {
            Player a = level().getPlayerByUUID(pendingAttacker);
            if (a != null && a != t && validPlayer(a) && distanceTo(a) <= BossConfig.SIGHT.get() && hasLineOfSight(a)) {
                t = a;
                swapLock = 100;                                         // ignore other shooters for ~5 s
                lostSight = 0;
            }
        }
        pendingAttacker = null;
        if (t == null) {
            if (cur != null && committed) endMove();
            t = pickNew();
            lostSight = 0;
        }
        targetId = t == null ? null : t.getUUID();
        return t;
    }

    private Player pickNew() {
        List<Player> ps = level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(BossConfig.SIGHT.get()),
                p -> validPlayer(p) && hasLineOfSight(p));
        if (ps.isEmpty()) return null;
        Player best = null;
        boolean weakest = "HARD".equalsIgnoreCase(BossConfig.AI_LEVEL.get()) && random.nextBoolean();
        for (Player p : ps) {
            if (best == null) { best = p; continue; }
            if (weakest ? p.getHealth() < best.getHealth() : distanceToSqr(p) < distanceToSqr(best)) best = p;
        }
        return best;
    }

    private void trackTarget(Player t) {
        Vec3 p = t.position();
        if (!lastTargetPos.equals(Vec3.ZERO)) targetVel = targetVel.scale(0.6).add(p.subtract(lastTargetPos).scale(0.4));
        lastTargetPos = p;
        Vec3 to = dirTo(t);
        habit = habit * 0.97 + targetVel.dot(new Vec3(-to.z, 0, to.x)) * 0.03;   // which way does this player like to dodge?
        hist.addLast(p);
        int n = switch (BossConfig.AI_LEVEL.get().toUpperCase()) { case "EASY" -> 14; case "NORMAL" -> 8; default -> 3; };
        while (hist.size() > n) hist.removeFirst();
    }

    // ---- movement helpers

    private Vec3 dirTo(Entity e) {
        Vec3 d = new Vec3(e.getX() - getX(), 0, e.getZ() - getZ());
        return d.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : d.normalize();
    }

    private void moveDir(Vec3 dir, double bps) {
        Vec3 m = getDeltaMovement();
        setDeltaMovement(dir.x * bps / 20.0, m.y, dir.z * bps / 20.0);
        if (horizontalCollision && onGround()) jumpFromGround();
    }

    private void stopMove() {
        Vec3 m = getDeltaMovement();
        setDeltaMovement(0, m.y, 0);
        getNavigation().stop();
    }

    private void faceYaw(float yaw) { setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw; }

    private float yawTo(Entity e) { return (float) (Mth.atan2(e.getZ() - getZ(), e.getX() - getX()) * Mth.RAD_TO_DEG) - 90f; }

    private void faceTarget(Entity e) { faceYaw(yawTo(e)); }

    private void faceLimited(Entity e, float maxDeg) {
        float d = Mth.wrapDegrees(yawTo(e) - getYRot());
        faceYaw(getYRot() + Mth.clamp(d, -maxDeg, maxDeg));
    }

    private void parts(ParticleOptions p, double x, double y, double z, int n, double dx, double dy, double dz, double sp) {
        if (level() instanceof ServerLevel sl) sl.sendParticles(p, x, y, z, n, dx, dy, dz, sp);
    }

    private void line(Vec3 a, Vec3 b, ParticleOptions p) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.min(80, d.length() / 1.5);
        for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale(n == 0 ? 0 : (double) i / n));
            parts(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    private void hurtPlayer(Player p, float dmg) {
        moveHit = true;
        p.hurt(damageSources().mobAttack(this), dmg * dmgMult() * (float) (1 + adj));
    }

    private void knock(Player p, Vec3 dir, double h, double v) {
        p.setDeltaMovement(p.getDeltaMovement().add(dir.x * h, v, dir.z * h));
        p.hurtMarked = true;
    }

    // ---- move selection

    private void pickMove(Player t) {
        double d = distanceTo(t);
        List<Integer> c = new ArrayList<>();
        int total = 0;
        List<MoveDef> ms = def.moves();
        for (int i = 0; i < ms.size(); i++) {
            MoveDef m = ms.get(i);
            if (phase < m.minPhase() || phase > m.maxPhase() || cd[i] > 0 || d < m.minR() || d > m.maxR()) continue;
            c.add(i);
            total += wt(i);
        }
        if (c.isEmpty()) {
            for (int i = 0; i < ms.size(); i++) {                       // fallback: a shooting move if one is allowed, else walk toward the target
                MoveDef m = ms.get(i);
                if (m.type() == MoveType.SHOOT && phase >= m.minPhase() && phase <= m.maxPhase()) { startMove(i); return; }
            }
            getNavigation().moveTo(t, speedMult);
            faceLimited(t, 20f);
            setBack(false);
            return;
        }
        int roll = random.nextInt(total);
        for (int i : c) {
            roll -= wt(i);
            if (roll < 0) { startMove(i); return; }
        }
    }

    private int wt(int i) { return Math.max(1, def.moves().get(i).weight() * 2 / (2 + miss[i])); }

    private void startMove(int i) {
        moveHit = false;
        curIdx = i; cur = def.moves().get(i);
        mt = 0; sub = 0; subT = 0; retreatLeft = 0; strafeLeft = 0; rounds = 0;
        switch (cur.type()) {
            case SHOOT, SNIPE, RUNAROUND, SHOTGUN_RUSH, GRENADE, CHASE -> { if (hasGun()) holdGun(); else holdMelee(); }
            case MINIGUN -> holdGun2();
            default -> holdMelee();
        }
    }

    private void endMove() {
        if (cur != null) {
            cd[curIdx] = cur.cooldown();
            switch (cur.type()) {
                case RUNAROUND, CHASE, SUMMON -> { }
                default -> miss[curIdx] = moveHit ? 0 : Math.min(5, miss[curIdx] + 1);
            }
        }
        cur = null; mt = 0; sub = 0; subT = 0;
        rushing = false; shielding = false; sitting = false;
        setBack(false);
        if (hasGun()) holdGun(); else holdMelee();
    }

    private void runMove(Player t) {
        switch (cur.type()) {
            case SHOOT -> runShoot(t);
            case SNIPE -> runSnipe(t);
            case RUNAROUND -> runRunaround(t);
            case CHASE -> { getNavigation().moveTo(t, cur.speed() * speedMult); faceLimited(t, 20f); if (++mt >= cur.total()) endMove(); }
            case MELEE -> runMelee(t);
            case SLAM -> runSlam(t);
            case DASH -> runDash(t);
            case SHOTGUN_RUSH -> runRush(t);
            case MINIGUN -> runMinigun(t);
            case SHIELD_GRAB -> runShieldGrab(t);
            case GRENADE -> runGrenade(t);
            case LEAP_SLAM -> runLeap(t);
            case LAUNCH_SHOT -> runLaunch(t);
            case SUMMON -> runSummon(t);
        }
    }

    // ------------------------------------------------------------------ gun behaviour

    private void runShoot(Player t) {
        faceTarget(t);
        Vec3 to = dirTo(t);
        double d = distanceTo(t);
        double walk = def.walkBps() * speedMult;
        if (retreatLeft > 0) {                                          // walk BACKWARDS while still facing the player and shooting
            moveDir(to.scale(-1), walk * 0.7);
            setBack(true);
            retreatLeft--;
        } else if (strafeLeft > 0) {                                    // side-step left / right while shooting
            moveDir(new Vec3(-to.z, 0, to.x).scale(strafeSign), walk * 0.7);
            setBack(false);
            strafeLeft--;
        } else {
            setBack(false);
            if (d < def.retreatRange() && random.nextFloat() < 0.03f) retreatLeft = 20 + random.nextInt(40);
            else if (random.nextFloat() < def.strafeChance()) { strafeLeft = 15 + random.nextInt(25); strafeSign = random.nextBoolean() ? 1 : -1; }
            else if (d > def.gunRange() * 0.75) moveDir(to, walk);
            else stopMove();
        }
        if (mt >= 6 && mt % def.shotInterval() == 0) {
            shootOnce(t);
            if (ammo <= 0) { startReload(); endMove(); return; }
        }
        if (++mt >= cur.total()) endMove();
    }

    private void shootOnce(Player t) {
        ammo--;
        if (def.pellets() > 1) {
            int hits = 0;
            for (int i = 0; i < def.pellets(); i++) if (fireBullet(t, def.spread(), def.gunRange(), i == 0) != null) hits++;
            if (hits > 0) hurtPlayer(t, hits * def.bulletDmg());
        } else {
            Player h = fireBullet(t, def.spread(), def.gunRange(), true);
            if (h != null) hurtPlayer(h, def.bulletDmg());
        }
        if (def.shotInterval() >= 6 || mt % 12 == 0) triggerAnim("action", "shoot");
        playSound(SoundEvents.DISPENSER_LAUNCH, 2f, 1.4f);
    }

    private Vec3 aimPoint(Player t) {
        Vec3 p = hist.peekFirst() != null ? hist.peekFirst() : t.position();     // aims at where the player WAS a few ticks ago
        Vec3 to = dirTo(t);
        Vec3 lead = "EASY".equalsIgnoreCase(BossConfig.AI_LEVEL.get()) ? Vec3.ZERO
                : new Vec3(-to.z, 0, to.x).scale(habit * 6.0);                   // ...plus a nudge toward the side they usually dodge to
        return p.add(lead).add(0, t.getBbHeight() * 0.62, 0);
    }

    /** Hitscan. Returns the player hit (or null). */
    private Player fireBullet(Player t, double spreadDeg, double range, boolean tracer) {
        Vec3 muzzle = position().add(0, getBbHeight() * 0.72, 0).add(dirTo(t).scale(0.6 * scale()));
        Vec3 dir = aimPoint(t).subtract(muzzle).normalize();
        double sp = Math.tan(Math.toRadians(spreadDeg));
        dir = dir.add(random.nextGaussian() * sp, random.nextGaussian() * sp, random.nextGaussian() * sp).normalize();
        Vec3 end = muzzle.add(dir.scale(range));
        BlockHitResult bhr = level().clip(new ClipContext(muzzle, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 stop = bhr.getType() == HitResult.Type.MISS ? end : bhr.getLocation();
        EntityHitResult ehr = ProjectileUtil.getEntityHitResult(level(), this, muzzle, stop,
                new AABB(muzzle, stop).inflate(1.0), e -> e instanceof Player p && validPlayer(p));
        Player hit = null;
        if (ehr != null) { stop = ehr.getLocation(); hit = (Player) ehr.getEntity(); }
        if (tracer) line(muzzle, stop, ParticleTypes.CRIT);
        return hit;
    }

    private void startReload() {
        reloadLeft = def.reloadTicks();
        coverTried = false;
        holdGun();
        triggerAnim("reload", def.reloadTicks());
        playSound(SoundEvents.CROSSBOW_LOADING_END, 2f, 0.8f);
    }

    private void doReload(Player t) {
        faceTarget(t);
        double walk = def.walkBps() * speedMult;
        if (!coverTried) { coverTried = true; coverPos = findCover(t); }
        if (coverPos != null && Math.hypot(coverPos.x - getX(), coverPos.z - getZ()) > 1.2) {     // go behind a block to reload
            Vec3 d = new Vec3(coverPos.x - getX(), 0, coverPos.z - getZ()).normalize();
            moveDir(d, walk);
            setBack(d.dot(dirTo(t)) < -0.3);
        } else if (distanceTo(t) < def.retreatRange() + 4) { moveDir(dirTo(t).scale(-1), walk * 0.6); setBack(true); }
        else { setBack(false); stopMove(); }
        if (--reloadLeft <= 0) { ammo = def.ammo(); setBack(false); coverPos = null; }
    }

    private void runSnipe(Player t) {
        setMode(3);
        stopMove();
        setBack(false);
        if (mt < cur.windup()) faceLimited(t, 20f);                    // keeps tracking until the shot: laser line shows the aim
        if (mt == cur.windup() - 12) line(position().add(0, getBbHeight() * 0.72, 0), aimPoint(t), ParticleTypes.END_ROD);
        if (mt == cur.windup()) {
            Player h = fireBullet(t, def.spread(), def.gunRange(), true);
            if (h != null) hurtPlayer(h, def.bulletDmg());
            ammo--;
            triggerAnim("action", "shoot");
            playSound(SoundEvents.GENERIC_EXPLODE, 3f, 1.6f);
            if (ammo <= 0) { setMode(1); startReload(); endMove(); return; }
        }
        if (++mt >= cur.total()) { setMode(1); endMove(); }
    }

    private void runRunaround(Player t) {
        faceTarget(t);
        Vec3 to = dirTo(t);
        double d = distanceTo(t);
        if (mt % 35 == 0 && random.nextBoolean()) runSign = -runSign;
        double radial = d > 32 ? 0.6 : d < 14 ? -0.6 : 0;
        Vec3 dir = new Vec3(-to.z, 0, to.x).scale(runSign).add(to.scale(radial)).normalize();
        moveDir(dir, cur.speed() * speedMult);
        setBack(false);
        if (++mt >= cur.total()) endMove();
    }

    private void runMinigun(Player t) {
        if (sub == 0) {                                                 // sit down
            sitting = true; setMode(2); stopMove(); faceTarget(t);
            if (mt == 0) { triggerAnim("action", "reload"); playSound(SoundEvents.IRON_GOLEM_REPAIR, 2f, 0.6f); }
            if (++mt >= cur.windup()) { sub = 1; rounds = 0; }
        } else if (sub == 1) {                                          // spray until the magazine is empty
            stopMove();
            faceLimited(t, 9f);                                         // slow turn: players can circle around it
            Player h = fireBullet(t, 4.0, def.gunRange() + 15, rounds % 3 == 0);
            if (h != null) hurtPlayer(h, cur.dmg());
            if (rounds % 4 == 0) playSound(SoundEvents.DISPENSER_LAUNCH, 2f, 0.7f);
            if (++rounds >= cur.count()) { sub = 2; subT = 0; sitting = false; setMode(1); }
        } else {                                                        // stand back up, then back to the AK
            stopMove();
            if (++subT >= cur.total()) endMove();
        }
    }

    // ------------------------------------------------------------------ melee-style moves

    private void arcHit(double reach, int arc, float dmg) { arcHit(reach, arc, dmg, 0.3); }

    private Player arcHit(double reach, int arc, float dmg, double vUp) {
        Player first = null;
        double r = reach * scale() + 0.6;
        Vec3 fwd = Vec3.directionFromRotation(0, getYRot());
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(r, 1.5, r), this::validPlayer)) {
            Vec3 off = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
            double dist = off.length();
            if (dist > r + p.getBbWidth() / 2) continue;
            Vec3 n = dist < 1e-6 ? fwd : off.normalize();
            if (arc < 360 && Math.toDegrees(Math.acos(Mth.clamp(n.dot(fwd), -1, 1))) > arc / 2.0) continue;
            hurtPlayer(p, dmg);
            knock(p, n, 0.5 + dmg * 0.05, vUp);
            if (first == null) first = p;
        }
        Vec3 f = position().add(fwd.scale(r * 0.7)).add(0, getBbHeight() * 0.5, 0);
        parts(ParticleTypes.SWEEP_ATTACK, f.x, f.y, f.z, 1, 0, 0, 0, 0);
        playSound(SoundEvents.PLAYER_ATTACK_STRONG, 2f, 0.7f);
        return first;
    }

    private void shock(double radius, float dmg) {
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(radius, 3, radius), this::validPlayer)) {
            Vec3 off = new Vec3(p.getX() - getX(), 0, p.getZ() - getZ());
            double dist = off.length();
            if (dist > radius) continue;
            if (!p.onGround() && p.getY() > getY() + 0.6) continue;     // jumping over the shockwave avoids it
            hurtPlayer(p, (float) (dmg * (1.0 - 0.4 * dist / radius)));
            knock(p, dist < 1e-6 ? new Vec3(0, 0, 1) : off.normalize(), 1.0, 0.5);
        }
        for (int i = 0; i < 40; i++) {
            double a = i * Math.PI * 2 / 40;
            parts(ParticleTypes.POOF, getX() + Math.cos(a) * radius, getY() + 0.2, getZ() + Math.sin(a) * radius, 2, 0.2, 0.1, 0.2, 0.05);
        }
        parts(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 3, radius * 0.3, 0.1, radius * 0.3, 0);
        playSound(SoundEvents.GENERIC_EXPLODE, 3f, 0.7f);
    }

    private void runMelee(Player t) {
        if (mt == 0) triggerAnim("action", cur.anim());
        for (int i = 1; i < cur.count(); i++) {
            int lead = Math.max(3, (int) (cur.gap() * 0.6));
            if (mt == cur.windup() + i * cur.gap() - lead)
                triggerAnim(i % 2 == 1 ? (cur.anim().equals("swing_a") ? "swing_b" : "swing_a") : cur.anim(), lead);
        }
        int nextHit = -1;
        for (int i = 0; i < cur.count(); i++) {
            int ht = cur.windup() + i * cur.gap();
            if (ht >= mt) { nextHit = ht; break; }
        }
        if (nextHit >= 0 && nextHit - mt > 4) faceLimited(t, 25f);     // stops tracking just before the hit: sidestepping works
        if (mt < cur.windup() && distanceTo(t) > cur.reach() * scale() * 0.9) moveDir(dirTo(t), def.walkBps() * 1.3 * speedMult);
        else stopMove();
        for (int i = 0; i < cur.count(); i++) {
            if (mt == cur.windup() + i * cur.gap()) {
                arcHit(cur.reach(), cur.arc(), cur.dmg());
                moveDir(Vec3.directionFromRotation(0, getYRot()), 5.0);   // small lunge into each hit
            }
        }
        if (++mt >= cur.total()) endMove();
    }

    private void runSlam(Player t) {
        if (mt == 0) triggerAnim("action", cur.anim());
        stopMove();
        if (mt < cur.windup() - 5) faceLimited(t, 25f);
        if (mt == cur.windup()) shock(cur.reach(), cur.dmg());
        if (++mt >= cur.total()) endMove();
    }

    private void runLeap(Player t) {
        if (sub == 0) {
            if (mt == 0) triggerAnim("action", "leap");
            stopMove(); faceTarget(t);
            if (++mt >= cur.windup()) {
                Vec3 to = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ());
                double per = Math.min(to.length() / 22.0, 1.1);
                leapV = to.lengthSqr() < 1e-6 ? Vec3.ZERO : to.normalize().scale(per);
                setDeltaMovement(leapV.x, 0.9, leapV.z);
                sub = 1; subT = 0;
            }
        } else if (sub == 1) {
            setDeltaMovement(leapV.x, getDeltaMovement().y, leapV.z);
            subT++;
            if ((onGround() && subT > 4) || subT > 90) { shock(cur.reach(), cur.dmg()); sub = 2; subT = 0; stopMove(); }
        } else {
            stopMove();
            if (++subT >= cur.total()) endMove();
        }
    }

    // ------------------------------------------------------------------ dash / charge moves

    private void telegraph(Player t, boolean lockDirAtEnd) {
        if (mt == 0) {
            triggerAnim("action", cur.anim());
            playSound(SoundEvents.WARDEN_SONIC_CHARGE, 3f, 1.2f);       // 1-second warning
        }
        faceLimited(t, 30f);
        stopMove();
        if (mt % 4 == 0) parts(ParticleTypes.CRIT, getX(), getY() + getBbHeight() * 0.5, getZ(), 6, 0.5, 0.8, 0.5, 0.1);
        if (lockDirAtEnd && mt == cur.windup() - 1) { Vec3 aim = lastTargetPos.add(targetVel.scale(6)); Vec3 raw = new Vec3(aim.x - getX(), 0, aim.z - getZ()); dashDir = raw.lengthSqr() < 1e-6 ? dirTo(t) : raw.normalize(); faceYaw((float) (Mth.atan2(dashDir.z, dashDir.x) * Mth.RAD_TO_DEG) - 90f); }
    }

    private void runDash(Player t) {
        if (sub == 0) {
            telegraph(t, true);
            if (++mt >= cur.windup()) { sub = 1; subT = 0; dashDist = 0; dashHit = false; rushing = true; }
        } else if (sub == 1) {
            moveDir(dashDir, cur.speed());
            dashDist += cur.speed() / 20.0;
            breakFront(dashDir);
            if (!dashHit) {
                for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(0.6), this::validPlayer)) {
                    hurtPlayer(p, cur.dmg());
                    knock(p, dashDir, 1.4, 0.4);
                    dashHit = true;
                    playSound(SoundEvents.PLAYER_ATTACK_CRIT, 2f, 0.8f);
                }
            }
            subT++;
            if (dashDist >= cur.reach() || subT > cur.reach() / cur.speed() * 20 + 10 || (dashHit && subT > 6)) {
                sub = 2; subT = 0; rushing = false; stopMove();
            }
        } else {
            stopMove();
            if (++subT >= cur.total()) endMove();
        }
    }

    private void runRush(Player t) {          // Boss 4: shotgun charge
        if (sub == 0) {
            telegraph(t, false);
            if (++mt >= cur.windup()) { sub = 1; subT = 0; rushing = true; }
        } else if (sub == 1) {
            Vec3 dir = dirTo(t);
            faceTarget(t);
            moveDir(dir, cur.speed());
            breakFront(dir);
            if (distanceTo(t) <= 2.6 * scale() || ++subT > cur.reach() / cur.speed() * 20 + 30) { sub = 2; subT = 0; rushing = false; }
        } else if (sub == 2) {                // point-blank shells
            stopMove(); faceTarget(t);
            if (subT % 12 == 0 && subT / 12 < cur.count()) {
                if (distanceTo(t) < 5.5 * scale()) { hurtPlayer(t, cur.dmg()); knock(t, dirTo(t), 1.4, 0.35); }
                parts(ParticleTypes.EXPLOSION, getX() + dirTo(t).x * 2, getY() + getBbHeight() * 0.7, getZ() + dirTo(t).z * 2, 1, 0, 0, 0, 0);
                playSound(SoundEvents.GENERIC_EXPLODE, 2.5f, 1.6f);
                triggerAnim("action", "shoot");
            }
            if (++subT >= cur.count() * 12) { sub = 3; subT = 0; }
        } else {                              // walk backwards to reset
            faceTarget(t);
            moveDir(dirTo(t).scale(-1), def.walkBps() * speedMult);
            setBack(true);
            if (++subT >= cur.total() - 10) endMove();
        }
    }

    private void runShieldGrab(Player t) {    // Boss 5: raise shield, walk at the locked target, grab, slam
        if (sub == 0) {
            shielding = true;
            if (mt == 0) triggerAnim("action", "shield");
            stopMove(); faceTarget(t);
            if (++mt >= cur.windup()) { sub = 1; subT = 0; }
        } else if (sub == 1) {
            Vec3 dir = dirTo(t);
            faceTarget(t);
            moveDir(dir, cur.speed() * speedMult);
            breakFront(dir);
            subT++;
            if (distanceTo(t) <= 2.4 * scale() + 0.6) { sub = 2; subT = 0; grabbed = t.getUUID(); triggerAnim("action", "throw"); }
            else if (subT > 160) { sub = 3; subT = 0; shielding = false; }
        } else if (sub == 2) {
            stopMove();
            Player p = grabbed == null ? null : level().getPlayerByUUID(grabbed);
            if (p == null || !validPlayer(p)) { sub = 3; subT = 0; shielding = false; return; }
            faceTarget(p);
            Vec3 f = dirTo(p).scale(0.5);
            if (p instanceof ServerPlayer sp) sp.teleportTo(getX() + f.x, getY() + 1.8 * scale(), getZ() + f.z);
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0;
            if (++subT >= 18) {
                Vec3 out = Vec3.directionFromRotation(0, getYRot()).scale(2.0 * scale());
                if (p instanceof ServerPlayer sp) sp.teleportTo(getX() + out.x, getY(), getZ() + out.z);
                hurtPlayer(p, cur.dmg());
                p.setDeltaMovement(0, -1.2, 0);
                p.hurtMarked = true;
                parts(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.4, 0.1, 0.4, 0);
                playSound(SoundEvents.GENERIC_EXPLODE, 3f, 0.6f);
                sub = 3; subT = 0; shielding = false; grabbed = null;
            }
        } else {
            stopMove();
            if (++subT >= cur.total()) endMove();
        }
    }

    private void breakFront(Vec3 dir) {
        if (!BossConfig.BREAK_BLOCKS.get() || level().isClientSide) return;
        AABB bb = getBoundingBox().move(dir.x, 0.1, dir.z).inflate(0.1, 0, 0.1);
        int limit = BossConfig.MAX_BREAK_PER_TICK.get(), n = 0;
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(bb.minX, bb.minY, bb.minZ),
                BlockPos.containing(bb.maxX - 1e-3, bb.maxY - 1e-3, bb.maxZ - 1e-3))) {
            if (n >= limit) return;
            BlockState s = level().getBlockState(p);
            if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(level(), p) < 0 || s.is(BlockTags.WITHER_IMMUNE)) continue;
            level().destroyBlock(p.immutable(), false);
            n++;
        }
    }

    // ------------------------------------------------------------------ grenades

    private void runGrenade(Player t) {
        if (mt == 0) triggerAnim("action", "throw");
        stopMove(); faceTarget(t);
        if (mt == cur.windup()) {
            for (int i = 0; i < cur.count(); i++) {
                Vec3 p = lastTargetPos.add(targetVel.scale(30));
                if (i > 0) p = p.add((random.nextDouble() - 0.5) * 8, 0, (random.nextDouble() - 0.5) * 8);
                blasts.add(new double[] {p.x, p.y, p.z, 30, cur.dmg(), cur.reach()});
                line(position().add(0, getBbHeight() * 0.8, 0), p, ParticleTypes.SMOKE);
            }
            playSound(SoundEvents.SNOWBALL_THROW, 2f, 0.6f);
        }
        if (++mt >= cur.total()) endMove();
    }

    private void tickBlasts() {
        if (blasts.isEmpty()) return;
        List<double[]> done = new ArrayList<>();
        for (double[] b : blasts) {
            b[3]--;
            if ((int) b[3] % 5 == 0) {
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8;
                    parts(ParticleTypes.CRIT, b[0] + Math.cos(a) * b[5], b[1] + 0.2, b[2] + Math.sin(a) * b[5], 1, 0, 0, 0, 0);
                }
            }
            if (b[3] <= 0) {
                parts(ParticleTypes.EXPLOSION, b[0], b[1] + 0.5, b[2], 2, b[5] * 0.3, 0.3, b[5] * 0.3, 0);
                level().playSound(null, b[0], b[1], b[2], SoundEvents.GENERIC_EXPLODE, getSoundSource(), 4f, 1f);
                for (Player p : level().getEntitiesOfClass(Player.class, new AABB(b[0] - b[5], b[1] - 3, b[2] - b[5], b[0] + b[5], b[1] + 3, b[2] + b[5]), this::validPlayer)) {
                    double dist = Math.sqrt(Math.pow(p.getX() - b[0], 2) + Math.pow(p.getZ() - b[2], 2));
                    if (dist > b[5]) continue;
                    hurtPlayer(p, (float) (b[4] * (1.0 - 0.5 * dist / b[5])));
                    Vec3 o = new Vec3(p.getX() - b[0], 0, p.getZ() - b[2]);
                    knock(p, o.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : o.normalize(), 1.0, 0.5);
                }
                done.add(b);
            }
        }
        blasts.removeAll(done);
    }

    // ------------------------------------------------------------------ extra moves and smarter AI

    private void runLaunch(Player t) {        // Boss 5 phase 2: uppercut launches the player, then shoots them in the air
        if (sub == 0) {
            if (mt == 0) { triggerAnim("action", cur.anim()); holdMelee(); }
            stopMove();
            if (mt < cur.windup() - 4) faceLimited(t, 25f);
            if (mt == cur.windup()) {
                Player hit = arcHit(cur.reach(), cur.arc(), cur.dmg(), 1.15);
                launchTarget = hit == null ? null : hit.getUUID();
            }
            if (++mt > cur.windup()) {
                if (launchTarget == null) { sub = 2; subT = 0; } else { sub = 1; subT = 0; holdGun(); }
            }
        } else if (sub == 1) {
            Player p = launchTarget == null ? null : level().getPlayerByUUID(launchTarget);
            stopMove();
            if (p == null || !validPlayer(p)) { sub = 2; subT = 0; return; }
            faceTarget(p);
            if (subT % cur.gap() == 0) {
                Player h = fireBullet(p, 1.0, def.gunRange(), true);
                if (h != null) hurtPlayer(h, def.bulletDmg() * 0.8f);
                triggerAnim("action", "shoot");
                playSound(SoundEvents.DISPENSER_LAUNCH, 2f, 1.4f);
            }
            if (++subT >= cur.count() * cur.gap()) { sub = 2; subT = 0; }
        } else {
            stopMove();
            if (++subT >= cur.total()) endMove();
        }
    }

    private void runSummon(Player t) {        // Boss 4 phase 2: call armoured zombies (max 6 alive at once)
        if (mt == 0) { triggerAnim("action", "throw"); playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 3f, 0.8f); }
        stopMove(); faceTarget(t);
        if (mt == cur.windup() && level() instanceof ServerLevel sl) {
            minions.removeIf(id -> sl.getEntity(id) == null);
            int room = Math.max(0, 6 - minions.size());
            for (int i = 0; i < Math.min(cur.count(), room); i++) {
                Zombie z = EntityType.ZOMBIE.create(level());
                if (z == null) continue;
                double a = random.nextDouble() * Math.PI * 2;
                z.moveTo(getX() + Math.cos(a) * 3, getY(), getZ() + Math.sin(a) * 3, random.nextFloat() * 360f, 0);
                z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));   // also stops them burning in daylight
                z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                z.setPersistenceRequired();
                z.setTarget(t);
                sl.addFreshEntity(z);
                minions.add(z.getUUID());
                parts(ParticleTypes.SOUL, z.getX(), z.getY() + 1, z.getZ(), 12, 0.3, 0.6, 0.3, 0.05);
            }
        }
        if (++mt >= cur.total()) endMove();
    }

    /** Looks for a spot a few blocks away that the player cannot see. */
    private Vec3 findCover(Player t) {
        Vec3 eye = t.getEyePosition();
        Vec3 best = null;
        double bestD = 1e9;
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8, rad = 4 + random.nextInt(5);
            double x = getX() + Math.cos(a) * rad, z = getZ() + Math.sin(a) * rad;
            BlockPos bp = BlockPos.containing(x, getY(), z);
            if (!level().getBlockState(bp).isAir() || !level().getBlockState(bp.above()).isAir() || level().getBlockState(bp.below()).isAir()) continue;
            Vec3 head = new Vec3(x, getY() + getBbHeight() * 0.9, z);
            BlockHitResult hit = level().clip(new ClipContext(eye, head, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t));
            if (hit.getType() == HitResult.Type.MISS) continue;
            double d = distanceToSqr(x, getY(), z);
            if (d < bestD) { bestD = d; best = new Vec3(x, getY(), z); }
        }
        return best;
    }

    /** Every 10 s compare how fast the boss is losing HP: speed up if it is too easy, ease off if players keep dying. */
    private void directorTick() {
        if (++fightTicks % 600 != 0) return;
        float f = getHealth() / getMaxHealth();
        float lost = hpMark - f;
        if (lost > 0.35f) adj = Math.min(0.3, adj + 0.1);
        else if (lost < 0.08f) adj = Math.max(-0.3, adj - 0.05);
        hpMark = f;
    }

    public void playerDied() { adj = Math.max(-0.3, adj - 0.1); }

    /** Player on a pillar, in a sealed pit or otherwise unreachable: lob explosives and dig under them. */
    private void antiCheese(Player t) {
        if (tickCount % 20 != 0) return;
        if (cheeseCd > 0) cheeseCd--;
        double horiz = Math.hypot(t.getX() - getX(), t.getZ() - getZ());
        boolean high = t.getY() - getY() > 6 && horiz < 25;
        boolean sealed = false;
        if (!high && distanceTo(t) < 40 && t.onGround()) {
            var path = getNavigation().createPath(t, 0);
            sealed = path == null || !path.canReach();
        }
        cheeseTicks = (high || sealed) ? cheeseTicks + 20 : 0;
        if (cheeseTicks < 60 || cheeseCd > 0) return;
        cheeseCd = 5;
        blasts.add(new double[] {t.getX(), t.getY(), t.getZ(), 30, 14, 4.5});
        line(position().add(0, getBbHeight() * 0.8, 0), t.position(), ParticleTypes.SMOKE);
        triggerAnim("action", "throw");
        if (high && BossConfig.BREAK_BLOCKS.get()) {          // knock out a block under the player's feet
            for (int dy = 1; dy <= 4; dy++) {
                BlockPos bp = BlockPos.containing(t.getX(), t.getY() - dy, t.getZ());
                BlockState s = level().getBlockState(bp);
                if (s.isAir() || !s.getFluidState().isEmpty() || s.getDestroySpeed(level(), bp) < 0 || s.is(BlockTags.WITHER_IMMUNE)) continue;
                level().destroyBlock(bp, false);
                break;
            }
        }
    }

    // ------------------------------------------------------------------ animation sync (drawn on the client by client/BossAnims)

    public static final String[] ANIMS = {"shoot", "reload", "swing_a", "swing_b", "spin", "slam", "leap", "dash_warn", "throw"};
    public int clientAnimStart;

    public int getMode() { return entityData.get(MODE); }
    public boolean isBack() { return entityData.get(BACK); }
    public boolean isAiming() { return (entityData.get(FLAGS) & 1) != 0; }
    public boolean isRushing() { return (entityData.get(FLAGS) & 2) != 0; }
    public boolean isShielding() { return (entityData.get(FLAGS) & 4) != 0; }
    public int animId() { return entityData.get(ANIM_ID); }
    public int animWindup() { return entityData.get(ANIM_W); }

    /** Plays a one-shot animation; the strike frame is stretched to land on the move's windup tick. */
    public void triggerAnim(String controller, String anim) { triggerAnim(anim, cur != null ? cur.windup() : 0); }

    public void triggerAnim(String anim, int windupTicks) {
        for (int i = 0; i < ANIMS.length; i++) {
            if (ANIMS[i].equals(anim)) {
                entityData.set(ANIM_W, windupTicks);
                entityData.set(ANIM_ID, i);
                entityData.set(ANIM_T, (int) level().getGameTime());
                return;
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == ANIM_T) clientAnimStart = tickCount;
    }
}
