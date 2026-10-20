package com.taczbosses;

import static com.taczbosses.BossDef.MoveType.*;

import java.util.List;

public final class BossDefs {
    private BossDefs() {}

    private static BossDef.MoveDef mv(BossDef.MoveType t, int p1, int p2, int w, double r1, double r2, int cd, String a,
                                      int windup, int gap, int count, int total, double reach, int arc, float dmg, double spd) {
        return new BossDef.MoveDef(t, p1, p2, w, r1, r2, cd, a, windup, gap, count, total, reach, arc, dmg, spd);
    }

    public static final List<BossDef> ALL = List.of(
        // ---- 1: Lt. Rockie (rifle, machete, grenades) ----
        new BossDef(0, "boss_1", 1.2, 8100, 4.3,
            "tacz:m4a1", "", "survival_instinct:machete", "", "survival_instinct:derset_rockie_armor_helmet",
            30, 50, 3, 7f, 40, 2.5, 1, 8, 0.04f, 0.5f, 0f,
            List.of(
                mv(SHOOT, 1, 9, 5, 5, 40, 0, "", 0, 0, 0, 60, 0, 0, 0, 0),
                mv(MELEE, 1, 9, 3, 0, 3.4, 60, "swing_a", 12, 0, 1, 28, 3.0, 100, 7, 0),
                mv(MELEE, 1, 9, 2, 0, 3.2, 90, "swing_b", 10, 0, 1, 26, 3.0, 90, 7, 0),
                mv(MELEE, 2, 9, 4, 0, 3.4, 100, "swing_a", 10, 10, 2, 40, 3.0, 100, 9, 0),
                mv(GRENADE, 1, 1, 1, 10, 28, 320, "throw", 15, 0, 1, 40, 4.5, 0, 14, 0),
                mv(GRENADE, 2, 9, 2, 10, 28, 260, "throw", 15, 0, 2, 40, 4.5, 0, 14, 0))),
        // ---- 2: Shade Marksman (sniper, knife dashes) ----
        new BossDef(1, "boss_2", 1.2, 13500, 5.0,
            "tacz:m95", "", "survival_instinct:lance_with_knife", "", "lrarmor:sniper_helmet",
            5, 80, 40, 15f, 100, 0.3, 1, 10, 0.02f, 0.5f, 0f,
            List.of(
                mv(SNIPE, 1, 9, 5, 15, 100, 0, "kneel", 25, 0, 1, 45, 0, 0, 0, 0),
                mv(RUNAROUND, 1, 9, 4, 0, 100, 0, "", 0, 0, 0, 70, 0, 0, 0, 6.5),
                mv(DASH, 1, 1, 2, 8, 30, 160, "dash_warn", 20, 0, 1, 25, 30, 0, 8, 12),
                mv(MELEE, 1, 9, 3, 0, 3.4, 70, "swing_a", 10, 8, 2, 34, 3.0, 110, 8, 0),
                mv(DASH, 2, 9, 3, 6, 30, 120, "dash_warn", 15, 0, 1, 22, 30, 0, 10, 14),
                mv(MELEE, 2, 9, 3, 0, 3.4, 60, "swing_b", 8, 6, 3, 36, 3.0, 120, 9, 0))),
        // ---- 3: Chemical Sergeant (AK, hammer, minigun) ----
        new BossDef(2, "boss_3", 1.3, 21600, 3.0,
            "tacz:ak47", "tacz:m249", "survival_instinct:sledge_great_hammer", "", "lrarmor:armored_chemical_helmet",
            30, 60, 3, 9f, 45, 3.0, 1, 7, 0.02f, 0.5f, 0f,
            List.of(
                mv(SHOOT, 1, 9, 5, 8, 45, 0, "", 0, 0, 0, 60, 0, 0, 0, 0),
                mv(MELEE, 1, 9, 4, 0, 5, 50, "swing_a", 25, 0, 1, 50, 4.0, 110, 14, 0),
                mv(MELEE, 1, 9, 3, 0, 5, 100, "spin", 20, 0, 1, 45, 4.0, 360, 10, 0),
                mv(SLAM, 1, 9, 3, 0, 9, 140, "slam", 28, 0, 1, 55, 8, 0, 14, 0),
                mv(MELEE, 2, 9, 3, 0, 5, 160, "swing_a", 18, 16, 3, 70, 4.0, 120, 12, 0),
                mv(LEAP_SLAM, 2, 9, 2, 8, 24, 240, "leap", 20, 0, 1, 70, 8, 0, 17, 0),
                mv(MINIGUN, 2, 9, 4, 5, 60, 400, "sit_minigun", 30, 0, 500, 40, 0, 0, 6, 0))),
        // ---- 4: Breacher Scout (shotgun rush) ----
        new BossDef(3, "boss_4", 1.5, 24300, 4.8,
            "tacz:m1014", "", "survival_instinct:machete", "", "lrarmor:scout_helmet",
            8, 70, 18, 2.5f, 15, 6.0, 8, 5, 0.05f, 0.5f, 0f,
            List.of(
                mv(SHOOT, 1, 9, 4, 4, 15, 0, "", 0, 0, 0, 50, 0, 0, 0, 0),
                mv(CHASE, 1, 9, 7, 8, 100, 0, "", 0, 0, 0, 40, 0, 0, 0, 1.0),
                mv(SHOTGUN_RUSH, 1, 9, 3, 8, 35, 200, "dash_warn", 20, 0, 3, 40, 40, 0, 12, 8.4),
                mv(MELEE, 1, 9, 3, 0, 3.6, 60, "swing_a", 10, 0, 1, 28, 3.2, 100, 10, 0),
                mv(MELEE, 2, 9, 3, 0, 3.6, 100, "swing_b", 8, 6, 5, 50, 3.2, 130, 6, 0),
                mv(DASH, 2, 9, 2, 4, 14, 150, "dash_warn", 15, 0, 1, 30, 14, 0, 12, 9),
                mv(GRENADE, 2, 9, 2, 8, 28, 260, "throw", 15, 0, 2, 40, 4.5, 0, 14, 0),
                mv(SUMMON, 2, 9, 2, 0, 80, 700, "throw", 20, 0, 3, 40, 0, 0, 0, 0))),
        // ---- 5: Bulwark Warlord (P90, shield, katana; phase 2 at 66%, phase 3 at 30%) ----
        new BossDef(4, "boss_5", 1.7, 43200, 3.5,
            "tacz:p90", "", "survival_instinct:katana", "survival_instinct:stop_sing_shield", "lrarmor:defender_helmet",
            50, 70, 2, 11f, 70, 3.5, 1, 8, 0.03f, 0.66f, 0.30f,
            List.of(
                mv(SHOOT, 1, 2, 5, 8, 70, 0, "", 0, 0, 0, 70, 0, 0, 0, 0),
                mv(SHIELD_GRAB, 1, 9, 3, 8, 30, 360, "shield", 20, 0, 1, 40, 3.0, 0, 21, 4.5),
                mv(DASH, 1, 9, 2, 5, 14, 200, "dash_warn", 18, 0, 1, 30, 10, 0, 11, 7),
                mv(MELEE, 2, 9, 4, 0, 5, 120, "swing_a", 12, 10, 5, 70, 4.5, 130, 11, 0),
                mv(DASH, 2, 9, 3, 6, 25, 220, "dash_warn", 20, 0, 1, 30, 25, 0, 21, 14),
                mv(MELEE, 2, 9, 2, 0, 4.5, 200, "swing_b", 14, 0, 1, 36, 4.0, 110, 11, 0),
                mv(SLAM, 3, 9, 3, 0, 14, 300, "slam", 40, 0, 1, 70, 14, 0, 24, 0),
                mv(MELEE, 3, 9, 5, 0, 5, 100, "swing_a", 10, 8, 9, 90, 4.5, 140, 12, 0),
                mv(LAUNCH_SHOT, 2, 2, 2, 0, 5, 240, "swing_b", 14, 4, 6, 60, 4.0, 110, 11, 0)))
    );
}
