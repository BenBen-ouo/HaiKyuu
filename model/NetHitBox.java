/*
網子專用碰撞箱，保存網子碰撞區域的位置與大小。
預設值由 GameConfig 讀取，可透過 set 方法或 GameConfig 的 NET_HITBOX 設定調整。
只負責判定球是否碰到網子的左右側或上方，反彈處理由 Ball 處理。
*/
package model;

public class NetHitBox {
    private double centerX;
    private double topY;
    private double width;
    private double height;

    public NetHitBox() {
        resetToConfig();
    }

    public void resetToConfig() {
        set(
                GameConfig.NET_HITBOX_CENTER_X,
                GameConfig.NET_HITBOX_TOP_Y,
                GameConfig.NET_HITBOX_WIDTH,
                GameConfig.NET_HITBOX_HEIGHT
        );
    }

    public void set(double centerX, double topY, double width, double height) {
        this.centerX = centerX;
        this.topY = topY;
        this.width = width;
        this.height = height;
    }

    public double getLeft() {
        return centerX - width / 2.0;
    }

    public double getRight() {
        return centerX + width / 2.0;
    }

    public double getTop() {
        return topY;
    }

    public double getBottom() {
        return topY + height;
    }

    public boolean intersectsBall(Ball ball) {
        // 網子底部預設與地板重合，不需要處理球從下方撞網的情況。
        if (ball.y > getBottom()) {
            return false;
        }

        double nearestX = Math.max(getLeft(), Math.min(ball.x, getRight()));
        double nearestY = Math.max(getTop(), Math.min(ball.y, getBottom()));
        double dx = ball.x - nearestX;
        double dy = ball.y - nearestY;

        return dx * dx + dy * dy <= ball.radius * ball.radius;
    }
}