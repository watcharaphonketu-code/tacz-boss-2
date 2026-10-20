package com.taczbosses.client;

import com.taczbosses.BossEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Player-shaped model: poses are set in code with the same arm conventions the game uses for bows and zombies. */
public class BossHumanoidModel extends HumanoidModel<BossEntity> {
    public BossHumanoidModel(ModelPart root) {
        super(root);
        this.hat.visible = false;
    }

    @Override
    public void setupAnim(BossEntity e, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        super.setupAnim(e, limbSwing, limbSwingAmount, age, netHeadYaw, headPitch);
        if (e.isBack()) {                                   // walking backwards: reverse the leg swing
            this.rightLeg.xRot = -this.rightLeg.xRot;
            this.leftLeg.xRot = -this.leftLeg.xRot;
        }
        float hx = this.head.xRot, hy = this.head.yRot;
        int mode = e.getMode();
        boolean gun = mode == 1 || mode == 2 || mode == 3;
        if (gun) {
            if (e.isAiming() || mode >= 2) {                // shouldered rifle, arms follow the head pitch
                this.rightArm.xRot = -1.50f + hx; this.rightArm.yRot = -0.12f + hy; this.rightArm.zRot = 0f;
                this.leftArm.xRot = -1.42f + hx;  this.leftArm.yRot = 0.60f + hy;   this.leftArm.zRot = 0f;
            } else {                                        // "low ready" while moving
                float sway = Mth_sin(age * 0.2f) * 0.02f * limbSwingAmount;
                this.rightArm.xRot = -0.85f + sway; this.rightArm.yRot = -0.10f; this.rightArm.zRot = 0f;
                this.leftArm.xRot = -0.95f + sway;  this.leftArm.yRot = 0.55f;   this.leftArm.zRot = 0f;
            }
        } else {                                            // melee weapon held ready
            this.rightArm.xRot = -0.55f; this.rightArm.yRot = -0.10f; this.rightArm.zRot = 0.05f;
        }
        if (mode == 2) {                                    // seated (minigun)
            this.rightLeg.xRot = -1.45f; this.rightLeg.yRot = 0.20f;
            this.leftLeg.xRot = -1.45f;  this.leftLeg.yRot = -0.20f;
        } else if (mode == 3) {                             // kneeling (sniper)
            this.rightLeg.xRot = -0.35f; this.rightLeg.yRot = 0.10f;
            this.leftLeg.xRot = -1.50f;  this.leftLeg.yRot = -0.15f;
        }
        if (e.isRushing()) {                                // charging: lean forward, arms back
            this.body.xRot = 0.45f;
            this.rightArm.xRot = 0.8f; this.leftArm.xRot = 0.8f;
            this.rightArm.yRot = 0f;   this.leftArm.yRot = 0f;
        }
        if (e.isShielding()) {                              // shield up in the left arm
            this.leftArm.xRot = -1.35f + hx; this.leftArm.yRot = -0.15f; this.leftArm.zRot = 0f;
        }
        BossAnims.apply(this, e, age);
    }

    private static float Mth_sin(float v) { return (float) Math.sin(v); }
}
