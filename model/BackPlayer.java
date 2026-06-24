/*
後排球員的行為邏輯，包含左右移動、跑步動畫、撲球與後排攻擊流程。
撲球細節交給 DiveController，攻擊動畫細節交給 PlayerActionAnimator。
*/
package model;

public class BackPlayer extends Player {
    private static final double BACK_ATTACK_AIR_SPEED = 3.0;

    private final DiveController diveController;
    private boolean previousBackAction = false;

    public BackPlayer(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
        this.diveController = new DiveController(this);
    }

    @Override
    public void update(TeamInput input) {
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

        if (action == PlayerAction.ATTACK_READY || action == PlayerAction.ATTACK_SWING) {
            updateBackAttack(justPressedAction);
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
            startAttackReady(directionTowardNet() * BACK_ATTACK_AIR_SPEED);
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

    private boolean tryStartPriorityAction(TeamInput input, boolean justPressedAction) {
        if (input.backJump && justPressedAction && !jumping) {
            startAttackReady(directionTowardNet() * BACK_ATTACK_AIR_SPEED);
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

    private void updateBackAttack(boolean justPressedAction) {
        if (action == PlayerAction.ATTACK_READY && justPressedAction && jumping) {
            startAttackSwingAnimation();
        }

        if (jumping) {
            vx = directionTowardNet() * BACK_ATTACK_AIR_SPEED;
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