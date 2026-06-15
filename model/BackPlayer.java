package model;

public class BackPlayer extends Player {
    private final DiveController diveController;

    public BackPlayer(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
        this.diveController = new DiveController(this);
    }

    @Override
    public void update(TeamInput input) {
        if (diveController.isActive()) {
            diveController.update(input.backDive);
            return;
        }

        vx = 0;
        attacking = input.backJump;

        if (diveController.tryStartForBackPlayer(input)) {
            diveController.update(input.backDive);
            return;
        }

        moveHorizontally(input);
        jump(input);

        diveController.rememberInput(input.backDive);
        applyGravity();
    }

    private void moveHorizontally(TeamInput input) {
        if (input.backLeft) {
            vx -= GameConfig.PLAYER_SPEED;
        }

        if (input.backRight) {
            vx += GameConfig.PLAYER_SPEED;
        }
    }

    private void jump(TeamInput input) {
        if (input.backJump && !jumping) {
            vy = GameConfig.PLAYER_JUMP_SPEED;
            jumping = true;
        }
    }
}