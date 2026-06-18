package model;

public class DiveController {
    private static final int TOTAL_FRAMES = 60;
    private static final double HITBOX_WIDTH = 96;
    private static final double HITBOX_HEIGHT = 16;
    private static final double HITBOX_OFFSET_Y = GameConfig.PLAYER_IMAGE_HEIGHT - HITBOX_HEIGHT - 2;

    private final Player player;

    private int frame = 0;
    private int direction = 1;
    private boolean previousDiveInput = false;

    private double normalHitBoxOffsetX;
    private double normalHitBoxOffsetY;
    private double normalHitBoxWidth;
    private double normalHitBoxHeight;
    private int normalHitBoxArcWidth;
    private int normalHitBoxArcHeight;
    private double normalHitBoxRotationDegrees;

    public DiveController(Player player) {
        this.player = player;
    }

    public boolean isActive() {
        return player.diving;
    }

    public boolean tryStartForBackPlayer(TeamInput input) {
        boolean diveJustPressed = input.backDive && !previousDiveInput;

        if (!diveJustPressed || player.jumping) {
            return false;
        }

        start(getBackPlayerDiveDirection(input));
        return true;
    }

    public void update(boolean currentDiveInput) {
        double remainingRatio = Math.max(0, TOTAL_FRAMES - frame) / (double) TOTAL_FRAMES;

        player.vx = direction * GameConfig.DIVE_SPEED * remainingRatio;
        player.x += player.vx;
        clampXToMovementBounds();

        player.y = GameConfig.FLOOR_Y - player.imageHeight;
        player.vy = 0;
        player.jumping = false;
        player.attacking = false;

        frame++;

        // 碰撞偵測在 Team.update() 後面才發生，
        // 所以這裡用 >，讓第 60 frame 還維持撲接碰撞箱。
        if (frame > TOTAL_FRAMES) {
            end();
        }

        previousDiveInput = currentDiveInput;
    }

    public void rememberInput(boolean currentDiveInput) {
        previousDiveInput = currentDiveInput;
    }

    private void start(int direction) {
        saveNormalHitBox();

        player.diving = true;
        player.attacking = false;
        player.jumping = false;
        player.vy = 0;

        this.frame = 0;
        this.direction = direction;

        setDiveImageDirection();
        player.playDiveAnimation();

        player.y = GameConfig.FLOOR_Y - player.imageHeight;
        setDiveHitBox();
    }

    private void end() {
        player.diving = false;
        player.vx = 0;
        frame = 0;
        restoreNormalHitBox();
    }

    private int getBackPlayerDiveDirection(TeamInput input) {
        // 以「當下按住的左右鍵」決定撲接方向。
        // 紅隊：A 左、D 右。藍隊：← 左、→ 右。
        if (input.backLeft && !input.backRight) {
            return -1;
        }

        if (input.backRight && !input.backLeft) {
            return 1;
        }

        // 沒按方向時保留原本預設：紅隊往右，藍隊往左。
        return player.redSide ? 1 : -1;
    }

    private void setDiveImageDirection() {
        // 預設素材方向：player 1 面右，player 2 面左。
        // 往預設相反方向撲接時，Renderer 會把圖片水平反轉。
        player.mirrorImage = player.redSide ? direction < 0 : direction > 0;
    }

    private void setDiveHitBox() {
        double offsetX;

        if (direction > 0) {
            // 往右撲：碰撞箱從身體往右前方延伸。
            offsetX = 6;
        } else {
            // 往左撲：碰撞箱從身體往左前方延伸。
            offsetX = player.imageWidth - 6 - HITBOX_WIDTH;
        }

        player.hitBox.set(
                offsetX,
                HITBOX_OFFSET_Y,
                HITBOX_WIDTH,
                HITBOX_HEIGHT,
                0,
                0,
                0
        );
    }

    private void saveNormalHitBox() {
        normalHitBoxOffsetX = player.hitBox.offsetX;
        normalHitBoxOffsetY = player.hitBox.offsetY;
        normalHitBoxWidth = player.hitBox.width;
        normalHitBoxHeight = player.hitBox.height;
        normalHitBoxArcWidth = player.hitBox.arcWidth;
        normalHitBoxArcHeight = player.hitBox.arcHeight;
        normalHitBoxRotationDegrees = player.hitBox.rotationDegrees;
    }

    private void restoreNormalHitBox() {
        player.hitBox.set(
                normalHitBoxOffsetX,
                normalHitBoxOffsetY,
                normalHitBoxWidth,
                normalHitBoxHeight,
                normalHitBoxArcWidth,
                normalHitBoxArcHeight,
                normalHitBoxRotationDegrees
        );
    }

    private void clampXToMovementBounds() {
        if (player.x < player.minX) {
            player.x = player.minX;
        }

        if (player.x + player.imageWidth > player.maxX) {
            player.x = player.maxX - player.imageWidth;
        }
    }
}