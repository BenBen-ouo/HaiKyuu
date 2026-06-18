package model;

public abstract class Player {
    public String assetName;

    // 圖片左上角位置
    public double x;
    public double y;

    public double vx;
    public double vy;

    // 圖片顯示大小
    public int imageWidth = GameConfig.PLAYER_IMAGE_WIDTH;
    public int imageHeight = GameConfig.PLAYER_IMAGE_HEIGHT;

    // 每個球員自己的碰撞箱
    public HitBox hitBox;

    public boolean jumping;
    public boolean attacking;
    public boolean blocking;
    public boolean diving;

    public boolean redSide;

    // true 時，Renderer 會把目前圖片水平左右反轉。主要用在 back 往反方向撲接。
    public boolean mirrorImage = false;

    protected final PlayerAnimation animation;
    protected PlayerAction action = PlayerAction.IDLE;

    public Player(String assetName, double x, double y, boolean redSide) {
        this.assetName = assetName;
        this.x = x;
        this.y = y;
        this.redSide = redSide;
        this.hitBox = new HitBox(this);
        this.animation = new PlayerAnimation(this, assetName);
    }

    public abstract void update(TeamInput input);

    // 運動邊界限制
    public double minX = GameConfig.WORLD_LEFT;
    public double maxX = GameConfig.WORLD_RIGHT;

    public void applyGravity() {
        vy += GameConfig.GRAVITY;
        x += vx;
        y += vy;

        // 用圖片底部判斷是否碰到地板
        if (y + imageHeight > GameConfig.FLOOR_Y) {
            y = GameConfig.FLOOR_Y - imageHeight;
            vy = 0;
            jumping = false;
            diving = false;
        }

        // 限制移動邊界
        if (x < minX) {
            x = minX;
        }

        if (x + imageWidth > maxX) {
            x = maxX - imageWidth;
        }
    }

    public boolean intersectsBall(Ball ball) {
        return hitBox.intersectsBall(ball);
    }

    public double getHitBoxCenterX() {
        return hitBox.getCenterX();
    }

    public double getHitBoxCenterY() {
        return hitBox.getCenterY();
    }

    public boolean isAttackReady() {
        return action == PlayerAction.ATTACK_READY;
    }

    public boolean isAttackSwinging() {
        return action == PlayerAction.ATTACK_SWING;
    }

    public boolean isReceiving() {
        return action == PlayerAction.RECEIVING;
    }

    public boolean isSetting() {
        return action == PlayerAction.SETTING;
    }

    public boolean isMovementLockedByAnimation() {
        return action == PlayerAction.RECEIVING;
    }

    public void playReceiveAnimation() {
        if (action == PlayerAction.DIVE
                || action == PlayerAction.ATTACK_READY
                || action == PlayerAction.ATTACK_SWING
                || action == PlayerAction.BLOCK) {
            return;
        }

        mirrorImage = false;
        action = PlayerAction.RECEIVING;
        attacking = false;
        blocking = false;
        vx = 0;
        animation.play(new String[]{teamAsset("receive")}, new int[]{30});
    }

    public void playSettingAnimation() {
        mirrorImage = false;
        action = PlayerAction.SETTING;
        attacking = false;
        blocking = false;

        // 目前素材有 setting1 / setting2，所以中間長停用 setting2。
        animation.play(
                new String[]{teamAsset("setting1"), teamAsset("setting2"), teamAsset("setting1")},
                new int[]{5, 30, 5}
        );
    }

    public void playDiveAnimation() {
        action = PlayerAction.DIVE;
        attacking = false;
        blocking = false;
        animation.play(
                new String[]{teamAsset("dive1"), teamAsset("dive2"), teamAsset("dive3")},
                new int[]{5, 10, -1}
        );
    }

    protected void startAttackReady(double horizontalSpeed) {
        mirrorImage = false;
        action = PlayerAction.ATTACK_READY;
        attacking = true;
        blocking = false;
        diving = false;
        vx = horizontalSpeed;
        vy = GameConfig.PLAYER_JUMP_SPEED;
        jumping = true;
        animation.show(teamAsset("attack1"));
    }

    protected void startAttackSwingAnimation() {
        mirrorImage = false;
        action = PlayerAction.ATTACK_SWING;
        attacking = true;
        blocking = false;
        animation.play(
                new String[]{teamAsset("attack2"), teamAsset("attack3")},
                new int[]{5, -1}
        );
    }

    protected void startBlockAnimation() {
        mirrorImage = false;
        action = PlayerAction.BLOCK;
        blocking = true;
        attacking = false;
        diving = false;

        if (!jumping) {
            vy = GameConfig.PLAYER_JUMP_SPEED;
            jumping = true;
        }

        animation.play(
                new String[]{teamAsset("block1"), teamAsset("block2"), teamAsset("block1")},
                new int[]{20, 20, -1}
        );
    }

    protected void startRunApproachAnimation(int cycles) {
        mirrorImage = false;
        action = PlayerAction.RUN_APPROACH;
        attacking = false;
        blocking = false;
        diving = false;
        playRunAnimationCycles(cycles);
    }

    protected void startRunLoopAnimation() {
        if (animation.isLooping()
                && (action == PlayerAction.RUN_LOOP || action == PlayerAction.RUN_RETURN)) {
            return;
        }

        playRunAnimationLoop();
    }

    private void playRunAnimationCycles(int cycles) {
        int safeCycles = Math.max(1, cycles);
        String[] oneCycleFrames = getRunCycleFrames();
        int[] oneCycleDurations = getRunCycleDurations();
        String[] frames = new String[oneCycleFrames.length * safeCycles];
        int[] durations = new int[oneCycleDurations.length * safeCycles];

        for (int cycle = 0; cycle < safeCycles; cycle++) {
            for (int i = 0; i < oneCycleFrames.length; i++) {
                int index = cycle * oneCycleFrames.length + i;
                frames[index] = oneCycleFrames[i];
                durations[index] = oneCycleDurations[i];
            }
        }

        animation.play(frames, durations);
    }

    private void playRunAnimationLoop() {
        animation.playLoop(getRunCycleFrames(), getRunCycleDurations());
    }

    private String[] getRunCycleFrames() {
        return new String[]{
                teamAsset("run1"),
                teamAsset("run2"),
                teamAsset("run3"),
                teamAsset("run2")
        };
    }

    private int[] getRunCycleDurations() {
        return new int[]{2, 2, 2, 2};
    }

    protected void updateActionAnimation() {
        animation.update();

        if (action == PlayerAction.ATTACK_READY && !jumping) {
            finishAction();
            return;
        }

        if (action == PlayerAction.ATTACK_SWING && !jumping && animation.isHoldingFrame()) {
            finishAction();
            return;
        }

        if (action == PlayerAction.BLOCK && !jumping && animation.isHoldingFrame()) {
            finishAction();
            return;
        }

        if (action == PlayerAction.DIVE && !diving && animation.isHoldingFrame()) {
            finishAction();
            return;
        }

        if ((action == PlayerAction.RECEIVING || action == PlayerAction.SETTING) && !animation.isPlaying()) {
            finishAction();
        }
    }

    protected void finishAction() {
        mirrorImage = false;
        action = PlayerAction.IDLE;
        attacking = false;
        blocking = false;
        animation.stopToIdle();
    }

    protected String teamAsset(String actionName) {
        return (redSide ? "player 1 " : "player 2 ") + actionName + ".png";
    }

    protected double directionTowardNet() {
        return redSide ? 1.0 : -1.0;
    }
}