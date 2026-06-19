package model;

public abstract class Player {
    public String assetName;

    // 圖片左上角位置
    public double x;
    public double y;

    // 初始位置
    protected double initialX;
    protected double initialY;

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

    public Player(String assetName, double x, double y, boolean redSide) {
        this.assetName = assetName;
        this.x = x;
        this.y = y;
        this.initialX = x;
        this.initialY = y;
        this.redSide = redSide;
        this.hitBox = new HitBox(this);
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
}