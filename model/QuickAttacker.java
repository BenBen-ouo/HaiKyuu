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
            } else {
                startBlockAnimation();
            }
        }

        applyGravity();
        updateActionAnimation();
        previousQuickAttack = input.quickAttack;
    }
}