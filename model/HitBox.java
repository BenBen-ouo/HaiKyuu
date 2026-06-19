/*
角色一般觸球碰撞箱，使用相對於角色圖片左上角的位置與大小。
支援旋轉矩形與球的碰撞判定，負責一般接球、舉球與觸球檢查。
*/
package model;

public class HitBox {
    private final Player owner;

    // 相對於球員圖片左上角的位置
    public double offsetX;
    public double offsetY;

    // 碰撞箱大小
    public double width;
    public double height;

    // 圓角大小
    public int arcWidth;
    public int arcHeight;

    // 旋轉角度，單位是 degree
    public double rotationDegrees;

    public HitBox(Player owner) {
        this.owner = owner;

        // 預設值
        this.offsetX = 21;
        this.offsetY = 14;
        this.width = 28;
        this.height = 68;
        this.arcWidth = 8;
        this.arcHeight = 8;
        this.rotationDegrees = 0;
    }

    public void set(double offsetX, double offsetY,
                    double width, double height,
                    int arcWidth, int arcHeight,
                    double rotationDegrees) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.width = width;
        this.height = height;
        this.arcWidth = arcWidth;
        this.arcHeight = arcHeight;
        this.rotationDegrees = rotationDegrees;
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
        double centerX = getCenterX();
        double centerY = getCenterY();

        double angle = Math.toRadians(-rotationDegrees);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        // 把球的位置轉回碰撞箱自己的座標系
        double dx = ball.x - centerX;
        double dy = ball.y - centerY;

        double localX = cos * dx - sin * dy + width / 2.0;
        double localY = sin * dx + cos * dy + height / 2.0;

        return circleIntersectsRoundedRect(localX, localY, ball.radius);
    }

    private boolean circleIntersectsRoundedRect(double circleX, double circleY, double radius) {
        double cornerRadius = Math.min(arcWidth, arcHeight) / 2.0;
        cornerRadius = Math.max(0, Math.min(cornerRadius, Math.min(width, height) / 2.0));

        // 沒有圓角時，用一般矩形碰撞
        if (cornerRadius <= 0) {
            double nearestX = Math.max(0, Math.min(circleX, width));
            double nearestY = Math.max(0, Math.min(circleY, height));

            double dx = circleX - nearestX;
            double dy = circleY - nearestY;

            return dx * dx + dy * dy <= radius * radius;
        }

        // 使用 rounded rectangle 的 signed distance 檢查
        double px = Math.abs(circleX - width / 2.0);
        double py = Math.abs(circleY - height / 2.0);

        double qx = px - (width / 2.0 - cornerRadius);
        double qy = py - (height / 2.0 - cornerRadius);

        double outsideX = Math.max(qx, 0);
        double outsideY = Math.max(qy, 0);

        double outsideDistance = Math.sqrt(outsideX * outsideX + outsideY * outsideY);
        double insideDistance = Math.min(Math.max(qx, qy), 0);

        double distance = outsideDistance + insideDistance - cornerRadius;

        return distance <= radius;
    }
}
