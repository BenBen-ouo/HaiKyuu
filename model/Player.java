package model;

public abstract class Player {
    public String assetName;
    public double x;
    public double y;

    // 初始位置
    protected double initialX;
    protected double initialY;

    public double vx;
    public double vy;

    public int imageWidth = GameConfig.PLAYER_IMAGE_WIDTH;
    public int imageHeight = GameConfig.PLAYER_IMAGE_HEIGHT;
    public HitBox hitBox;

    public boolean jumping;
    public boolean attacking;
    public boolean blocking;
    public boolean diving;
    public boolean redSide;
    public boolean mirrorImage = false;

    protected final PlayerAnimation animation;
    protected final PlayerActionAnimator actionAnimator;
    protected PlayerAction action = PlayerAction.IDLE;

    public double minX = GameConfig.WORLD_LEFT;
    public double maxX = GameConfig.WORLD_RIGHT;

    public Player(String assetName, double x, double y, boolean redSide) {
        this.assetName = assetName;
        this.x = x;
        this.y = y;
        this.initialX = x;
        this.initialY = y;
        this.redSide = redSide;
        this.hitBox = new HitBox(this);
        this.animation = new PlayerAnimation(this, assetName);
        this.actionAnimator = new PlayerActionAnimator(this, animation);
    }

    public void resetToInitial() {
        this.x = initialX;
        this.y = initialY;
        this.vx = 0;
        this.vy = 0;
        this.jumping = false;
        this.attacking = false;
        this.blocking = false;
        this.diving = false;
    }

    public abstract void update(TeamInput input);

    public void applyGravity() {
        vy += GameConfig.GRAVITY;
        x += vx;
        y += vy;

        if (y + imageHeight > GameConfig.FLOOR_Y) {
            landOnFloor();
        }

        clampToMovementBounds();
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
        actionAnimator.playReceive();
    }

    public void playSettingAnimation() {
        actionAnimator.playSetting();
    }

    public void playDiveAnimation() {
        actionAnimator.playDive();
    }

    protected void startAttackReady(double horizontalSpeed) {
        actionAnimator.startAttackReady(horizontalSpeed);
    }

    protected void startAttackSwingAnimation() {
        actionAnimator.startAttackSwing();
    }

    protected void startBlockAnimation() {
        actionAnimator.startBlock();
    }

    protected void startRunApproachAnimation(int cycles) {
        actionAnimator.startRunApproach(cycles);
    }

    protected void startRunLoopAnimation() {
        actionAnimator.startRunLoop();
    }

    protected void updateActionAnimation() {
        actionAnimator.updateActionState();
    }

    protected void finishAction() {
        actionAnimator.finishAction();
    }

    protected String teamAsset(String actionName) {
        return (redSide ? "player 1 " : "player 2 ") + actionName + ".png";
    }

    protected double directionTowardNet() {
        return SideRules.directionTowardOpponent(redSide);
    }

    private void landOnFloor() {
        y = GameConfig.FLOOR_Y - imageHeight;
        vy = 0;
        jumping = false;
        diving = false;
    }

    private void clampToMovementBounds() {
        if (x < minX) {
            x = minX;
        }

        if (x + imageWidth > maxX) {
            x = maxX - imageWidth;
        }
    }
}