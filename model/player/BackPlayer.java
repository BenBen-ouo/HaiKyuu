/*
後排球員的行為邏輯，包含左右移動、跑步動畫、撲球與後排攻擊流程。
撲球細節交給 DiveController，攻擊動畫細節交給 PlayerActionAnimator。
*/
package model.player;

import model.GameConfig;
import model.TeamInput;

public class BackPlayer extends Player {
    private static final double BACK_JUMP_MAX_HORIZONTAL_SPEED = 2.8;

    private final DiveController diveController;
    private HitBoxSnapshot defaultHitBox;
    private boolean previousBackAction = false;
    private double jumpAirSpeed;
    private int runDirection;
    private int runHeldFrames;

    public BackPlayer(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
        this.diveController = new DiveController(this);
    }

    @Override
    public void update(TeamInput input) {
        clearAttackAttempt();
        int direction = input.backLeft == input.backRight ? 0 : input.backLeft ? -1 : 1;
        boolean canRun = !jumping && !diving
                && (action == PlayerAction.IDLE || action == PlayerAction.RUN_LOOP)
                && !input.backJump && !input.backDive;
        runHeldFrames = canRun && direction != 0
                ? (direction == runDirection
                        ? Math.min(runHeldFrames + 1, GameConfig.BACK_RUN_FOURTH_STAGE_END_FRAMES + 1) : 1) : 0;
        runDirection = runHeldFrames == 0 ? 0 : direction;
        boolean actionPressed = input.backJump || input.backDive;
        boolean justPressedAction = actionPressed && !previousBackAction;

        if (diveController.isActive()) {
            diveController.update(actionPressed);
            updateActionAnimation();
            previousBackAction = actionPressed;
            return;
        }

        if (isMovementLockedByAnimation()) {
            vx = 0;
            applyGravity();
            updateActionAnimation();
            diveController.rememberInput(input.backDive);
            previousBackAction = actionPressed;
            return;
        }

        if (action == PlayerAction.ATTACK_READY || action == PlayerAction.ATTACK_SWING
                || action == PlayerAction.AIR_SETTING) {
            updateBackAttack(justPressedAction, input);
            previousBackAction = actionPressed;
            return;
        }

        if (action == PlayerAction.RUN_LOOP) {
            if (tryStartPriorityAction(input, justPressedAction)) {
                previousBackAction = actionPressed;
                return;
            }

            updateNormalRun(input);
            diveController.rememberInput(input.backDive);
            previousBackAction = actionPressed;
            return;
        }

        vx = 0;

        if (input.backJump && justPressedAction && !jumping) {
            startBackAttack();
        } else if (diveController.tryStartForBackPlayer(input)) {
            diveController.update(input.backDive);
            updateActionAnimation();
            previousBackAction = actionPressed;
            return;
        } else {
            moveHorizontallyWithRunAnimation(input);
        }

        diveController.rememberInput(input.backDive);
        applyGravity();
        updateActionAnimation();
        previousBackAction = actionPressed;
    }

    /** Team 設定一般 hitBox 後呼叫，保存此角色的固定站立碰撞框。 */
    public void captureDefaultHitBox() {
        defaultHitBox = HitBoxSnapshot.capture(hitBox);
    }

    /** 撲球被取消、結束或進入發球準備時，強制恢復一般站立碰撞框。 */
    public void restoreDefaultHitBox() {
        if (defaultHitBox != null) {
            defaultHitBox.restoreTo(hitBox);
        }
    }

    private boolean tryStartPriorityAction(TeamInput input, boolean justPressedAction) {
        if (input.backJump && justPressedAction && !jumping) {
            startBackAttack();
            diveController.rememberInput(input.backDive);
            applyGravity();
            updateActionAnimation();
            return true;
        }

        if (diveController.tryStartForBackPlayer(input)) {
            diveController.update(input.backDive);
            updateActionAnimation();
            return true;
        }

        return false;
    }

    private void updateBackAttack(boolean justPressedAction, TeamInput input) {
        if (isHeldAttackReady(input.backJump || input.backDive, justPressedAction)
                && input.lastHitter != this
                && isBallInAttackBox(input)) {
            if (input.canResolveAttack && input.canBackAirSet && canAirSetWith(input)) {
                startAirSettingAnimation();
            } else if (input.canResolveAttack && !input.attackLocked) {
                queueAttackAttempt();
            }
        }

        if (jumping) {
            vx = jumpAirSpeed;
        } else {
            vx = 0;
        }

        applyGravity();
        updateActionAnimation();
    }

    private void updateNormalRun(TeamInput input) {
        if (input.backLeft != input.backRight) {
            vx = 0;
            moveHorizontally(input);
            startRunLoopAnimation();
            applyGravity();
            animation.update();
        } else {
            vx = 0;
            finishAction();
            applyGravity();
        }
    }

    private void moveHorizontallyWithRunAnimation(TeamInput input) {
        moveHorizontally(input);

        if (vx != 0) {
            action = PlayerAction.RUN_LOOP;
            startRunLoopAnimation();
        }
    }

    private void moveHorizontally(TeamInput input) {
        double speed;
        if (runHeldFrames <= GameConfig.BACK_RUN_FIRST_STAGE_END_FRAMES) speed = GameConfig.BACK_RUN_START_SPEED;
        else if (runHeldFrames <= GameConfig.BACK_RUN_SECOND_STAGE_END_FRAMES) speed = GameConfig.BACK_RUN_SECOND_STAGE_SPEED;
        else if (runHeldFrames <= GameConfig.BACK_RUN_THIRD_STAGE_END_FRAMES) speed = GameConfig.BACK_RUN_THIRD_STAGE_SPEED;
        else if (runHeldFrames <= GameConfig.BACK_RUN_FOURTH_STAGE_END_FRAMES) speed = GameConfig.BACK_RUN_FOURTH_STAGE_SPEED;
        else speed = GameConfig.BACK_RUN_MAX_SPEED;
        if (input.backLeft) {
            vx -= speed;
        }

        if (input.backRight) {
            vx += speed;
        }
    }

    private void startBackAttack() {
        jumpAirSpeed = plannedJumpAirSpeed();
        startAttackReady(jumpAirSpeed);
    }

    /** 發球拋球也使用同一份起跳預測，避免漏算攻擊框相對圖片中心的位移。 */
    public double plannedAttackHitBoxCenterAtApex() {
        return attackHitBox.getCenterX() + plannedJumpAirSpeed() * framesToApex();
    }

    private double plannedJumpAirSpeed() {
        // 起跳中心若已在線上或靠網側，讓攻擊框的前緣在最高點對齊 Setter 中心。
        // 線後方維持原上限；速度只在起跳時決定，空中不重新計算。
        double center = x + imageWidth / 2.0;
        double line = GameConfig.NET_X + (redSide ? -1 : 1) * GameConfig.THREE_METER_PX;
        double speed = BACK_JUMP_MAX_HORIZONTAL_SPEED;
        if (redSide ? center >= line : center <= line) {
            double setterCenter = redSide
                    ? GameConfig.NET_X + GameConfig.RED_SETTER_OFFSET_X + imageWidth / 2.0
                    : GameConfig.NET_X + GameConfig.BLUE_SETTER_OFFSET_X + imageWidth / 2.0;
            double attackFront = redSide
                    ? center + (attackHitBox.offsetX + attackHitBox.width - imageWidth / 2.0)
                    : center + (attackHitBox.offsetX - imageWidth / 2.0);
            speed = Math.max(0, Math.min(BACK_JUMP_MAX_HORIZONTAL_SPEED,
                    (redSide ? setterCenter - attackFront : attackFront - setterCenter)
                            / framesToApex()));
        }
        return directionTowardNet() * speed;
    }

    private int framesToApex() {
        return Math.max(1, (int) Math.ceil(-GameConfig.BACK_JUMP_VELOCITY_Y / GameConfig.GRAVITY) - 1);
    }

    /** 快照還原時保留本次起跳速度，避免 Client 下一幀改回舊速度。 */
    public void syncJumpAirSpeed(double serverVx) {
        jumpAirSpeed = serverVx;
    }
    /**
     * 發球準備時清除舊的撲球／攻擊動作，避免發球鍵沿用上一個 Space 狀態。
     */
    public void prepareForServe() {
        runDirection = 0;
        runHeldFrames = 0;
        diveController.cancel();
        restoreDefaultHitBox();
        previousBackAction = false;
        jumpAirSpeed = 0;
        PlayerPhysics.clearMotionAndActions(this);
        finishAction();
        attackHitBox.disable();
    }

    @Override
    public void resetToInitial() {
        runDirection = 0;
        runHeldFrames = 0;
        diveController.cancel();
        super.resetToInitial();
        restoreDefaultHitBox();
        previousBackAction = false;
        jumpAirSpeed = 0;
    }

    @Override
    public boolean isDefaultHitBoxActive() {
        if (diving || action == PlayerAction.DIVE) {
            return true;
        }

        return !jumping
                && action != PlayerAction.ATTACK_READY
                && action != PlayerAction.ATTACK_SWING;
    }
}
