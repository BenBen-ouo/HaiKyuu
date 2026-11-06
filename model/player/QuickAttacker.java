/*
快攻手 MB 的角色邏輯，依照本次球權是否已完成第一球決定攻擊或攔網。
完成第一球後按動作鍵會起跳準備攻擊，否則進入攔網動畫。
*/
package model.player;

import model.GameConfig;
import model.TeamInput;

public final class QuickAttacker extends Player {
    private boolean previousMbActionPressed = false;
    private int countedBlockJumps;
    private boolean opponentFirstTouchSeen;
    public final BlockHitBox blockHitBox;
    public final CircleHitBox headBlockHitBox;

    public QuickAttacker(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
        blockHitBox = new BlockHitBox(this);
        headBlockHitBox = new CircleHitBox(this);
    }

    @Override
    public void resetToInitial() {
        super.resetToInitial();
        previousMbActionPressed = false;
        resetBlockJumpCount();
    }

    public void resetBlockJumpCount() {
        countedBlockJumps = 0;
        opponentFirstTouchSeen = false;
    }

    /** 對手在新球權接起第一顆一般球，攔網起跳從第一次重新計算。 */
    public void beginOpponentReceiveCycle() {
        countedBlockJumps = 0;
        opponentFirstTouchSeen = true;
    }

    public int getCountedBlockJumps() {
        return countedBlockJumps;
    }

    public boolean hasSeenOpponentFirstTouch() {
        return opponentFirstTouchSeen;
    }

    public void applyBlockJumpState(int count, boolean firstTouchSeen) {
        countedBlockJumps = Math.max(0, count);
        opponentFirstTouchSeen = firstTouchSeen;
    }

    @Override
    public void update(TeamInput input) {
        clearAttackAttempt();
        boolean justPressedQuick = input.mbActionPressed && !previousMbActionPressed;
        if (input.opponentHasFirstRegularTouch) opponentFirstTouchSeen = true;

        vx = 0;

        if (action == PlayerAction.ATTACK_READY || action == PlayerAction.ATTACK_SWING) {
            if (isHeldAttackReady(input.mbActionPressed, justPressedQuick)
                    && input.canResolveAttack && !input.attackLocked && input.lastHitter != this
                    && isBallInAttackBox(input)) {
                queueAttackAttempt();
            }

            applyGravity();
            updateActionAnimation();
            previousMbActionPressed = input.mbActionPressed;
            return;
        }

        if (action == PlayerAction.BLOCK) {
            applyGravity();
            updateActionAnimation();
            previousMbActionPressed = input.mbActionPressed;
            return;
        }

        if (justPressedQuick) {
            if (input.hasFirstRegularTouch) {
                startAttackReady(0);
                vy = GameConfig.MB_JUMP_VELOCITY_Y;
            } else {
                startBlockAnimation();
                vy = GameConfig.MB_JUMP_VELOCITY_Y
                        * (opponentFirstTouchSeen && countedBlockJumps > 0
                                ? GameConfig.MB_REPEAT_BLOCK_JUMP_SPEED_MULTIPLIER : 1.0);
                if (opponentFirstTouchSeen) countedBlockJumps++;
            }
        }

        applyGravity();
        updateActionAnimation();
        if (!jumping && action == PlayerAction.IDLE) {
            animation.show(teamAsset(input.hasFirstRegularTouch ? "MB ready attack" : "MB"));
        }
        previousMbActionPressed = input.mbActionPressed;
    }

    public boolean isHeadBlockHitBoxActive() {
        return jumping && (getBlockPhase() == BlockPhase.RAISING || getBlockPhase() == BlockPhase.RETRACTING);
    }

    public HitBox activeBlockHitBox() {
        return isBlockHitBoxActive() ? blockHitBox
                : isHeadBlockHitBoxActive() ? headBlockHitBox : null;
    }
    @Override
    public boolean isDefaultHitBoxActive() {
        // MB 目前只有攻擊與攔網判定；攔網改由獨立的 blockHitBox 負責。
        return false;
    }
}
