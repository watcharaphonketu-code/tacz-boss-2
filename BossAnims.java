package com.taczbosses.client;

import com.taczbosses.BossEntity;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * One-shot animations as keyframes (degrees; vanilla convention: arm xRot negative = raised forward).
 * Slow wind-up, fast strike: the strike time is stretched to land on the move's hit tick.
 */
public final class BossAnims {
    private BossAnims() {}

    record Key(float t, float x, float y, float z) {}
    record Anim(float length, float strike, boolean additive, Map<String, List<Key>> tracks) {}

    private static final Map<String, Anim> BY_NAME = new HashMap<>();

    private static Key k(float t, float x, float y, float z) { return new Key(t, x, y, z); }
    private static void def(String n, float len, float strike, boolean add, Map<String, List<Key>> tr) { BY_NAME.put(n, new Anim(len, strike, add, tr)); }

    static {
        def("swing_a", 1.0f, 0.65f, false, Map.of(          // overhead chop: slow raise, fast fall
            "rarm", List.of(k(0, -30, 0, 0), k(.5f, -170, 0, 12), k(.6f, -175, 0, 12), k(.7f, -20, 0, -8), k(1, -30, 0, 0)),
            "larm", List.of(k(0, -10, 0, 0), k(.5f, -120, 0, -10), k(.7f, -30, 0, 0), k(1, -10, 0, 0)),
            "body", List.of(k(0, 0, 0, 0), k(.5f, -8, 0, 0), k(.7f, 14, 0, 0), k(1, 0, 0, 0)),
            "twist", List.of(k(0, 0, 0, 0), k(.5f, 0, 22, 0), k(.7f, 0, -28, 0), k(1, 0, 0, 0)),
            "rleg", List.of(k(0, 0, 0, 0), k(.5f, -25, 0, 0), k(1, 0, 0, 0)),
            "lleg", List.of(k(0, 0, 0, 0), k(.5f, 20, 0, 0), k(1, 0, 0, 0))));
        def("swing_b", 0.9f, 0.5f, false, Map.of(           // horizontal slash
            "rarm", List.of(k(0, -30, 0, 0), k(.4f, -90, -70, 0), k(.55f, -90, 60, 0), k(.9f, -30, 0, 0)),
            "larm", List.of(k(0, -10, 0, 0), k(.4f, -60, 20, 0), k(.9f, -10, 0, 0)),
            "twist", List.of(k(0, 0, 0, 0), k(.4f, 0, 35, 0), k(.55f, 0, -40, 0), k(.9f, 0, 0, 0)),
            "rleg", List.of(k(0, 0, 0, 0), k(.4f, -20, 0, 0), k(.9f, 0, 0, 0)),
            "lleg", List.of(k(0, 0, 0, 0), k(.4f, 20, 0, 0), k(.9f, 0, 0, 0))));
        def("spin", 1.0f, 0.55f, false, Map.of(             // 360 sweep: the whole body turns (see spinDeg)
            "rarm", List.of(k(0, -30, 0, 0), k(.35f, -90, -60, 0), k(.8f, -90, -60, 0), k(1, -30, 0, 0)),
            "larm", List.of(k(0, -10, 0, 0), k(.35f, -90, 60, 0), k(.8f, -90, 60, 0), k(1, -10, 0, 0)),
            "spin", List.of(k(0, 0, 0, 0), k(.3f, -20, 0, 0), k(.8f, 360, 0, 0), k(1, 360, 0, 0))));
        def("slam", 1.6f, 1.1f, false, Map.of(              // both hands up, heavy smash
            "rarm", List.of(k(0, -30, 0, 0), k(.9f, -175, 0, 0), k(1.0f, -175, 0, 0), k(1.1f, -15, 0, 0), k(1.6f, -30, 0, 0)),
            "larm", List.of(k(0, -10, 0, 0), k(.9f, -170, 0, 0), k(1.0f, -170, 0, 0), k(1.1f, -15, 0, 0), k(1.6f, -10, 0, 0)),
            "body", List.of(k(0, 0, 0, 0), k(.9f, -12, 0, 0), k(1.1f, 25, 0, 0), k(1.6f, 0, 0, 0)),
            "rleg", List.of(k(0, 0, 0, 0), k(.9f, -15, 0, 0), k(1.6f, 0, 0, 0)),
            "lleg", List.of(k(0, 0, 0, 0), k(.9f, 15, 0, 0), k(1.6f, 0, 0, 0))));
        def("leap", 1.2f, 0.5f, false, Map.of(
            "rarm", List.of(k(0, -30, 0, 0), k(.5f, 35, 0, 0), k(.7f, -170, 0, 0), k(1.2f, -30, 0, 0)),
            "larm", List.of(k(0, -10, 0, 0), k(.5f, 35, 0, 0), k(.7f, -170, 0, 0), k(1.2f, -10, 0, 0)),
            "body", List.of(k(0, 0, 0, 0), k(.5f, 22, 0, 0), k(.7f, -10, 0, 0), k(1.2f, 0, 0, 0))));
        def("dash_warn", 1.0f, 1.0f, false, Map.of(         // crouch and lean back: the 1-second warning
            "body", List.of(k(0, 0, 0, 0), k(.4f, 28, 0, 0), k(1, 30, 0, 0)),
            "rarm", List.of(k(0, -30, 0, 0), k(.4f, 40, 0, 0), k(1, 40, 0, 0)),
            "larm", List.of(k(0, -10, 0, 0), k(.4f, 40, 0, 0), k(1, 40, 0, 0)),
            "rleg", List.of(k(0, 0, 0, 0), k(.4f, -30, 0, 0), k(1, -30, 0, 0)),
            "lleg", List.of(k(0, 0, 0, 0), k(.4f, 25, 0, 0), k(1, 25, 0, 0))));
        def("throw", 0.7f, 0.4f, false, Map.of(
            "rarm", List.of(k(0, -30, 0, 0), k(.25f, -170, 0, 0), k(.4f, -20, 0, 0), k(.7f, -30, 0, 0)),
            "body", List.of(k(0, 0, 0, 0), k(.25f, -10, 0, 0), k(.4f, 10, 0, 0), k(.7f, 0, 0, 0))));
        def("reload", 1.5f, 1.5f, false, Map.of(            // mag out, mag in, charging handle
            "larm", List.of(k(0, -80, 35, 0), k(.3f, -35, 40, 0), k(.9f, -35, 40, 0), k(1.2f, -80, 30, 0), k(1.5f, -80, 35, 0)),
            "rarm", List.of(k(0, -86, -10, 0), k(.3f, -65, -10, 0), k(1.2f, -65, -10, 0), k(1.5f, -86, -10, 0)),
            "body", List.of(k(0, 0, 0, 0), k(.3f, 8, 0, 0), k(1.2f, 8, 0, 0), k(1.5f, 0, 0, 0))));
        def("shoot", 0.2f, 0.2f, true, Map.of(              // recoil, added on top of the aim pose
            "body", List.of(k(0, 0, 0, 0), k(.04f, -4, 0, 0), k(.2f, 0, 0, 0)),
            "rarm", List.of(k(0, 0, 0, 0), k(.04f, -6, 0, 0), k(.2f, 0, 0, 0))));
    }

    private static float[] sample(List<Key> ks, float t) {
        Key a = ks.get(0);
        if (t <= a.t()) return new float[] {a.x(), a.y(), a.z()};
        for (int i = 1; i < ks.size(); i++) {
            Key b = ks.get(i);
            if (t <= b.t()) {
                float f = (t - a.t()) / Math.max(1e-4f, b.t() - a.t());
                f = f * f * (3 - 2 * f);
                return new float[] {Mth.lerp(f, a.x(), b.x()), Mth.lerp(f, a.y(), b.y()), Mth.lerp(f, a.z(), b.z())};
            }
            a = b;
        }
        return new float[] {a.x(), a.y(), a.z()};
    }

    private static Anim active(BossEntity e) {
        int id = e.animId();
        return id < 0 || id >= BossEntity.ANIMS.length ? null : BY_NAME.get(BossEntity.ANIMS[id]);
    }

    private static float time(BossEntity e, Anim a, float age) {
        float rate = (a.additive() || e.animWindup() <= 0) ? 1f : a.strike() * 20f / e.animWindup();
        return (age - e.clientAnimStart) / 20f * rate;
    }

    /** Extra yaw (degrees) for the 360 spin; applied to the whole entity by the renderer. */
    public static float spinDeg(BossEntity e, float age) {
        Anim a = active(e);
        if (a == null || !a.tracks().containsKey("spin")) return 0f;
        float t = time(e, a, age);
        if (t < 0 || t > a.length()) return 0f;
        return sample(a.tracks().get("spin"), t)[0];
    }

    public static void apply(BossHumanoidModel m, BossEntity e, float age) {
        Anim a = active(e);
        if (a == null) return;
        float t = time(e, a, age);
        if (t < 0 || t > a.length()) return;
        for (Map.Entry<String, List<Key>> en : a.tracks().entrySet()) {
            float[] v = sample(en.getValue(), t);
            float x = v[0] * Mth.DEG_TO_RAD, y = v[1] * Mth.DEG_TO_RAD, z = v[2] * Mth.DEG_TO_RAD;
            switch (en.getKey()) {
                case "twist" -> { m.body.yRot += y; m.head.yRot += y; m.rightArm.yRot += y; m.leftArm.yRot += y; }
                case "spin" -> { }
                default -> {
                    ModelPart p = switch (en.getKey()) {
                        case "rarm" -> m.rightArm;
                        case "larm" -> m.leftArm;
                        case "body" -> m.body;
                        case "head" -> m.head;
                        case "rleg" -> m.rightLeg;
                        default -> m.leftLeg;
                    };
                    if (a.additive()) { p.xRot += x; p.yRot += y; p.zRot += z; }
                    else { p.xRot = x; p.yRot = y; p.zRot = z; }
                }
            }
        }
    }
}
