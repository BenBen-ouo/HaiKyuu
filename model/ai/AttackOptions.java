package model.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import model.GameConfig;

/** 評估既有球路能否過網／落在對場；只估計，不碰觸權威球。 */
final class AttackOptions {
    private static final double[] HORIZONTAL_SPEEDS = {GameConfig.SPIKE_HORIZONTAL_SPEED,
            GameConfig.FLAT_SPIKE_HORIZONTAL_SPEED, GameConfig.SHORT_SPIKE_HORIZONTAL_SPEED,
            GameConfig.LOB_SPIKE_HORIZONTAL_SPEED, GameConfig.LONG_LOB_SPIKE_HORIZONTAL_SPEED};
    private static final double[] VELOCITIES_Y = {GameConfig.SPIKE_VELOCITY_Y,
            GameConfig.FLAT_SPIKE_VELOCITY_Y, GameConfig.SHORT_SPIKE_VELOCITY_Y,
            GameConfig.LOB_SPIKE_VELOCITY_Y, GameConfig.LONG_LOB_SPIKE_VELOCITY_Y};
    private AttackOptions() {}

    static boolean hasSafeShot(double x, double y, double skill) {
        int limit = skill < AiTuning.MIN_LOB_ABILITY ? 3 : 5;
        for (int mode = 0; mode < limit; mode++) if (landing(x, y, mode) != null) return true;
        return false;
    }

    static boolean hasNormalShot(double x, double y) {
        for (int mode = 0; mode < 3; mode++) if (landing(x, y, mode) != null) return true;
        return false;
    }

    static int choose(AiObservation o, double skill, RandomGenerator random) {
        List<Integer> safe = new ArrayList<>(5);
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        boolean normalAvailable = hasNormalShot(o.ball().x(), o.ball().y());
        boolean useLob = skill >= AiTuning.MIN_LOB_ABILITY
                && (!normalAvailable || random.nextDouble() < AiTuning.MAX_LOB_PROBABILITY * skill);
        int limit = useLob ? 5 : 3;
        for (int mode = 0; mode < limit; mode++) {
            Double landing = landing(o.ball().x(), o.ball().y(), mode);
            if (landing == null) continue;
            safe.add(mode);
            double gap = Math.min(Math.abs(landing - o.opponent().get(0).receiveX()),
                    Math.abs(landing - o.opponent().get(3).receiveX()));
            double block = o.opponent().get(2).jumping() && mode < 3 ? AiTuning.BLOCKED_SHOT_PENALTY : 0;
            double score = gap - block + random.nextDouble() * AiTuning.SHOT_SELECTION_NOISE;
            if (mode >= 3) score -= (1 - skill) * AiTuning.LOB_PREFERENCE_PENALTY;
            if (score > bestScore) { bestScore = score; best = mode; }
        }
        if (safe.isEmpty()) return -1;
        double variation = AiTuning.EXPERT_SUBOPTIMAL_PROBABILITY
                + (1 - skill) * AiTuning.LOW_SUBOPTIMAL_EXTRA_PROBABILITY;
        return random.nextDouble() < variation ? safe.get(random.nextInt(safe.size())) : best;
    }

    private static Double landing(double x, double y, int mode) {
        return landing(x, y, HORIZONTAL_SPEEDS[mode], VELOCITIES_Y[mode]);
    }

    static boolean isUsableFlight(double x, double y, double horizontalSpeed, double velocityY) {
        return landing(x, y, horizontalSpeed, velocityY) != null;
    }

    private static Double landing(double x, double y, double horizontalSpeed, double velocityY) {
        if (!Double.isFinite(horizontalSpeed) || horizontalSpeed <= 0 || !Double.isFinite(velocityY)) return null;
        BallForecast forecast = new BallForecast(x, y, horizontalSpeed, velocityY);
        int first = Math.max(0, (int) Math.floor((GameConfig.NET_X - GameConfig.NET_WIDTH / 2 - GameConfig.BALL_RADIUS - x)
                / horizontalSpeed));
        int last = Math.max(first, (int) Math.ceil((GameConfig.NET_X + GameConfig.NET_WIDTH / 2 + GameConfig.BALL_RADIUS - x)
                / horizontalSpeed));
        if (last > AiTuning.FORECAST_FRAMES) return null;
        for (int t = first; t <= last; t++) {
            if (forecast.yAt(t) + GameConfig.BALL_RADIUS + AiTuning.NET_CLEARANCE_MARGIN_PX >= GameConfig.NET_TOP_Y) return null;
        }
        double target = forecast.xAt(forecast.descendingArrival(GameConfig.FLOOR_Y - GameConfig.BALL_RADIUS));
        return target > GameConfig.NET_X + GameConfig.BALL_RADIUS
                && target < GameConfig.COURT_RIGHT_X + GameConfig.BALL_RADIUS ? target : null;
    }
}
