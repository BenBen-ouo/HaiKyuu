/*
攻擊動作專用碰撞箱，使用相對於角色圖片左上角的位置與大小。
只有角色進入攻擊準備或揮臂狀態時啟用，負責扣球命中判定。
*/
package model;

public class AttackHitBox {
    private final Player owner;

    public boolean enabled = false;
    public double offsetX = 0;
    public double offsetY = 0;
    public double width = 0;
    public double height = 0;

    public AttackHitBox(Player owner) {
        this.owner = owner;
    }

    public void set(double offsetX, double offsetY, double width, double height) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width = width;
        this.height = height;
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public double getX() {
        return owner.x + offsetX;
    }

    public double getY() {
        return owner.y + offsetY;
    }

    public double getCenterX() {
        return getX() + width / 2.0;
    }

    public double getCenterY() {
        return getY() + height / 2.0;
    }

    public boolean intersectsBall(Ball ball) {
        if (!enabled || width <= 0 || height <= 0) {
            return false;
        }

        double nearestX = Math.max(getX(), Math.min(ball.x, getX() + width));
        double nearestY = Math.max(getY(), Math.min(ball.y, getY() + height));
        double dx = ball.x - nearestX;
        double dy = ball.y - nearestY;

        return dx * dx + dy * dy <= ball.radius * ball.radius;
    }
}