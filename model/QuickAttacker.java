/*
快攻手 MB 的角色邏輯，依照球在本場或對場決定攻擊或攔網。
本場按攻擊鍵會起跳準備攻擊，對場按攻擊鍵會進入攔網動畫。
*/
package model;

public class QuickAttacker extends Player {
    private boolean previousQuickAttack = false;

    public QuickAttacker(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
    }

    @Override
    public void update(TeamInput input) {
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
            if (input.ballOnOwnSide) {
                startAttackReady(0);
                // 稍微降低非後排（QuickAttacker）起跳高度
                vy *= GameConfig.NON_BACK_AI_JUMP_MULTIPLIER;
            } else {
                startBlockAnimation();
                // 若剛起跳成為攔網，也降低起跳高度
                vy *= GameConfig.NON_BACK_AI_JUMP_MULTIPLIER;
            }
        }

        applyGravity();
        updateActionAnimation();
        previousQuickAttack = input.quickAttack;
    }
    @Override
    public boolean isDefaultHitBoxActive() {
        return isBlockHitBoxActive();
    }
}