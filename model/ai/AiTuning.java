package model.ai;

/** AI 可調決策參數；像素／tick 與正式模型相同，不覆寫角色能力。 */
public final class AiTuning {
    private AiTuning() {}
    public static final int FORECAST_FRAMES = 150;
    public static final int TRACK_INTERVAL_FRAMES = 4;
    public static final int SERVE_WAIT_FRAMES = 45;
    public static final double REACTION_JITTER_RATIO = .16;
    public static final double DEFENSE_ERROR_LOW_PX = 45;
    public static final double DEFENSE_ERROR_HIGH_PX = 2;
    public static final double MOVEMENT_DEAD_ZONE_PX = 3;
    public static final double HABIT_PRIOR_SAMPLES = 8;
    public static final int MIN_RETURN_HABIT_SAMPLES = 3;
    public static final double BACK_RETURN_RISK_THRESHOLD = .20;
    public static final int FAST_OPPONENT_RETURN_FRAMES = 30;
    public static final double BLOCK_TIMING_ERROR_LOW_FRAMES = 20;
    public static final double BLOCK_TIMING_ERROR_HIGH_FRAMES = 3;
    public static final int ATTACK_TIMING_JITTER_FRAMES = 2;
    public static final double MIN_LOB_ABILITY = .20;
    public static final double MAX_LOB_PROBABILITY = .28;
    public static final double MB_LOB_PROBABILITY_MULTIPLIER = .15;
    public static final int MIN_ATTACK_COVER_DEFENSE = 41;
    public static final double MAX_ATTACK_COVER_PROBABILITY = .9;
    public static final double MAX_AIR_SET_PROBABILITY = .28;
    public static final int MIN_AIR_SET_ATTACK = 61;
    public static final int MIN_JUMP_SERVE_ATTACK = 41;
    public static final int MIN_TWO_ATTACKERS = 41;
    public static final int MIN_THREE_ATTACKERS = 61;
    public static final double MAX_JUMP_SET_PROBABILITY = .95;
    public static final double MAX_ADVANCED_SERVE_PROBABILITY = .75;
    public static final double EXPERT_SUBOPTIMAL_PROBABILITY = .02;
    public static final double LOW_SUBOPTIMAL_EXTRA_PROBABILITY = .15;
    public static final double QUICK_SET_BASE_PROBABILITY = .15;
    public static final double QUICK_SET_SKILL_PROBABILITY = .35;
    public static final int MIN_QUICK_SET_ATTACK = 21;
    public static final int MIN_JUMP_SET_ATTACK = 41;
    public static final int MIN_NEGATIVE_TEMPO_ATTACK = 21;
    public static final int MIN_GAP_SELECTION_ATTACK = 41;
    public static final int MIN_TEMPO_PREDICTION_DEFENSE = 21;
    public static final int MIN_REPEAT_BLOCK_DEFENSE = 41;
    public static final double MAX_NEGATIVE_TEMPO_PROBABILITY = .55;
    public static final double ADVANCED_JUMP_SERVE_SHARE = .65;
    public static final double LOW_VISIBLE_READ_PROBABILITY = .25;
    public static final double VISIBLE_READ_SKILL_PROBABILITY = .7;
    public static final int NEGATIVE_START_MIN_LEAD_FRAMES = 42;
    public static final int NEGATIVE_START_MAX_LEAD_FRAMES = 48;
    public static final int FIRST_TEMPO_WINDOW_FRAMES = 5;
    public static final int REPLAN_INTERVAL_FRAMES = 4;
    public static final int CONTACT_PATIENCE_FRAMES = 4;
    public static final double SHOT_SELECTION_NOISE = 8;
    public static final double NET_CLEARANCE_MARGIN_PX = 6;
    public static final double LOB_PREFERENCE_PENALTY = 35;
    public static final double BLOCKED_SHOT_PENALTY = 70;
    public static final double SHORT_DEFENSE_DISTANCE_FROM_NET_PX = 180;
    // 防守分工、撲救與攔網的估計容差；不是實際碰撞箱或角色速度。
    public static final double DEFAULT_DEFENSE_DISTANCE_FROM_NET_PX = 280;
    public static final double SERVE_HABIT_POSITION_BIAS_PX = 100;
    public static final double SERVE_POSITION_EDGE_MARGIN_RATIO = .25;
    public static final double LOB_HABIT_POSITION_BIAS_PX = 40;
    public static final double WING_REACH_MARGIN_PX = 65;
    public static final double RESERVE_GAP_PX = 100;
    public static final int WING_DIVE_WINDOW_FRAMES = 34;
    public static final double LOW_BALL_HEIGHT_FROM_FLOOR_PX = 10;
    public static final double WING_MIN_DIVE_DISTANCE_PX = 10;
    public static final double WING_OBSERVATION_NET_MARGIN_PX = 60;
    public static final int BLOCK_NET_WINDOW_FRAMES = 65;
    public static final double BLOCK_NET_HEIGHT_MARGIN_PX = 30;
    public static final int BLOCK_NET_LEAD_FRAMES = 22;
    public static final int NEGATIVE_BLOCK_GUESS_FRAMES = 5;
    public static final int FIRST_BLOCK_GUESS_FRAMES = 12;
    public static final int SECOND_BLOCK_GUESS_FRAMES = 20;
    public static final int VISIBLE_BLOCK_OFFSET_FRAMES = 6;
    public static final int HABIT_BLOCK_ADJUST_FRAMES = 8;
    // 進攻估計的偏好與安全邊距。
    public static final double BACK_TAKEOFF_LINE_MARGIN_PX = 2;
    public static final int RECEIVE_RECOVERY_ESTIMATE_FRAMES = 30;
    public static final double BALL_FLIGHT_CHANGE_TOLERANCE = .5;
    public static final int SIMULTANEOUS_TAKEOFF_WINDOW_FRAMES = 5;

    /** 解鎖值即有小幅機率，其後逐點增加，滿能力為 1。 */
    static double unlockedProgress(int ability, int minimum) {
        return ability < minimum ? 0 : (ability - minimum + 1.0) / (101 - minimum);
    }

    public static void validate() {
        for (int minimum : new int[]{MIN_ATTACK_COVER_DEFENSE, MIN_AIR_SET_ATTACK, MIN_JUMP_SERVE_ATTACK,
                MIN_TWO_ATTACKERS, MIN_THREE_ATTACKERS, MIN_QUICK_SET_ATTACK, MIN_JUMP_SET_ATTACK,
                MIN_NEGATIVE_TEMPO_ATTACK, MIN_GAP_SELECTION_ATTACK, MIN_TEMPO_PREDICTION_DEFENSE,
                MIN_REPEAT_BLOCK_DEFENSE}) {
            if (minimum < 1 || minimum > 100) throw new IllegalArgumentException("Invalid AI unlock threshold");
        }
        if (MIN_THREE_ATTACKERS < MIN_TWO_ATTACKERS
                || SERVE_POSITION_EDGE_MARGIN_RATIO < 0 || SERVE_POSITION_EDGE_MARGIN_RATIO >= .5) {
            throw new IllegalArgumentException("Invalid AI formation settings");
        }
        if (FORECAST_FRAMES <= 0 || TRACK_INTERVAL_FRAMES <= 0 || SERVE_WAIT_FRAMES < 0
                || ATTACK_TIMING_JITTER_FRAMES < 0 || REPLAN_INTERVAL_FRAMES <= 0
                || CONTACT_PATIENCE_FRAMES < 0 || HABIT_PRIOR_SAMPLES <= 0
                || MIN_RETURN_HABIT_SAMPLES <= 0 || FAST_OPPONENT_RETURN_FRAMES <= 0
                || NEGATIVE_START_MIN_LEAD_FRAMES < 0
                || NEGATIVE_START_MAX_LEAD_FRAMES < NEGATIVE_START_MIN_LEAD_FRAMES
                || DEFENSE_ERROR_HIGH_PX < 0 || DEFENSE_ERROR_LOW_PX < DEFENSE_ERROR_HIGH_PX
                || BLOCK_TIMING_ERROR_HIGH_FRAMES < 0
                || BLOCK_TIMING_ERROR_LOW_FRAMES < BLOCK_TIMING_ERROR_HIGH_FRAMES) {
            throw new IllegalArgumentException("Invalid AI timing or error settings");
        }
        double[] probabilities = {REACTION_JITTER_RATIO, MIN_LOB_ABILITY, MAX_LOB_PROBABILITY,
                BACK_RETURN_RISK_THRESHOLD,
                MB_LOB_PROBABILITY_MULTIPLIER, MAX_ATTACK_COVER_PROBABILITY,
                MAX_AIR_SET_PROBABILITY, MAX_JUMP_SET_PROBABILITY, MAX_ADVANCED_SERVE_PROBABILITY,
                EXPERT_SUBOPTIMAL_PROBABILITY, LOW_SUBOPTIMAL_EXTRA_PROBABILITY,
                QUICK_SET_BASE_PROBABILITY, QUICK_SET_SKILL_PROBABILITY, MAX_NEGATIVE_TEMPO_PROBABILITY,
                ADVANCED_JUMP_SERVE_SHARE, LOW_VISIBLE_READ_PROBABILITY, VISIBLE_READ_SKILL_PROBABILITY};
        for (double probability : probabilities) {
            if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
                throw new IllegalArgumentException("AI probability must be finite and in 0..1");
            }
        }
        if (EXPERT_SUBOPTIMAL_PROBABILITY + LOW_SUBOPTIMAL_EXTRA_PROBABILITY > 1
                || QUICK_SET_BASE_PROBABILITY + QUICK_SET_SKILL_PROBABILITY > 1
                || LOW_VISIBLE_READ_PROBABILITY + VISIBLE_READ_SKILL_PROBABILITY > 1) {
            throw new IllegalArgumentException("AI probability curve exceeds 1");
        }
    }
}
