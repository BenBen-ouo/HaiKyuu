package model.ball;

/** 離散舉球／拋球，以及保留原球路的固定速率目標估算。 */
public final class BallTrajectory {
    private BallTrajectory() {}

    public record ApexLaunch(int durationFrames, double velocityY) {}
    public record Velocity(double x, double y) {}

    public static ApexLaunch toApex(double startY, double apexY, double gravity) {
        if (!Double.isFinite(startY) || !Double.isFinite(apexY)
                || !Double.isFinite(gravity) || gravity <= 0) {
            throw new IllegalArgumentException("Invalid apex trajectory");
        }
        int frames = Math.max(1, (int) Math.ceil(
                (-1 + Math.sqrt(1 + 8 * (startY - apexY) / gravity)) / 2));
        return new ApexLaunch(frames, (apexY - startY) / frames - gravity * (frames + 1) / 2);
    }

    /** 下降時通過指定落點；最高點的 X 由此飛行時間決定。 */
    public static Velocity throughTarget(double startX, double startY, double targetX, double targetY,
            double apexY, double gravity) {
        ApexLaunch launch = toApex(startY, apexY, gravity);
        double linearTerm = launch.velocityY() + gravity / 2;
        double discriminant = linearTerm * linearTerm + 2 * gravity * (targetY - startY);
        double duration = (-linearTerm + Math.sqrt(Math.max(0, discriminant))) / gravity;
        return new Velocity((targetX - startX) / Math.max(1, duration), launch.velocityY());
    }

    /** 非垂直球路沿用連續時間估算；離散步進的接球容差另由行為測試保障。 */
    public static Velocity estimateVelocityToTargetAtSpeed(double startX, double startY,
            double targetX, double targetY, double launchSpeed, double gravity) {
        if (!Double.isFinite(startX) || !Double.isFinite(startY) || !Double.isFinite(targetX)
                || !Double.isFinite(targetY) || !Double.isFinite(launchSpeed) || launchSpeed <= 0
                || !Double.isFinite(gravity) || gravity <= 0) {
            throw new IllegalArgumentException("Invalid target trajectory");
        }
        double deltaX = targetX - startX;
        double deltaY = targetY - startY;
        if (deltaX == 0 && Double.isFinite(launchSpeed) && launchSpeed > 0
                && Double.isFinite(gravity) && gravity > 0) {
            // Ball 先加重力再移動；以離散步進可達的頂點判斷垂直目標。
            double ascentFrames = Math.floor(launchSpeed / gravity);
            double apexDeltaY = -launchSpeed * ascentFrames
                    + gravity * ascentFrames * (ascentFrames + 1) / 2;
            if (deltaY >= apexDeltaY) return new Velocity(0, -launchSpeed);
            // 依使用者收尾決定，維持既有不可達降級，不改力道與球路。
        }
        double x2 = deltaX * deltaX;
        double a = deltaY * deltaY + x2;
        double b = -x2 * (gravity * deltaY + launchSpeed * launchSpeed);
        double d = 0.25 * gravity * gravity * x2 * x2;
        double discriminant = b * b - 4 * a * d;
        if (Math.abs(deltaX) < 1) {
            deltaX = deltaX >= 0 ? 1 : -1;
        }

        if (discriminant >= 0) {
            double u = (-b - Math.sqrt(discriminant)) / (2.0 * a);

            if (u > 0) {
                double vx = Math.sqrt(u) * Math.signum(deltaX);
                double vy = -Math.sqrt(Math.max(0, launchSpeed * launchSpeed - u));
                return new Velocity(vx, vy);
            }
        }
        double v = Math.sqrt(launchSpeed * launchSpeed / 2.0);
        return new Velocity(v * Math.signum(deltaX), -v);
    }
}
