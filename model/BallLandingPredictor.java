/*
依目前球的位置與速度預測下一次到達地板高度的落點。
攔網後只用它判斷 touch out 預告，不改變球的實際物理運算。
*/
package model;

final class BallLandingPredictor {
    private BallLandingPredictor() {
    }

    static boolean willLandOutsideCourt(Ball ball) {
        double landingTime = calculateLandingTime(ball);
        if (landingTime <= 0) {
            return false;
        }

        double landingX = ball.x + ball.vx * landingTime;
        return landingX < GameConfig.COURT_LEFT_X || landingX > GameConfig.COURT_RIGHT_X;
    }

    private static double calculateLandingTime(Ball ball) {
        double a = 0.5 * GameConfig.GRAVITY;
        double b = ball.vy;
        double c = ball.y - (GameConfig.FLOOR_Y - ball.radius);
        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) {
            return -1;
        }

        double root = Math.sqrt(discriminant);
        double firstTime = (-b + root) / (2 * a);
        if (firstTime > 0) {
            return firstTime;
        }

        double secondTime = (-b - root) / (2 * a);
        return secondTime > 0 ? secondTime : -1;
    }
}
