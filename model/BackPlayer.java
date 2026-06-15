package model;

public class BackPlayer extends Player {
    public BackPlayer(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
    }

    @Override
    public void update(TeamInput input) {
        vx = 0;
        if (input.backLeft) vx -= GameConfig.PLAYER_SPEED;
        if (input.backRight) vx += GameConfig.PLAYER_SPEED;

        if (input.backJump && !jumping) {
            vy = GameConfig.PLAYER_JUMP_SPEED;
            jumping = true;
        }

        if (input.backDive && !jumping) {
            diving = true;
            vx = redSide ? GameConfig.DIVE_SPEED : -GameConfig.DIVE_SPEED;
        }

        applyGravity();
    }
}