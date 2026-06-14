package model;

public class Player {
    public String assetName;

    // 圖片左上角位置
    public double x;
    public double y;

    public double vx;
    public double vy;

    // 圖片顯示大小
    public int imageWidth = 70;
    public int imageHeight = 86;

    // 碰撞箱大小
    public int width = 28;
    public int height = 68;

    // 碰撞箱相對於圖片左上角的偏移
    public int hitBoxOffsetX = 21;
    public int hitBoxOffsetY = 14;

    public boolean jumping;
    public boolean attacking;
    public boolean blocking;
    public boolean diving;

    public Player(String assetName, double x, double y) {
        this.assetName = assetName;
        this.x = x;
        this.y = y;
    }

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

        // 用圖片範圍限制世界邊界
        if (x < GameConfig.WORLD_LEFT) {
            x = GameConfig.WORLD_LEFT;
        }

        if (x + imageWidth > GameConfig.WORLD_RIGHT) {
            x = GameConfig.WORLD_RIGHT - imageWidth;
        }
    }

    public double getHitBoxX() {
        return x + hitBoxOffsetX;
    }

    public double getHitBoxY() {
        return y + hitBoxOffsetY;
    }

    public double getHitBoxCenterX() {
        return getHitBoxX() + width / 2.0;
    }

    public double getHitBoxCenterY() {
        return getHitBoxY() + height / 2.0;
    }

    public boolean intersectsBall(Ball ball) {
        double hitX = getHitBoxX();
        double hitY = getHitBoxY();

        double nearestX = Math.max(hitX, Math.min(ball.x, hitX + width));
        double nearestY = Math.max(hitY, Math.min(ball.y, hitY + height));

        double dx = ball.x - nearestX;
        double dy = ball.y - nearestY;

        return dx * dx + dy * dy <= ball.radius * ball.radius;
    }
}