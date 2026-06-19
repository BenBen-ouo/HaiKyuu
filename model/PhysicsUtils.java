package model;

public class PhysicsUtils {

    public static double[] calculateVelocityToTarget(double startX, double startY, double targetX, double targetY, double power, double gravity) {
        double deltaX = targetX - startX;
        double deltaY = targetY - startY;
        double x2 = deltaX * deltaX;
        double a = deltaY * deltaY + x2;
        double b = -x2 * (gravity * deltaY + power * power);
        double d = 0.25 * gravity * gravity * x2 * x2;
        double discriminant = b * b - 4 * a * d;
        

        if (Math.abs(deltaX) < 1) {
            deltaX = deltaX >= 0 ? 1 : -1;
        }

        if (discriminant >= 0) {
            double u = (-b - Math.sqrt(discriminant)) / (2.0 * a);

            if (u > 0) {
                double vx = Math.sqrt(u) * Math.signum(deltaX);
                double vy = -Math.sqrt(Math.max(0, power * power - u));
                return new double[]{vx, vy};
            }
        }
        double v = Math.sqrt(power * power / 2.0);
        return new double[]{v * Math.signum(deltaX), -v};
    }
}
