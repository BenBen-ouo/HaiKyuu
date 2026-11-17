package model.ball;

/** 攔網的中心法線反射；正式碰撞與 AI 估計共用，不含碰撞或得分判斷。 */
public final class BlockReflection {
    private static final double MIN_NORMAL_LENGTH = .001;
    private static final double CENTER_NORMAL_Y = -.2;
    private BlockReflection() {}

    public static BallTrajectory.Velocity reflect(double vx, double vy, double nx, double ny,
            double fallbackDirectionX, double bounce) {
        double length = Math.hypot(nx, ny);
        if (length < MIN_NORMAL_LENGTH) {
            nx = vx == 0 ? fallbackDirectionX : Math.signum(vx);
            ny = CENTER_NORMAL_Y;
            length = Math.hypot(nx, ny);
        }
        nx /= length;
        ny /= length;
        double dot = vx * nx + vy * ny;
        if (dot < 0) return new BallTrajectory.Velocity((vx - 2 * dot * nx) * bounce,
                (vy - 2 * dot * ny) * bounce);
        double speed = Math.hypot(vx, vy) * bounce;
        return new BallTrajectory.Velocity(nx * speed, ny * speed);
    }
}
