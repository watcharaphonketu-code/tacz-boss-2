package com.taczbosses;

import java.util.List;

/** Static data for one boss. Edit BossDefs.java to rebalance, then rebuild. */
public record BossDef(int index, String key, double scale, int hp, double walkBps,
                      String gun, String gun2, String melee, String offhand, String helmet,
                      int ammo, int reloadTicks, int shotInterval, float bulletDmg, double gunRange, double spread, int pellets,
                      double retreatRange, float strafeChance, float p2, float p3, List<MoveDef> moves) {

    public enum MoveType {
        SHOOT, SNIPE, RUNAROUND, CHASE, MELEE, SLAM, DASH, SHOTGUN_RUSH, MINIGUN, SHIELD_GRAB, GRENADE, LEAP_SLAM, LAUNCH_SHOT, SUMMON
    }

    /**
     * windup = ticks before the first hit / telegraph length, gap = ticks between hits, count = number of hits (or shots / rounds),
     * total = move length or recovery ticks, reach = blocks (melee reach, slam radius, dash distance), speed = blocks per second.
     */
    public record MoveDef(MoveType type, int minPhase, int maxPhase, int weight, double minR, double maxR, int cooldown,
                          String anim, int windup, int gap, int count, int total, double reach, int arc, float dmg, double speed) {
    }
}
