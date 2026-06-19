/*
主攻手 WS 的角色邏輯，包含助跑、起跳、空中攻擊揮臂與落地回位。
目前只處理攻擊動畫流程，真正扣球改變球速之後再從碰撞邏輯接入。
*/
package model;

public class WingSpiker extends Player {
    private static final double APPROACH_SPEED = 6.5;
    private static final double RETURN_SPEED = 6.5;

    private final double homeX;
    private boolean previousWingAttack = false;

    public WingSpiker(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
        this.homeX = x;
    }

    @Override
    public void update(TeamInput input) {
        boolean justPressedAttack = input.wingAttack && !previousWingAttack;

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

        if (action == PlayerAction.ATTACK_READY || action == PlayerAction.ATTACK_SWING) {
            updateAttackInAir(justPressedAttack);
            previousWingAttack = input.wingAttack;
            return;
        }

        if (action == PlayerAction.RUN_RETURN) {
            updateReturnToHome();
            previousWingAttack = input.wingAttack;
            return;
        }

        vx = 0;

        if (justPressedAttack && input.ballOnOwnSide) {
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
        }
    }

    private void updateAttackInAir(boolean justPressedAttack) {
        if (action == PlayerAction.ATTACK_READY && justPressedAttack && jumping) {
            startAttackSwingAnimation();
        }

        vx = 0;
        applyGravity();
        animation.update();

        if (!jumping) {
            startReturnToHome();
        }
    }

    private void startReturnToHome() {
        action = PlayerAction.RUN_RETURN;
        attacking = false;
        blocking = false;
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
}