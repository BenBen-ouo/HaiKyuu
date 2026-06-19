/*
預留給攻擊動作使用的碰撞箱資料。
目前只保存攻擊碰撞箱的位置、大小與開關，不參與球的碰撞邏輯。
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
}
