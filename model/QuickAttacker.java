package model;

public class QuickAttacker extends Player {
    public QuickAttacker(String assetName, double x, double y) {
        super(assetName, x, y);
    }

    public void update(TeamInput input) {
        vx = 0;
        attacking = input.quickAttack;
        blocking = input.quickBlock;

        if ((input.quickAttack || input.quickBlock) && !jumping) {
            vy = GameConfig.PLAYER_JUMP_SPEED;
            jumping = true;
        }

        applyGravity();
    }
}
