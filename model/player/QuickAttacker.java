/*
快攻手 MB 的角色邏輯，依照本次球權是否已完成第一球決定攻擊或攔網。
完成第一球後按動作鍵會起跳準備攻擊，否則進入攔網動畫。
*/
package model.player;

import model.GameConfig;
import model.TeamInput;

public class QuickAttacker extends Player {
    private boolean previousQuickAttack = false;
    public final BlockHitBox blockHitBox;

    public QuickAttacker(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
        blockHitBox = new BlockHitBox(this);
    }

    @Override
    public void resetToInitial() {
        super.resetToInitial();
        previousQuickAttack = false;
    }

    @Override
    public void update(TeamInput input) {
        clearAttackAttempt();
        boolean justPressedQuick = input.quickAttack && !previousQuickAttack;

        vx = 0;

        if (action == PlayerAction.ATTACK_READY || action == PlayerAction.ATTACK_SWING) {
            if (action == PlayerAction.ATTACK_READY && justPressedQuick && jumping) {
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
            } else {
                startBlockAnimation();
            }
            vy = GameConfig.QUICK_ATTACKER_JUMP_SPEED;
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
