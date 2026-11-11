package model.ai;

import model.GameConfig;

/** 僅累計本場已發生的公開觸球；先驗避免少數樣本支配預測。 */
public final class OpponentHabits {
    private final int[] attackers = new int[3];
    private final int[] tempos = new int[3];
    private final int[] directions = new int[5];
    private final int[] serves = new int[6];
    private final double[] landingSum = new double[2];
    private final int[] landings = new int[2];
    private final double[][] attackLandingSum = new double[3][3];
    private final int[][] attackLandings = new int[3][3];
    private double fastDeepSum;
    private int fastDeepLandings;
    private int attackLandingSamples;
    private int attacks;
    private int lobs;
    private int serveSamples;
    public void recordAttack(int role, int tempo, int direction) {
        attackers[role]++;
        tempos[tempo]++;
        directions[direction]++;
        attacks++;
        if (direction >= 3) lobs++;
    }
    public void recordServe(int type) { serves[type]++; serveSamples++; }
    public double directionWeight(int direction) {
        return (directions[direction] + AiTuning.HABIT_PRIOR_SAMPLES / directions.length)
                / (attacks + AiTuning.HABIT_PRIOR_SAMPLES);
    }
    public double serveWeight(int type) {
        return (serves[type] + AiTuning.HABIT_PRIOR_SAMPLES / serves.length)
                / (serveSamples + AiTuning.HABIT_PRIOR_SAMPLES);
    }
    public void recordLanding(boolean serve, double x) {
        int slot = serve ? 0 : 1;
        landingSum[slot] += x;
        landings[slot]++;
    }
    public void recordAttackLanding(int role, int tempo, double x) {
        recordLanding(false, x);
        attackLandingSum[role][tempo] += x;
        attackLandings[role][tempo]++;
        attackLandingSamples++;
        if (tempo <= 1 && x >= GameConfig.COURT_LEFT_X - GameConfig.BALL_RADIUS
                && x <= GameConfig.COURT_LEFT_X + GameConfig.THREE_METER_PX) {
            fastDeepLandings++;
            fastDeepSum += x;
        }
    }
    public double expectedAttackLanding(int role, int tempo, double fallback) {
        return (attackLandingSum[role][tempo] + fallback * AiTuning.HABIT_PRIOR_SAMPLES)
                / (attackLandings[role][tempo] + AiTuning.HABIT_PRIOR_SAMPLES);
    }
    public double fastDeepRisk() {
        return fastDeepLandings / (attackLandingSamples + AiTuning.HABIT_PRIOR_SAMPLES);
    }
    public boolean shouldKeepBackForReturn(int defense) {
        return attackLandingSamples >= AiTuning.MIN_RETURN_HABIT_SAMPLES
                && fastDeepRisk() * AiDifficulty.ability(defense) >= AiTuning.BACK_RETURN_RISK_THRESHOLD;
    }
    public double expectedFastDeepLanding(double fallback) {
        return (fastDeepSum + fallback * AiTuning.HABIT_PRIOR_SAMPLES)
                / (fastDeepLandings + AiTuning.HABIT_PRIOR_SAMPLES);
    }
    public int attackLandingSamples() { return attackLandingSamples; }
    public double attackerWeight(int role) {
        return (attackers[role] + AiTuning.HABIT_PRIOR_SAMPLES / 3)
                / (attacks + AiTuning.HABIT_PRIOR_SAMPLES);
    }
    public double tempoWeight(int tempo) {
        return (tempos[tempo] + AiTuning.HABIT_PRIOR_SAMPLES / 3)
                / (attacks + AiTuning.HABIT_PRIOR_SAMPLES);
    }
    public double lobRate() { return (lobs + 1.0) / (attacks + AiTuning.HABIT_PRIOR_SAMPLES); }
    public double expectedLanding(boolean serve, double fallback) {
        int slot = serve ? 0 : 1;
        return (landingSum[slot] + fallback * AiTuning.HABIT_PRIOR_SAMPLES)
                / (landings[slot] + AiTuning.HABIT_PRIOR_SAMPLES);
    }
    public int attackSamples() { return attacks; }
    public void reset() {
        java.util.Arrays.fill(attackers, 0);
        java.util.Arrays.fill(tempos, 0);
        java.util.Arrays.fill(directions, 0);
        java.util.Arrays.fill(serves, 0);
        java.util.Arrays.fill(landingSum, 0);
        java.util.Arrays.fill(landings, 0);
        for (int i = 0; i < 3; i++) {
            java.util.Arrays.fill(attackLandingSum[i], 0);
            java.util.Arrays.fill(attackLandings[i], 0);
        }
        fastDeepSum = 0;
        fastDeepLandings = 0;
        attackLandingSamples = 0;
        attacks = 0;
        lobs = 0;
        serveSamples = 0;
    }
}
