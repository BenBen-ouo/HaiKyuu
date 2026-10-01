/*
主攻手 WS 的角色邏輯，包含助跑、起跳、空中攻擊揮臂與落地回位。
目前只處理攻擊動畫流程，真正扣球改變球速之後再從碰撞邏輯接入。
*/
package model.player;

import model.GameConfig;
import model.TeamInput;

public class WingSpiker extends Player {
    private static final double APPROACH_SPEED = 7.5;
    private static final double RETURN_SPEED = 6.5;

    private final double homeX;
    private final DiveController diveController;
    private boolean previousWingAttack = false;

    public WingSpiker(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
        this.homeX = x;
        this.diveController = new DiveController(this);
    }

    @Override
    public void resetToInitial() {
        super.resetToInitial();
        diveController.cancel();
        previousWingAttack = false;
    }

    @Override
    public void update(TeamInput input) {
        clearAttackAttempt();
        boolean justPressedAttack = input.wingAttack && !previousWingAttack;

        if (diveController.isActive()) {
            diveController.update(input.wingAttack);
            updateActionAnimation();
            if (!diveController.isActive()) {
                startReturnToHome();
            }
            previousWingAttack = input.wingAttack;
            return;
        }

        if (isMovementLockedByAnimation()) {
            vx = 0;
            applyGravity();
            updateActionAnimation();
            previousWingAttack = input.wingAttack;
            return;
        }

        if (action == PlayerAction.RUN_APPROACH) {
            updateApproachRun(input);
            previousWingAttack = input.wingAttack;
            return;
        }

        if (action == PlayerAction.ATTACK_READY || action == PlayerAction.ATTACK_SWING
                || action == PlayerAction.AIR_SETTING) {
            updateAttackInAir(justPressedAttack, input);
            previousWingAttack = input.wingAttack;
            return;
        }

        if (action == PlayerAction.RUN_RETURN) {
            updateReturnToHome();
            previousWingAttack = input.wingAttack;
            return;
        }

        vx = 0;

        if (justPressedAttack && !input.hasFirstRegularTouch
                && diveController.tryStartTowardNet(true)) {
            diveController.update(input.wingAttack);
            updateActionAnimation();
            previousWingAttack = input.wingAttack;
            return;
        } else if (justPressedAttack) {
            startRunApproachAnimation(2);
        }

        applyGravity();
        updateActionAnimation();
        previousWingAttack = input.wingAttack;
    }

    private void updateApproachRun(TeamInput input) {
        vx = directionTowardNet() * APPROACH_SPEED;
        applyGravity();
        updateActionAnimation();

        if (!animation.isPlaying()) {
            startAttackReady(0);
            vy = GameConfig.WING_SPIKER_JUMP_SPEED;
        }
    }

    private void updateAttackInAir(boolean justPressedAttack, TeamInput input) {
        if (isHeldAttackReady(input.wingAttack, justPressedAttack)
                && isBallInAttackBox(input)) {
            if (input.canWingAirSet && canAirSetWith(input)) {
                startAirSettingAnimation();
            } else {
                startAttackSwingAnimation();
            }
        }

        vx = 0;
        applyGravity();

        // 不能直接 animation.update()，否則會跳過 attack hitBox 的關閉判斷。
        updateActionAnimation();

        if (!jumping) {
            startReturnToHome();
        }
    }

    private void startReturnToHome() {
        action = PlayerAction.RUN_RETURN;
        attacking = false;
        blocking = false;
        attackHitBox.disable();
        vx = 0;
        startRunLoopAnimation();
    }

    private void updateReturnToHome() {
        double dx = homeX - x;

        if (Math.abs(dx) <= RETURN_SPEED) {
            x = homeX;
            vx = 0;
            finishAction();
            applyGravity();
            return;
        }

        vx = dx > 0 ? RETURN_SPEED : -RETURN_SPEED;
        startRunLoopAnimation();
        applyGravity();
        animation.update();
    }
    @Override
    public boolean isDefaultHitBoxActive() {
        return (action == PlayerAction.IDLE || action == PlayerAction.DIVE)
                && !jumping
                && !attacking
                && !blocking
                && (diving || Math.abs(vx) < 0.001)
                && Math.abs(vy) < 0.001;
    }
}
