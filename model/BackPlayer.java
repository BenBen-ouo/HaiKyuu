package model;

public class BackPlayer extends Player {
    private static final int DIVE_TOTAL_FRAMES = 60;
    private static final double DIVE_HITBOX_WIDTH = 96;
    private static final double DIVE_HITBOX_HEIGHT = 16;
    private static final double DIVE_HITBOX_OFFSET_Y = GameConfig.PLAYER_IMAGE_HEIGHT - DIVE_HITBOX_HEIGHT - 2;

    private int diveFrame = 0;
    private int diveDirection = 1;
    private boolean previousDiveInput = false;

    private double normalHitBoxOffsetX;
    private double normalHitBoxOffsetY;
    private double normalHitBoxWidth;
    private double normalHitBoxHeight;
    private int normalHitBoxArcWidth;
    private int normalHitBoxArcHeight;
    private double normalHitBoxRotationDegrees;

    public BackPlayer(String assetName, double x, double y, boolean redSide) {
        super(assetName, x, y, redSide);
    }

    @Override
    public void update(TeamInput input) {
        if (diving) {
            updateDive(input.backDive);
            return;
        }

        vx = 0;
        attacking = input.backJump;

        boolean diveJustPressed = input.backDive && !previousDiveInput;
        if (diveJustPressed && !jumping) {
            startDive(input);
            updateDive(input.backDive);
            return;
        }

        if (input.backLeft) vx -= GameConfig.PLAYER_SPEED;
        if (input.backRight) vx += GameConfig.PLAYER_SPEED;

        if (input.backJump && !jumping) {
            vy = GameConfig.PLAYER_JUMP_SPEED;
            jumping = true;
        }

        previousDiveInput = input.backDive;
        applyGravity();
    }

    private void startDive(TeamInput input) {
        saveNormalHitBox();

        diving = true;
        attacking = false;
        jumping = false;
        vy = 0;
        diveFrame = 0;

        if (redSide) {
            // 紅隊：A + Space 往左撲，其餘預設往右撲
            diveDirection = input.backLeft ? -1 : 1;
        } else {
            // 藍隊：Right + 0 往右撲，其餘預設往左撲
            diveDirection = input.backRight ? 1 : -1;
        }

        y = GameConfig.FLOOR_Y - imageHeight;
        setDiveHitBox();
    }

    private void updateDive(boolean currentDiveInput) {
        double remainingRatio = Math.max(0, DIVE_TOTAL_FRAMES - diveFrame) / (double) DIVE_TOTAL_FRAMES;

        vx = diveDirection * GameConfig.DIVE_SPEED * (remainingRatio - 0.2); // 最後 20% 速度急劇下降，模擬撲接後的減速效果
        x += vx;
        clampXToMovementBounds();

        y = GameConfig.FLOOR_Y - imageHeight;
        vy = 0;
        jumping = false;
        attacking = false;

        diveFrame++;

        // 碰撞偵測在 Team.update() 後面才發生，
        // 所以這裡用 >，讓第 60 frame 還維持撲接碰撞箱。
        if (diveFrame > DIVE_TOTAL_FRAMES) {
            endDive();
        }

        previousDiveInput = currentDiveInput;
    }

    private void endDive() {
        diving = false;
        vx = 0;
        diveFrame = 0;
        restoreNormalHitBox();
    }

    private void setDiveHitBox() {
        double offsetX;

        if (diveDirection > 0) {
            // 往右撲：碰撞箱從身體往右前方延伸
            offsetX = 6;
        } else {
            // 往左撲：碰撞箱從身體往左前方延伸
            offsetX = imageWidth - 6 - DIVE_HITBOX_WIDTH;
        }

        hitBox.set(
                offsetX,
                DIVE_HITBOX_OFFSET_Y,
                DIVE_HITBOX_WIDTH,
                DIVE_HITBOX_HEIGHT,
                0,
                0,
                0
        );
    }

    private void saveNormalHitBox() {
        normalHitBoxOffsetX = hitBox.offsetX;
        normalHitBoxOffsetY = hitBox.offsetY;
        normalHitBoxWidth = hitBox.width;
        normalHitBoxHeight = hitBox.height;
        normalHitBoxArcWidth = hitBox.arcWidth;
        normalHitBoxArcHeight = hitBox.arcHeight;
        normalHitBoxRotationDegrees = hitBox.rotationDegrees;
    }

    private void restoreNormalHitBox() {
        hitBox.set(
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
        if (x < minX) {
            x = minX;
        }

        if (x + imageWidth > maxX) {
            x = maxX - imageWidth;
        }
    }
}