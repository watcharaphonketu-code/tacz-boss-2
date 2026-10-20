# Tacz bosses (Forge 1.20.1)

Five bosses, spawned one at a time (first after 10 days, then 15 days after each death), using TACZ guns and Survival Instinct weapons.

**Status: first draft. It has NOT been compiled or run in-game.** Expect to fix compile errors and tune animations. See "Known gaps".

## Build the .jar

### Option A – GitHub (no tools needed)
1. Create a GitHub repository and upload everything in this folder.
2. Open the **Actions** tab, wait for the `build` run to finish.
3. Open the run and download `taczbosses-jar` under **Artifacts**.

### Option B – on your PC
1. Install **JDK 17** and **Gradle 8.1.1** (this zip has no `gradlew`).
2. In this folder run: `gradle build`
3. The jar is in `build/libs/`.


Put the jar in `mods/` together with TACZ, Survival Instinct and the armor mod (`lrarmor`). GeckoLib is no longer needed.

## Use
- Spawn eggs are in the creative **Spawn Eggs** tab, or `/summon taczbosses:boss_1` ... `boss_5`.
- Config: `config/taczbosses-common.toml` (days, lock radius, block breaking, AI level, HP/damage multipliers, announce).
- Stats, moves, item ids, ranges: `src/main/java/com/taczbosses/BossDefs.java` (edit, then rebuild).
- Names: `assets/taczbosses/lang/th_th.json` and `en_us.json`.
- Model: the vanilla player model, skin in `assets/taczbosses/textures/entity/boss_N.png` (64x64 player-skin layout, replace with real skins). Armor of any mod is drawn by the game's normal armor layer.
- Poses and animations are code: `client/BossHumanoidModel.java` (gun low-ready / aim, kneel, seated, rush, shield) and `client/BossAnims.java` (keyframes for swings, spin, slam, leap, throw, reload, recoil).
- `gen_assets.py` regenerates the placeholder textures.
- The game log prints `[taczbosses] boss N <slot> = <item>` for every armor piece; a `not found` warning means that piece could not be matched to the helmet's set.

## Implemented
Spawn schedule, boss bar, 25% heal on phase 2 (boss 5: phases at 66% / 30%), glow on spawn, rough-coordinate announcement, block lock around the spawn point (also blocks explosions, ops exempt), 120-block sight with line-of-sight, target locking during attacks, target swap when another player shoots (5 s lock), dropping the target when it hides or leaves sight, reload after the magazine is empty (the boss walks behind a block to reload when it can), strafing and walking backwards while shooting, scales 1.2 / 1.2 / 1.3 / 1.5 / 1.7, spawn eggs, drops of held items, immunity to boats / water flow / cobwebs, helmet-only armor lookup (finds the rest of the set).

Moves: shotgun rush (90% damage reduction, breaks blocks), sniper kneel + run-around + knife dash, hammer combos / slam / leap slam / minigun (500 rounds, 1.5x damage taken while seated), shield charge + grab + slam, katana combos, shield-bash and iaido dashes, uppercut + shoot in the air (boss 5), zombie summon (boss 4, max 6 alive, removed when it dies), grenades.

AI: aim history delay (aims where the player was a few ticks ago), learns which way the player likes to dodge and leads the aim and dashes that way, uses moves that keep missing less often, picks the weakest or nearest visible player, seeks cover to reload, anti-cheese (lobs explosives and digs under players on pillars or in sealed pits), difficulty director (speeds up if the boss loses HP too fast, eases off when players die). `aiLevel` in the config switches EASY / NORMAL / HARD.

## Known gaps
- The code passed a javac syntax check only. It has never been compiled against Forge / GeckoLib or run in game.
- Guns are shown with the real TACZ item but fire as custom hitscan with vanilla sounds (TACZ's own firing API is not used). How the gun renders in the hand may need adjusting.
- Boss 4 uses the machete as its melee weapon (no id was given).
- Poses and animations are hand-written keyframes, not TACZ's own player animations, and I could not see them in game. They will need tuning. Each swing's strike frame is stretched to match the move's hit tick.
- Damage the boss takes is divided by a per-boss factor so 1000 max HP stands in for the real total.
- Placeholder model (blocky humanoid) and flat-colour textures.
