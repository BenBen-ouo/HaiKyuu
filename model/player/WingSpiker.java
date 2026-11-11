/*
主攻手 WS 的角色邏輯，包含助跑、起跳、空中攻擊揮臂與落地回位。
成功碰球與球路由 RallyContactHandler 判定；角色只提出當幀請求。
*/
package model.player;

import model.GameConfig;
import model.TeamInput;

public final class WingSpiker extends Player {
    private static final double APPROACH_SPEED = 8.0;
    private static final double RETURN_SPEED = 6.5;
    private static final int APPROACH_CYCLES = 2;

    public static int approachDurationFrames() { return AnimationSequences.runDurationFrames(APPROACH_CYCLES); }
    // 啟動動畫的當幀尚未水平移動；其後才逐幀助跑。
    public static double approachDistance() { return APPROACH_SPEED * (approachDurationFrames() - 1); }

    private final double homeX;
    private final DiveController diveController;
    private boolean previousWingActionPressed = false;
    private boolean resumeReturnAfterReceive;

    public WingSpiker(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
        this.homeX = x;
        this.diveController = new DiveController(this);
    }

    @Override
    public void resetToInitial() {
        super.resetToInitial();
        diveController.cancel();
        previousWingActionPressed = false;
        resumeReturnAfterReceive = false;
    }

    @Override
    public void update(TeamInput input) {
        clearAttackAttempt();
        boolean justPressedAttack = input.wingActionPressed && !previousWingActionPressed;

        if (diveController.isActive()) {
            diveController.update(input.wingActionPressed);
            updateActionAnimation();
            if (!diveController.isActive()) {
                startReturnToHome();
            }
            previousWingActionPressed = input.wingActionPressed;
            return;
        }

        if (isMovementLockedByAnimation()) {
            vx = 0;
            applyGravity();
            updateActionAnimation();
            if (resumeReturnAfterReceive && !isMovementLockedByAnimation()) {
                resumeReturnAfterReceive = false;
                startReturnToHome();
            }
            previousWingActionPressed = input.wingActionPressed;
            return;
        }

        if (action == PlayerAction.RUN_APPROACH) {
            updateApproachRun(input);
            previousWingActionPressed = input.wingActionPressed;
            return;
        }

        if (action == PlayerAction.ATTACK_READY || action == PlayerAction.ATTACK_SWING
                || action == PlayerAction.AIR_SETTING) {
            updateAttackInAir(justPressedAttack, input);
            previousWingActionPressed = input.wingActionPressed;
            return;
        }

        if (action == PlayerAction.RUN_RETURN) {
            updateReturnToHome();
            previousWingActionPressed = input.wingActionPressed;
            return;
        }

        vx = 0;

        if (justPressedAttack && !input.hasFirstRegularTouch
                && diveController.tryStartTowardNet(true)) {
            diveController.update(input.wingActionPressed);
            updateActionAnimation();
            previousWingActionPressed = input.wingActionPressed;
            return;
        } else if (justPressedAttack) {
            startRunApproachAnimation(APPROACH_CYCLES);
        }

        applyGravity();
        updateActionAnimation();
        previousWingActionPressed = input.wingActionPressed;
    }

    private void updateApproachRun(TeamInput input) {
        vx = directionTowardNet() * APPROACH_SPEED;
        applyGravity();
        updateActionAnimation();

        if (!animation.isPlaying()) {
            startAttackReady(0);
            vy = GameConfig.WS_JUMP_VELOCITY_Y;
        }
    }

    private void updateAttackInAir(boolean justPressedAttack, TeamInput input) {
        if (isHeldAttackReady(input.wingActionPressed, justPressedAttack)
                && input.lastHitter != this
                && isBallInAttackBox(input)) {
            if (input.canResolveAttack && input.canWingAirSet && canAirSetWith(input)) {
                startAirSettingAnimation();
            } else if (input.canResolveAttack && !input.attackLocked) {
                queueAttackAttempt();
            }
        }

        vx = 0;
        applyGravity();

        // 不能直接 animation.update()，否則會跳過 attack hitBox 的關閉判斷。
        updateActionAnimation();

        if (!jumping && action != PlayerAction.ATTACK_SWING) {
            startReturnToHome();
        }
    }

    private void startReturnToHome() {
        action = PlayerAction.RUN_RETURN;
        attackHitBox.disable();
        vx = 0;
        startRunLoopAnimation();
    }

    @Override
    public void playReceiveAnimation() {
        resumeReturnAfterReceive = action == PlayerAction.RUN_RETURN;
        super.playReceiveAnimation();
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
        return (action == PlayerAction.IDLE || action == PlayerAction.DIVE || action == PlayerAction.RUN_RETURN)
                && !jumping
                && (diving || action == PlayerAction.RUN_RETURN || Math.abs(vx) < 0.001)
                && Math.abs(vy) < 0.001;
    }
}
