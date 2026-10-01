/*
快攻手 MB 的角色邏輯，依照本次球權是否已完成第一球決定攻擊或攔網。
完成第一球後按動作鍵會起跳準備攻擊，否則進入攔網動畫。
*/
package model.player;

import model.GameConfig;
import model.TeamInput;

public class QuickAttacker extends Player {
    private boolean previousQuickAttack = false;
    private int countedBlockJumps;
    private boolean opponentFirstTouchSeen;
    public final BlockHitBox blockHitBox;

    public QuickAttacker(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
        blockHitBox = new BlockHitBox(this);
    }

    @Override
    public void resetToInitial() {
        super.resetToInitial();
        previousQuickAttack = false;
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
        boolean justPressedQuick = input.quickAttack && !previousQuickAttack;
        if (input.opponentHasFirstRegularTouch) opponentFirstTouchSeen = true;

        vx = 0;

        if (action == PlayerAction.ATTACK_READY || action == PlayerAction.ATTACK_SWING) {
            if (isHeldAttackReady(input.quickAttack, justPressedQuick)
                    && isBallInAttackBox(input)) {
                startAttackSwingAnimation();
            }

            applyGravity();
            updateActionAnimation();
            previousQuickAttack = input.quickAttack;
            return;
        }

        if (action == PlayerAction.BLOCK) {
            applyGravity();
            updateActionAnimation();
            previousQuickAttack = input.quickAttack;
            return;
        }

        if (justPressedQuick) {
            if (input.hasFirstRegularTouch) {
                startAttackReady(0);
                vy = GameConfig.QUICK_ATTACKER_JUMP_SPEED;
            } else {
                startBlockAnimation();
                vy = GameConfig.QUICK_ATTACKER_JUMP_SPEED
                        * (opponentFirstTouchSeen && countedBlockJumps > 0
                                ? GameConfig.MB_REPEAT_BLOCK_JUMP_SPEED_MULTIPLIER : 1.0);
                if (opponentFirstTouchSeen) countedBlockJumps++;
            }
        }

        applyGravity();
        updateActionAnimation();
        previousQuickAttack = input.quickAttack;
    }
    @Override
    public boolean isDefaultHitBoxActive() {
        // MB 目前只有攻擊與攔網判定；攔網改由獨立的 blockHitBox 負責。
        return false;
    }
}
