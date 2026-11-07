package model.ai;

import model.GameConfig;
import java.util.random.RandomGenerator;

/** 每場固定的能力設定；只影響決策，不改物理或命中。 */
public record AiDifficulty(int defense, int attack) {
    public static final AiDifficulty DEFAULT = new AiDifficulty(50, 50);
    private static final int[] REACTION_KNOTS = {1, 20, 40, 60, 80, 100};
    private static final double[] REACTION_SECONDS = {.40, .30, .25, .20, .15, .12};
    public AiDifficulty {
        if (defense < 1 || defense > 100 || attack < 1 || attack > 100) {
            throw new IllegalArgumentException("AI abilities must be 1..100");
        }
    }
    public static double ability(int value) { return (value - 1) / 99.0; }
    public static String label(int value) {
        return value <= 20 ? "入門" : value <= 40 ? "簡單" : value <= 60 ? "普通"
                : value <= 80 ? "困難" : "專家";
    }
    public static double reactionSeconds(int value) {
        for (int i = 1; i < REACTION_KNOTS.length; i++) {
            if (value <= REACTION_KNOTS[i]) return REACTION_SECONDS[i - 1] + (REACTION_SECONDS[i] - REACTION_SECONDS[i - 1])
                    * (value - REACTION_KNOTS[i - 1]) / (REACTION_KNOTS[i] - REACTION_KNOTS[i - 1]);
        }
        return REACTION_SECONDS[REACTION_SECONDS.length - 1];
    }
    public static int reactionFrames(int value, RandomGenerator random) {
        return Math.max(1, (int) Math.round(reactionSeconds(value) * GameConfig.TICKS_PER_SECOND
                * (1 + (random.nextDouble() - .5) * AiTuning.REACTION_JITTER_RATIO)));
    }
}
