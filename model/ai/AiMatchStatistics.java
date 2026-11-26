package model.ai;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import model.GameConfig;
import model.rally.ContactEvent.Route;
import model.rally.ScoringLogic;

/** 本場已確認出手及結果；查詢只回傳不可變快照，不保存模型或跨場資料。 */
public final class AiMatchStatistics {
    public enum Tempo { NEGATIVE, FIRST, SECOND, UNKNOWN }
    public enum Setting { NONE, STANDING, JUMP, AIR }
    public enum Tactic { ATTACK, SECOND_ATTACK, AIR_SET, SERVE, FREE_BALL, JUMP_SET, MULTI }
    public enum Zone { FRONT, MIDDLE, DEEP, OUT }
    public enum Outcome { DIRECT_POINT, SAVED, BLOCK_RETURNED, BLOCKED_LOSS, SELF_ERROR, OTHER }
    public enum PointReason { LANDING, TOUCH_OUT, SERVE, NONE }
    /** 角色依 Team 順序；發球 touch=0，其餘為實際已裁定觸球次數。 */
    public record Key(boolean own, int role, int touch, Route route, boolean serve) {}
    public record Context(Tempo tempo, Setting setting, int participants, boolean opponentBlocking, boolean openGap) {}
    public record Detail(Key key, Context context) {}
    public record TacticKey(boolean own, Tactic tactic) {}
    public record Summary(int attempts, Map<Outcome, Integer> outcomes, Map<PointReason, Integer> points,
            Map<Zone, Integer> landings, Map<Zone, Integer> receptions) {}
    public record Plan(int planned, int completed, int unfinished) {}
    public record Blocks(int predictions, int attackerChecks, int attackerCorrect, int tempoChecks, int tempoCorrect,
            int contacts, int points, int outLosses, int continued) {}
    private static final class Counts {
        int attempts;
        final EnumMap<Outcome, Integer> outcomes = new EnumMap<>(Outcome.class);
        final EnumMap<PointReason, Integer> points = new EnumMap<>(PointReason.class);
        final EnumMap<Zone, Integer> landings = new EnumMap<>(Zone.class), receptions = new EnumMap<>(Zone.class);
        Summary snapshot() { return new Summary(attempts, Map.copyOf(outcomes), Map.copyOf(points), Map.copyOf(landings), Map.copyOf(receptions)); }
    }
    private final Map<Key, Counts> shots = new HashMap<>();
    private final Map<Detail, Counts> details = new HashMap<>();
    private final Map<TacticKey, Integer> tactics = new HashMap<>();
    private final EnumMap<Tactic, int[]> plans = new EnumMap<>(Tactic.class);
    private final EnumSet<Tactic> pendingPlans = EnumSet.noneOf(Tactic.class);
    private final EnumMap<Setting, Integer> recognitions = new EnumMap<>(Setting.class);
    private int multiRecognitions;
    private int predictions, attackerChecks, attackerCorrect, tempoChecks, tempoCorrect, blockContacts, blockPoints, blockOutLosses, blockContinued;

    void attempt(Key key, Context context) {
        shots.computeIfAbsent(key, ignored -> new Counts()).attempts++;
        details.computeIfAbsent(new Detail(key, context), ignored -> new Counts()).attempts++;
    }
    void result(Key key, Context context, Outcome outcome, PointReason reason, Zone landing, Zone reception) {
        record(shots.get(key), outcome, reason, landing, reception);
        record(details.get(new Detail(key, context)), outcome, reason, landing, reception);
    }
    private static void record(Counts c, Outcome outcome, PointReason reason, Zone landing, Zone reception) {
        c.outcomes.merge(outcome, 1, Integer::sum);
        if (reason != PointReason.NONE) c.points.merge(reason, 1, Integer::sum);
        if (landing != null) c.landings.merge(landing, 1, Integer::sum);
        if (reception != null) c.receptions.merge(reception, 1, Integer::sum);
    }
    void plan(Tactic tactic) {
        if (pendingPlans.add(tactic)) plans.computeIfAbsent(tactic, ignored -> new int[3])[0]++;
    }
    void complete(Tactic tactic) {
        if (pendingPlans.remove(tactic)) plans.get(tactic)[1]++;
    }
    void tactic(boolean own, Tactic tactic) { tactics.merge(new TacticKey(own, tactic), 1, Integer::sum); }
    void finishPlans() {
        for (Tactic tactic : pendingPlans) plans.get(tactic)[2]++;
        pendingPlans.clear();
    }
    void predictionStarted() { predictions++; }
    void checkPrediction(int guessedRole, int actualRole, Tempo guessedTempo, Tempo actualTempo) {
        attackerChecks++; if (guessedRole == actualRole) attackerCorrect++;
        if (guessedTempo != Tempo.UNKNOWN && actualTempo != Tempo.UNKNOWN) {
            tempoChecks++; if (guessedTempo == actualTempo) tempoCorrect++;
        }
    }
    void blockContact() { blockContacts++; }
    void blockResult(boolean point, boolean outLoss) {
        if (point) blockPoints++; else if (outLoss) blockOutLosses++; else blockContinued++;
    }
    void recognized(Setting setting, int participants) {
        if (setting == Setting.AIR || setting == Setting.JUMP) recognitions.merge(setting, 1, Integer::sum);
        if (participants >= 2) multiRecognitions++;
    }
    public Map<Key, Summary> shots() {
        Map<Key, Summary> result = new HashMap<>(); shots.forEach((key, value) -> result.put(key, value.snapshot()));
        return Map.copyOf(result);
    }
    public Map<Detail, Summary> details() {
        Map<Detail, Summary> result = new HashMap<>(); details.forEach((key, value) -> result.put(key, value.snapshot()));
        return Map.copyOf(result);
    }
    public Map<Tactic, Plan> plans() {
        Map<Tactic, Plan> result = new EnumMap<>(Tactic.class);
        plans.forEach((key, value) -> result.put(key, new Plan(value[0], value[1], value[2])));
        return Map.copyOf(result);
    }
    public Map<TacticKey, Integer> tactics() { return Map.copyOf(tactics); }
    public Blocks blocks() { return new Blocks(predictions, attackerChecks, attackerCorrect, tempoChecks, tempoCorrect,
            blockContacts, blockPoints, blockOutLosses, blockContinued); }
    public Map<Setting, Integer> recognitions() { return Map.copyOf(recognitions); }
    public int multiRecognitions() { return multiRecognitions; }

    public double selectionWeight(Key key, Context context, int attackAbility) {
        if (!key.own() || key.serve() || key.touch() < 2 || key.touch() > 3
                || attackAbility < AiTuning.MIN_TACTICAL_ATTACK) return 1;
        double base = weight(shots.get(key));
        return Math.max(base, context == null ? 1 : weight(details.get(new Detail(key, context))));
    }
    private static double weight(Counts c) {
        if (c == null || c.attempts < AiTuning.MIN_EFFECTIVE_SHOTS) return 1;
        return 1 + AiTuning.MAX_LEARNED_SELECTION_BOOST * c.outcomes.getOrDefault(Outcome.DIRECT_POINT, 0)
                / (c.attempts + AiTuning.HABIT_PRIOR_SAMPLES);
    }
    static Zone zone(double x) {
        if (!ScoringLogic.isBallInCourt(x, GameConfig.BALL_RADIUS)) return Zone.OUT;
        double distance = Math.abs(x - GameConfig.NET_X);
        return distance <= GameConfig.THREE_METER_PX ? Zone.FRONT
                : distance <= 2 * GameConfig.THREE_METER_PX ? Zone.MIDDLE : Zone.DEEP;
    }
    void reset() {
        shots.clear(); details.clear(); tactics.clear(); plans.clear(); pendingPlans.clear(); recognitions.clear(); multiRecognitions = 0;
        predictions = attackerChecks = attackerCorrect = tempoChecks = tempoCorrect = blockContacts = blockPoints = blockOutLosses = blockContinued = 0;
    }
}
