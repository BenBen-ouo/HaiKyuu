package model.ai;

import java.util.Random;
import java.util.random.RandomGenerator;
import model.ControlInput;
import model.GameConfig;
import model.serve.ServeState;

/** 每個對局一個 AI；不持有／修改 GameModel，只消費公開場況並產生操作。 */
public final class RuleBasedAi {
    public enum State { WAITING, SERVING, RECEIVING, DEFENDING, ORGANIZING, ATTACKING, FINISHED }
    private final AiDifficulty difficulty;
    private final boolean redSide;
    private final RandomGenerator attackRandom;
    private final OpponentHabits habits = new OpponentHabits();
    private final DefensePlanner defense;
    private final OffensePlanner offense;
    private final FirstBallAttackPlanner firstBallAttack;
    private final ServePlanner serve;
    private final AiMatchLearning learning;
    private AiObservation previous;
    private int tick;
    private int offenseReadyAt;
    private State state = State.WAITING;

    public RuleBasedAi(boolean redSide, AiDifficulty difficulty) {
        this(redSide, difficulty, new Random());
    }
    /** 固定亂數來源用於可重現的行為驗證；正式遊戲每場使用新來源。 */
    public RuleBasedAi(boolean redSide, AiDifficulty difficulty, RandomGenerator random) {
        AiTuning.validate();
        this.redSide = redSide;
        this.difficulty = java.util.Objects.requireNonNull(difficulty);
        learning = new AiMatchLearning(redSide, habits);
        java.util.Objects.requireNonNull(random);
        // 分流亂數，避免調整進攻方案的抽樣次數改變防守誤差序列。
        attackRandom = new Random(random.nextLong());
        RandomGenerator defenseRandom = new Random(random.nextLong());
        defense = new DefensePlanner(defenseRandom);
        offense = new OffensePlanner(attackRandom, habits, learning);
        firstBallAttack = new FirstBallAttackPlanner(attackRandom);
        serve = new ServePlanner(attackRandom);
    }
    public ControlInput update(AiObservation o) {
        tick++;
        learning.observe(o, tick);
        ControlInput input = new ControlInput();
        boolean firstBallAttackHandled = false;
        boolean waiting = o.serveState() == ServeState.WAITING_FOR_SERVE;
        boolean newBall = previous == null || waiting && (previous.serveState() != o.serveState()
                || previous.rallyOver() && !o.rallyOver());
        if (newBall) {
            offense.clear();
            firstBallAttack.reset();
            defense.reset();
            serve.begin(tick, difficulty);
            defense.react(tick, difficulty, habits, o);
        }
        defense.observe(o);
        defense.recognize(learning.reading(), tick, difficulty, learning.statistics());
        if (o.matchOver()) state = State.FINISHED;
        else if (o.rallyOver()) state = State.WAITING;
        else if (o.ownServing() && (waiting || o.serveState() == ServeState.JUMP_TOSS)) {
            state = State.SERVING;
            if (waiting) learning.statistics().plan(AiMatchStatistics.Tactic.SERVE);
            serve.update(o, input, tick);
        } else if (waiting) {
            state = State.RECEIVING;
            defense.waitForServe(o, input, difficulty, habits);
        } else {
            boolean changedFlight = previous == null || o.netContact()
                    || Math.abs(o.ball().velocityX() - previous.ball().velocityX()) > AiTuning.BALL_FLIGHT_CHANGE_TOLERANCE
                    || Math.abs(o.ball().velocityY() - previous.ball().velocityY() - GameConfig.GRAVITY)
                        > AiTuning.BALL_FLIGHT_CHANGE_TOLERANCE;
            boolean ownFirst = o.ownTouches() == 1 && (previous == null || previous.ownTouches() != 1);
            if (ownFirst) {
                offenseReadyAt = tick + AiDifficulty.reactionFrames(difficulty.attack(), attackRandom);
                offense.begin(o, tick, difficulty);
                defense.beginOwnAttack(difficulty);
            }
            if (o.ownTouches() == 2 && previous != null && previous.ownTouches() != 2) {
                offense.onSet(o, tick, difficulty);
            }
            if (o.ownTouches() > 0 && !o.attackLocked() && o.lastTouchOwn()) {
                state = o.ownTouches() == 1 ? State.ORGANIZING : State.ATTACKING;
                if (tick >= offenseReadyAt) offense.update(o, input, tick, difficulty);
                if (tick >= offenseReadyAt && !offense.isBackReserved()
                        && !input.wingActionPressed && !input.mbActionPressed && !input.backJump) {
                    defense.coverOwnAttack(o, input);
                }
            } else {
                state = o.receptionComplete() ? State.DEFENDING : State.RECEIVING;
                if (changedFlight) defense.react(tick, difficulty, habits, o);
                if (changedFlight) firstBallAttack.react();
                firstBallAttackHandled = firstBallAttack.update(o, input, tick, difficulty, learning);
                if (firstBallAttackHandled) state = State.ATTACKING;
                // 發球後尚未被接起，不能讓自己的球員追球觸發發球犯規。
                if (!firstBallAttackHandled && (o.receptionComplete() || !o.ownServing()))
                    defense.update(o, input, tick, difficulty, habits);
                else if (!firstBallAttackHandled) DefensePlanner.move(input, o.own().get(0), GameConfig.NET_X + GameConfig.RED_BACK_OFFSET_X
                        + o.own().get(0).receiveOffsetX() + o.own().get(0).receiveWidth() / 2);
            }
        }
        // 與鍵盤的共鍵關係一致，不能讓 AI 使用人類無法同時輸入的方向／球路組合。
        if (input.spikeFlat) input.backRight = true;
        if (input.airSetModifier) { input.backLeft = true; input.backRight = true; }
        input.airSetModifier = input.backLeft && input.backRight;
        input.spikeFlat = input.backRight;
        input.servePressed |= input.backJump || input.backDive;
        input.backJump = input.servePressed;
        input.backDive = input.servePressed;
        if (input.mbActionPressed && o.ownTouches() == 0 && !firstBallAttackHandled)
            learning.rememberPrediction(defense.predictedRole(), defense.predictedTempo());
        previous = o;
        return redSide ? input : input.mirroredHorizontally();
    }

    public void reset() {
        habits.reset();
        defense.reset();
        offense.clear();
        firstBallAttack.reset();
        previous = null;
        tick = 0;
        offenseReadyAt = 0;
        learning.reset();
        state = State.WAITING;
    }
    public State state() { return state; }
    public AiDifficulty difficulty() { return difficulty; }
    public boolean isRedSide() { return redSide; }
    public int learnedAttacks() { return habits.attackSamples(); }
    public int learnedAttackLandings() { return habits.attackLandingSamples(); }
    public AiMatchStatistics statistics() { return learning.statistics(); }
}
