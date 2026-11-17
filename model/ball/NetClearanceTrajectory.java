package model.ball;

import model.GameConfig;

/** 近網一般送球的離散預估；可行時延長飛行時間，保持原目標點。 */
public final class NetClearanceTrajectory {
    private static final double MAX_DISTANCE_FROM_NET_PX = GameConfig.THREE_METER_PX;
    private static final double CLEARANCE_MARGIN_PX = 2;
    private static final int MAX_FLIGHT_FRAMES = 600;

    private NetClearanceTrajectory() {}

    public static BallTrajectory.Velocity correctIfBlocked(double x, double y, double radius,
            double targetX, double targetY, BallTrajectory.Velocity original, NetHitBox net) {
        if (Math.abs(x - net.getCenterX()) > MAX_DISTANCE_FROM_NET_PX
                || (targetX - net.getCenterX()) * (x - net.getCenterX()) >= 0
                || !hitsNet(x, y, radius, original, net)) return original;

        int firstDuration = Math.max(1, (int) Math.ceil(Math.abs((targetX - x) / original.x())));
        for (int frames = firstDuration; frames <= MAX_FLIGHT_FRAMES; frames++) {
            double vx = (targetX - x) / frames;
            double vy = (targetY - y) / frames - GameConfig.GRAVITY * (frames + 1) / 2;
            int apex = Math.max(0, (int) Math.ceil(-vy / GameConfig.GRAVITY) - 1);
            double apexY = y + vy * apex + GameConfig.GRAVITY * apex * (apex + 1) / 2;
            if (apexY - radius < GameConfig.WORLD_TOP) break;
            var candidate = new BallTrajectory.Velocity(vx, vy);
            if (!hitsNet(x, y, radius + CLEARANCE_MARGIN_PX, candidate, net)) return candidate;
        }
        // 球心貼入網體等無可行彈道時，不移動球、不改落點或世界邊界來掩蓋失敗。
        return original;
    }

    private static boolean hitsNet(double x, double y, double radius,
            BallTrajectory.Velocity velocity, NetHitBox net) {
        if (velocity.x() == 0 || (net.getCenterX() - x) * velocity.x() <= 0) return false;
        Ball probe = new Ball(x, y);
        probe.radius = radius;
        for (int frame = 1; frame <= MAX_FLIGHT_FRAMES; frame++) {
            probe.x = x + velocity.x() * frame;
            probe.y = y + velocity.y() * frame + GameConfig.GRAVITY * frame * (frame + 1) / 2;
            if (net.intersectsBall(probe)) return true;
            if (probe.y + radius >= GameConfig.FLOOR_Y
                    || velocity.x() > 0 && probe.x - radius > net.getRight()
                    || velocity.x() < 0 && probe.x + radius < net.getLeft()) return false;
        }
        return false;
    }
}
