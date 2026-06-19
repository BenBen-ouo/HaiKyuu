package model;

public class Setter extends Player {
    public Setter(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
    }

    @Override
    public void update(TeamInput input) {
        vx = 0;

        if (input.setterJump && !jumping) {
            vy = GameConfig.PLAYER_JUMP_SPEED * 0.92;
            jumping = true;
        }

        applyGravity();
        updateActionAnimation();
    }
}