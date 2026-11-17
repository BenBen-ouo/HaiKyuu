package model.ai;

import model.GameConfig;
import java.util.random.RandomGenerator;

/** 每場固定的能力設定；只影響決策，不改物理或命中。 */
public record AiDifficulty(int defense, int attack) {
    public static final int LEVEL_COUNT = 5;
    public static final int DEFAULT_LEVEL = 3;
    private static final int POINTS_PER_LEVEL = 20;
    public static final AiDifficulty DEFAULT = new AiDifficulty(valueForLevel(DEFAULT_LEVEL), valueForLevel(DEFAULT_LEVEL));
    private static final int[] REACTION_KNOTS = {1, 20, 40, 60, 80, 100};
    private static final double[] REACTION_SECONDS = {.40, .32, .28, .25, .22, .20};
    public AiDifficulty {
        if (defense < 1 || defense > 100 || attack < 1 || attack > 100) {
            throw new IllegalArgumentException("AI abilities must be 1..100");
        }
    }
    public static double ability(int value) { return (value - 1) / 99.0; }
    /** 選單只提供五階段；內部仍沿用原本連續公式與隨機抽樣。 */
    public static int valueForLevel(int level) {
        if (level < 1 || level > LEVEL_COUNT) throw new IllegalArgumentException("AI level must be in 1..5");
        return level * POINTS_PER_LEVEL;
    }
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
        double skill = ability(value);
        if (random.nextDouble() < AiTuning.FAST_REACTION_MAX_PROBABILITY * skill * skill) {
            return (int) Math.round(.15 * GameConfig.TICKS_PER_SECOND);
        }
        return Math.max(1, (int) Math.round(reactionSeconds(value) * GameConfig.TICKS_PER_SECOND
                * (1 + (random.nextDouble() - .5) * AiTuning.REACTION_JITTER_RATIO)));
    }
    /** 同一判讀只抽一次；難度改變分布，不縮小所有樣本的硬上限。 */
    static double receiveError(int value, RandomGenerator random) {
        double largeChance = AiTuning.LARGE_ERROR_BEGINNER_PROBABILITY
                + ability(value) * (AiTuning.LARGE_ERROR_EXPERT_PROBABILITY - AiTuning.LARGE_ERROR_BEGINNER_PROBABILITY);
        double magnitude = random.nextDouble() < largeChance
                ? AiTuning.DEFENSE_ERROR_COMMON_PX + random.nextDouble()
                    * (AiTuning.DEFENSE_ERROR_MAX_PX - AiTuning.DEFENSE_ERROR_COMMON_PX)
                : random.nextDouble() * AiTuning.DEFENSE_ERROR_COMMON_PX;
        return random.nextBoolean() ? magnitude : -magnitude;
    }
}
