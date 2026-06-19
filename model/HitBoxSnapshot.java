/*
用來暫存 HitBox 當前設定，之後可恢復回原本碰撞箱。
目前主要用於撲球時切換低扁碰撞箱，撲球結束後還原。
*/
package model;

public class HitBoxSnapshot {
    private final double offsetX;
    private final double offsetY;
    private final double width;
    private final double height;
    private final int arcWidth;
    private final int arcHeight;
    private final double rotationDegrees;

    private HitBoxSnapshot(HitBox box) {
        this.offsetX = box.offsetX;
        this.offsetY = box.offsetY;
        this.width = box.width;
        this.height = box.height;
        this.arcWidth = box.arcWidth;
        this.arcHeight = box.arcHeight;
        this.rotationDegrees = box.rotationDegrees;
    }

    public static HitBoxSnapshot capture(HitBox box) {
        return new HitBoxSnapshot(box);
    }

    public void restoreTo(HitBox box) {
        box.set(offsetX, offsetY, width, height, arcWidth, arcHeight, rotationDegrees);
    }
}