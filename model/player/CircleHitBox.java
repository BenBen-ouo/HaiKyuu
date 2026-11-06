package model.player;

/** 精確的圓形碰撞判定；圓心位移以角色圖片左上角為基準。 */
public final class CircleHitBox extends HitBox {
    public CircleHitBox(Player owner) {
        super(owner);
    }

    public void setCircle(double centerX, double centerY, double radius) {
        int diameter = (int) Math.round(radius * 2);
        set(centerX - radius, centerY - radius, radius * 2, radius * 2,
                diameter, diameter, 0);
    }

    @Override
    public boolean intersectsBall(model.ball.Ball ball) {
        double dx = ball.x - getCenterX();
        double dy = ball.y - getCenterY();
        double radius = width / 2 + ball.radius;
        return dx * dx + dy * dy <= radius * radius;
    }
}
