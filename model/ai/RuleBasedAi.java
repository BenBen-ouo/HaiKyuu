package model.ai;

import java.util.Random;
import java.util.random.RandomGenerator;
import model.ControlInput;
import model.GameConfig;
import model.player.PlayerAction;
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
    private final ServePlanner serve;
    private final int[] opponentTakeoff = new int[4];
    private AiObservation previous;
    private int tick;
    private int offenseReadyAt;
    private int opponentSetAt = -1;
    private boolean lastOpponentShotWasServe;
    private boolean pendingLanding;
    private int pendingAttackRole;
    private int pendingAttackTempo;
    private State state = State.WAITING;

    public RuleBasedAi(boolean redSide, AiDifficulty difficulty) {
        this(redSide, difficulty, new Random());
    }
    /** 固定亂數來源用於可重現的行為驗證；正式遊戲每場使用新來源。 */
    public RuleBasedAi(boolean redSide, AiDifficulty difficulty, RandomGenerator random) {
        AiTuning.validate();
        this.redSide = redSide;
        this.difficulty = java.util.Objects.requireNonNull(difficulty);
        java.util.Arrays.fill(opponentTakeoff, -1);
        java.util.Objects.requireNonNull(random);
        // 分流亂數，避免調整進攻方案的抽樣次數改變防守誤差序列。
        attackRandom = new Random(random.nextLong());
        RandomGenerator defenseRandom = new Random(random.nextLong());
        defense = new DefensePlanner(defenseRandom);
        offense = new OffensePlanner(attackRandom, habits);
        serve = new ServePlanner(attackRandom);
    }
    public ControlInput update(AiObservation o) {
        tick++;
        observeHabits(o);
        ControlInput input = new ControlInput();
        boolean waiting = o.serveState() == ServeState.WAITING_FOR_SERVE;
        boolean newBall = previous == null || waiting && (previous.serveState() != o.serveState()
                || previous.rallyOver() && !o.rallyOver());
        if (newBall) {
            offense.clear();
            defense.reset();
            serve.begin(tick, difficulty);
            defense.react(tick, difficulty, habits, o);
        }
        if (o.matchOver()) state = State.FINISHED;
        else if (o.rallyOver()) state = State.WAITING;
        else if (o.ownServing() && (waiting || o.serveState() == ServeState.JUMP_TOSS)) {
            state = State.SERVING;
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
                // 發球後尚未被接起，不能讓自己的球員追球觸發發球犯規。
                if (o.receptionComplete() || !o.ownServing()) defense.update(o, input, tick, difficulty, habits);
                else DefensePlanner.move(input, o.own().get(0), GameConfig.NET_X + GameConfig.RED_BACK_OFFSET_X
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
        previous = o;
        return redSide ? input : input.mirroredHorizontally();
    }

    private void observeHabits(AiObservation o) {
        if (previous == null) return;
        if (o.serveState() == ServeState.WAITING_FOR_SERVE) {
            pendingLanding = false;
            opponentSetAt = -1;
            java.util.Arrays.fill(opponentTakeoff, -1);
            return;
        }
        if (o.opponentTouches() == 1 && previous.opponentTouches() != 1) {
            opponentSetAt = -1;
            java.util.Arrays.fill(opponentTakeoff, -1);
        }
        var setter = o.opponent().get(1);
        if (o.opponentTouches() == 2 && setter.action() == PlayerAction.SETTING
                && previous.opponent().get(1).action() != PlayerAction.SETTING) {
            opponentSetAt = tick;
        }
        int[] roles = {0, 2, 3};
        for (int i = 0; i < roles.length; i++) {
            int role = roles[i];
            var p = o.opponent().get(role);
            var old = previous.opponent().get(role);
            if (p.jumping() && !old.jumping()) opponentTakeoff[role] = tick;
            if (p.action() == PlayerAction.ATTACK_SWING && old.action() != PlayerAction.ATTACK_SWING) {
                // 發球不混入攻擊者／節奏統計。
                if (o.receptionComplete()) {
                    int difference = opponentTakeoff[role] - opponentSetAt;
                    int tempo = opponentSetAt < 0 || opponentTakeoff[role] < 0 ? 2
                            : difference < -AiTuning.SIMULTANEOUS_TAKEOFF_WINDOW_FRAMES ? 0
                            : difference <= AiTuning.SIMULTANEOUS_TAKEOFF_WINDOW_FRAMES ? 1 : 2;
                    habits.recordAttack(i, tempo, observedShot(o.ball()));
                    lastOpponentShotWasServe = false;
                    pendingLanding = !o.lastTouchOwn();
                    pendingAttackRole = i;
                    pendingAttackTempo = tempo;
                }
            }
        }
        if (!o.ownServing() && (previous.serveState() == ServeState.WAITING_FOR_SERVE
                || previous.serveState() == ServeState.JUMP_TOSS)
                && (o.serveState() == ServeState.SERVE_LAUNCHED || o.serveState() == ServeState.IN_PLAY)) {
            lastOpponentShotWasServe = true;
            pendingLanding = true;
            if (previous.serveState() == ServeState.JUMP_TOSS) {
                habits.recordServe(Math.abs(o.ball().velocityY() - GameConfig.GRAVITY - GameConfig.SERVE_JUMP_VELOCITY_Y) < .01
                        && Math.abs(o.ball().velocityX()) == GameConfig.SERVE_JUMP_HORIZONTAL_SPEED ? 3 : 4);
            } else {
                double[] velocities = {GameConfig.SERVE_NORMAL_VELOCITY_Y, GameConfig.SERVE_LOW_NET_VELOCITY_Y,
                        GameConfig.SERVE_SHORT_VELOCITY_Y, GameConfig.SERVE_CEILING_VELOCITY_Y};
                int best = 0;
                for (int i = 1; i < velocities.length; i++) {
                    if (Math.abs(o.ball().velocityY() - velocities[i]) < Math.abs(o.ball().velocityY() - velocities[best])) best = i;
                }
                habits.recordServe(best == 3 ? 5 : best);
            }
        }
        if (pendingLanding && (o.lastTouchOwn() || o.netContact())) pendingLanding = false;
        if (pendingLanding && o.landing() && !previous.landing() && !o.lastTouchOwn()) {
            if (lastOpponentShotWasServe) habits.recordLanding(true, o.ball().x());
            else habits.recordAttackLanding(pendingAttackRole, pendingAttackTempo, o.ball().x());
            pendingLanding = false;
        }
    }
    private static int observedShot(BallForecast ball) {
        if (ball.velocityY() < 0) return Math.abs(ball.velocityX()) < GameConfig.LONG_LOB_SPIKE_HORIZONTAL_SPEED ? 3 : 4;
        if (Math.abs(ball.velocityX()) > GameConfig.SPIKE_HORIZONTAL_SPEED) return 1;
        return ball.velocityY() > GameConfig.SPIKE_VELOCITY_Y + 1 ? 2 : 0;
    }
    public void reset() {
        habits.reset();
        defense.reset();
        offense.clear();
        previous = null;
        tick = 0;
        offenseReadyAt = 0;
        opponentSetAt = -1;
        pendingLanding = false;
        lastOpponentShotWasServe = false;
        java.util.Arrays.fill(opponentTakeoff, -1);
        state = State.WAITING;
    }
    public State state() { return state; }
    public AiDifficulty difficulty() { return difficulty; }
    public boolean isRedSide() { return redSide; }
    public int learnedAttacks() { return habits.attackSamples(); }
    public int learnedAttackLandings() { return habits.attackLandingSamples(); }
}
