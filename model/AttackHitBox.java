package model;

// 預留給之後扣球判斷使用的攻擊碰撞箱。
// 目前只保存位置與開關，不參與任何球的碰撞邏輯。

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
}
