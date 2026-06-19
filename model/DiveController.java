package model;

public class DiveController {
    private static final int TOTAL_FRAMES = 60;
    private static final double HITBOX_WIDTH = 80;
    private static final double HITBOX_HEIGHT = 5;
    private static final double HITBOX_OFFSET_Y = GameConfig.PLAYER_IMAGE_HEIGHT - HITBOX_HEIGHT - 2;
    private static final double HITBOX_FORWARD_OFFSET_X = 20;

    private final Player player;

    private int elapsedFrames = 0;
    private int diveDirection = 1;
    private boolean previousDiveInput = false;
    private HitBoxSnapshot standingHitBox;

    public DiveController(Player player) {
        this.player = player;
    }

    public boolean isActive() {
        return player.diving;
    }

    public boolean tryStartForBackPlayer(TeamInput input) {
        if (!isDiveJustPressed(input) || player.jumping) {
            return false;
        }

        startDive(SideRules.horizontalDirectionFromBackInput(input, player.redSide));
        return true;
    }

    public void update(boolean currentDiveInput) {
        slideWithDeceleration();
        keepPlayerOnGround();
        elapsedFrames++;

        if (elapsedFrames > TOTAL_FRAMES) {
            endDive();
        }

        previousDiveInput = currentDiveInput;
    }

    public void rememberInput(boolean currentDiveInput) {
        previousDiveInput = currentDiveInput;
    }

    private boolean isDiveJustPressed(TeamInput input) {
        return input.backDive && !previousDiveInput;
    }

    private void startDive(int direction) {
        standingHitBox = HitBoxSnapshot.capture(player.hitBox);
        elapsedFrames = 0;
        diveDirection = direction;

        player.diving = true;
        player.attacking = false;
        player.jumping = false;
        player.vy = 0;
        player.y = GameConfig.FLOOR_Y - player.imageHeight;
        player.mirrorImage = SideRules.shouldMirrorImage(player.redSide, diveDirection);

        player.playDiveAnimation();
        applyDiveHitBox();
    }

    private void slideWithDeceleration() {
        double remainingRatio = Math.max(0, TOTAL_FRAMES - elapsedFrames) / (double) TOTAL_FRAMES;
        player.vx = diveDirection * GameConfig.DIVE_SPEED * remainingRatio;
        player.x += player.vx;
        clampPlayerX();
    }

    private void keepPlayerOnGround() {
        player.y = GameConfig.FLOOR_Y - player.imageHeight;
        player.vy = 0;
        player.jumping = false;
        player.attacking = false;
    }

    private void endDive() {
        player.diving = false;
        player.vx = 0;
        elapsedFrames = 0;

        if (standingHitBox != null) {
            standingHitBox.restoreTo(player.hitBox);
        }
    }

    private void applyDiveHitBox() {
        player.hitBox.set(
                diveHitBoxOffsetX(),
                HITBOX_OFFSET_Y,
                HITBOX_WIDTH,
                HITBOX_HEIGHT,
                5,
                5,
                0
        );
    }

    private double diveHitBoxOffsetX() {
        if (diveDirection > 0) {
            return HITBOX_FORWARD_OFFSET_X;
        }

        return player.imageWidth - HITBOX_FORWARD_OFFSET_X - HITBOX_WIDTH;
    }

    private void clampPlayerX() {
        if (player.x < player.minX) {
            player.x = player.minX;
        }

        if (player.x + player.imageWidth > player.maxX) {
            player.x = player.maxX - player.imageWidth;
        }
    }
}