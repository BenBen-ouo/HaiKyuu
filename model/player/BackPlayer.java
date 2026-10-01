/*
後排球員的行為邏輯，包含左右移動、跑步動畫、撲球與後排攻擊流程。
撲球細節交給 DiveController，攻擊動畫細節交給 PlayerActionAnimator。
*/
package model.player;

import model.GameConfig;
import model.TeamInput;

public class BackPlayer extends Player {
    private static final double BACK_ATTACK_AIR_SPEED = 2.8;

    private final DiveController diveController;
    private HitBoxSnapshot defaultHitBox;
    private boolean previousBackAction = false;
    private double jumpAirSpeed;

    public BackPlayer(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
        this.diveController = new DiveController(this);
    }

    @Override
    public void update(TeamInput input) {
        clearAttackAttempt();
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
        attacking = false;

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

    @Override
    public void updateWhileAwaitingAuthority() {
        clearAttackAttempt();
        if (diveController.isActive()) {
            diveController.update(false);
            updateActionAnimation();
            return;
        }

        if (action == PlayerAction.AIR_SETTING && jumping) {
            vx = jumpAirSpeed;
            applyGravity();
            updateActionAnimation();
            return;
        }

        super.updateWhileAwaitingAuthority();
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
                && isBallInAttackBox(input)) {
            if (input.canBackAirSet && canAirSetWith(input)) {
                startAirSettingAnimation();
            } else {
                startAttackSwingAnimation();
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
        if (input.backLeft || input.backRight) {
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
        if (input.backLeft) {
            vx -= GameConfig.PLAYER_SPEED;
        }

        if (input.backRight) {
            vx += GameConfig.PLAYER_SPEED;
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
        double speed = BACK_ATTACK_AIR_SPEED;
        if (redSide ? center >= line : center <= line) {
            double setterCenter = redSide
                    ? GameConfig.NET_X + GameConfig.RED_SETTER_OFFSET_X + imageWidth / 2.0
                    : GameConfig.NET_X + GameConfig.BLUE_SETTER_OFFSET_X + imageWidth / 2.0;
            double attackFront = redSide
                    ? center + (attackHitBox.offsetX + attackHitBox.width - imageWidth / 2.0)
                    : center + (attackHitBox.offsetX - imageWidth / 2.0);
            speed = Math.max(0, Math.min(BACK_ATTACK_AIR_SPEED,
                    (redSide ? setterCenter - attackFront : attackFront - setterCenter)
                            / framesToApex()));
        }
        return directionTowardNet() * speed;
    }

    private int framesToApex() {
        return Math.max(1, (int) Math.ceil(-GameConfig.PLAYER_JUMP_SPEED / GameConfig.GRAVITY) - 1);
    }

    /** 快照還原時保留本次起跳速度，避免 Client 下一幀改回舊速度。 */
    public void syncJumpAirSpeed(double serverVx) {
        jumpAirSpeed = serverVx;
    }
    /**
     * 發球準備時清除舊的撲球／攻擊動作，避免發球鍵沿用上一個 Space 狀態。
     */
    public void prepareForServe() {
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
