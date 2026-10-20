package com.taczbosses;

import net.minecraftforge.common.ForgeConfigSpec;

public class BossConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue FIRST_DAY, GAP_DAYS, LOCK_RADIUS, SIGHT, GLOW_SECONDS,
            MAX_BREAK_PER_TICK, LOCK_TIMEOUT_MIN;
    public static final ForgeConfigSpec.BooleanValue ANNOUNCE, BREAK_BLOCKS, OPS_EXEMPT, LOOP;
    public static final ForgeConfigSpec.DoubleValue PHASE2_HEAL, HP_MULT, DMG_MULT;
    public static final ForgeConfigSpec.ConfigValue<String> AI_LEVEL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("spawn");
        FIRST_DAY = b.comment("Days after world start until boss 1 spawns").defineInRange("firstDay", 10, 1, 10000);
        GAP_DAYS = b.comment("Days between a boss dying and the next one spawning").defineInRange("gapDays", 15, 1, 10000);
        LOOP = b.comment("After boss 5 dies, restart from boss 1 with stronger stats").define("loopAfterFifth", false);
        ANNOUNCE = b.comment("Announce rough coordinates in chat when a boss spawns").define("announce", true);
        GLOW_SECONDS = b.defineInRange("glowSeconds", 20, 0, 600);
        b.pop();
        b.push("world");
        LOCK_RADIUS = b.comment("Players cannot break blocks within this radius of the boss spawn point until it dies (0 = off)")
                .defineInRange("lockRadius", 64, 0, 512);
        LOCK_TIMEOUT_MIN = b.comment("Unlock the area if no player has been inside it for this many minutes")
                .defineInRange("lockTimeoutMinutes", 10, 1, 1000);
        OPS_EXEMPT = b.comment("Operators (permission level 2+) may still break blocks in the locked area").define("opsExempt", true);
        BREAK_BLOCKS = b.comment("Boss dashes/charges destroy blocks in front of them (bedrock etc. always safe)").define("bossBreakBlocks", true);
        MAX_BREAK_PER_TICK = b.defineInRange("maxBlocksBrokenPerTick", 30, 1, 500);
        b.pop();
        b.push("combat");
        SIGHT = b.defineInRange("sightRange", 120, 16, 512);
        PHASE2_HEAL = b.comment("Fraction of max health restored on entering phase 2").defineInRange("phase2Heal", 0.25, 0.0, 1.0);
        HP_MULT = b.comment("Multiplier on every boss's total HP").defineInRange("hpMultiplier", 1.0, 0.05, 100.0);
        DMG_MULT = b.comment("Multiplier on every boss's damage").defineInRange("damageMultiplier", 1.0, 0.05, 100.0);
        AI_LEVEL = b.comment("EASY, NORMAL or HARD").define("aiLevel", "HARD");
        b.pop();
        SPEC = b.build();
    }
}
