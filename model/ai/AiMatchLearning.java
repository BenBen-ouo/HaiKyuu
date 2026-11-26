package model.ai;

import java.util.Arrays;
import model.GameConfig;
import model.player.PlayerAction;
import model.rally.ContactEvent;
import static model.ai.AiMatchStatistics.*;

/** 依確認事件串流歸因；一般接起即結束前次攻擊歸因，不把後續反攻失誤倒算回去。 */
final class AiMatchLearning {
    record Reading(long revision, Setting setting, int participants, int setTick, int backTakeoff, int mbTakeoff, int wingTakeoff) {
        Tempo tempo(int role) {
            int takeoff = role == 0 ? backTakeoff : role == 2 ? mbTakeoff : wingTakeoff;
            return classify(takeoff, setTick);
        }
    }
    private static final class Scene {
        final int[] takeoff = {-1, -1, -1, -1};
        int preparedMask, setTick = -1;
        Setting setting = Setting.NONE;
        boolean countedMulti;
        void clear() { Arrays.fill(takeoff, -1); preparedMask = 0; setTick = -1; setting = Setting.NONE; countedMulti = false; }
    }
    private static final class Trial {
        final Key key;
        final Context context;
        boolean blocked, hitNet;
        Trial(Key key, Context context) { this.key = key; this.context = context; }
    }
    private final boolean redSide;
    private final OpponentHabits habits;
    private final AiMatchStatistics stats = new AiMatchStatistics();
    private final Scene[] scenes = {new Scene(), new Scene()};
    private final Trial[] pending = new Trial[2];
    private final boolean[] pendingBlock = new boolean[2];
    private AiObservation previous;
    private long lastContactId, revision;
    private boolean predictionWaiting, predictionQueued;
    private int guessedRole;
    private Tempo guessedTempo = Tempo.UNKNOWN;

    AiMatchLearning(boolean redSide, OpponentHabits habits) { this.redSide = redSide; this.habits = habits; }
    AiMatchStatistics statistics() { return stats; }
    Reading reading() {
        var s = scenes[1];
        return new Reading(revision, s.setting, Integer.bitCount(s.preparedMask), s.setTick, s.takeoff[0], s.takeoff[2], s.takeoff[3]);
    }
    void rememberPrediction(int role, int tempo) {
        guessedRole = role; guessedTempo = tempo < 0 ? Tempo.UNKNOWN : Tempo.values()[tempo]; predictionQueued = true;
    }
    Context context(AiObservation o, int side, int role) {
        return context(o, side, role, o.ball());
    }
    private Context context(AiObservation o, int side, int role, BallForecast flight) {
        var s = scenes[side];
        var opponents = side == 0 ? o.opponent() : o.own();
        double landing = flight.xAt(flight.descendingArrival(GameConfig.FLOOR_Y - GameConfig.BALL_RADIUS));
        double gap = Math.min(Math.abs(landing - opponents.get(0).receiveX()), Math.abs(landing - opponents.get(3).receiveX()));
        return new Context(classify(s.takeoff[role], s.setTick), s.setting,
                Math.max(1, Math.min(3, Integer.bitCount(s.preparedMask))),
                opponents.get(2).action() == PlayerAction.BLOCK, gap > GameConfig.THREE_METER_PX / 2);
    }
    void observe(AiObservation o, int tick) {
        if (previous != null && previous.contactStreamId() != o.contactStreamId()) {
            for (Scene scene : scenes) scene.clear();
            Arrays.fill(pending, null); Arrays.fill(pendingBlock, false);
            stats.finishPlans(); lastContactId = 0; previous = null; predictionQueued = predictionWaiting = false;
        }
        observePoses(o, tick);
        for (ContactEvent e : o.contacts()) {
            if (e.id() <= lastContactId) continue;
            lastContactId = e.id();
            int side = e.redSide() == redSide ? 0 : 1;
            double x = redSide ? e.x() : GameConfig.SCREEN_WIDTH - e.x();
            process(o, e, side, x, tick);
        }
        if (o.netContact()) for (Trial trial : pending) if (trial != null) trial.hitNet = true;
        if (previous != null && (o.ownScore() != previous.ownScore() || o.opponentScore() != previous.opponentScore())) {
            finishPoint(o, o.ownScore() > previous.ownScore());
        }
        previous = o;
    }
    private void observePoses(AiObservation o, int tick) {
        for (int side = 0; side < 2; side++) {
            var poses = side == 0 ? o.own() : o.opponent();
            var old = previous == null ? null : side == 0 ? previous.own() : previous.opponent();
            var s = scenes[side];
            int oldParticipants = Integer.bitCount(s.preparedMask);
            for (int role : new int[]{0, 2, 3}) {
                var p = poses.get(role);
                boolean preparing = p.action() == PlayerAction.ATTACK_READY || p.action() == PlayerAction.RUN_APPROACH
                        || p.action() == PlayerAction.ATTACK_SWING || p.action() == PlayerAction.AIR_SETTING;
                if (preparing) s.preparedMask |= 1 << role;
                if (preparing && p.jumping() && (old == null || !old.get(role).jumping())) s.takeoff[role] = tick;
            }
            if (side == 1 && oldParticipants < 2 && Integer.bitCount(s.preparedMask) >= 2) revision++;
        }
        if (predictionQueued && o.own().get(2).action() == PlayerAction.BLOCK
                && (previous == null || previous.own().get(2).action() != PlayerAction.BLOCK)) {
            stats.predictionStarted(); predictionWaiting = true; predictionQueued = false;
        }
    }
    private void process(AiObservation o, ContactEvent e, int side, double x, int tick) {
        int other = 1 - side;
        var s = scenes[side];
        if (e.kind() == ContactEvent.Kind.BLOCK) {
            closeBlock(0, false, false); closeBlock(1, false, false);
            if (pending[side] != null) close(side, Outcome.BLOCK_RETURNED, PointReason.NONE, null, null);
            if (pending[other] != null) pending[other].blocked = true;
            pendingBlock[side] = true;
            if (side == 0) stats.blockContact();
            return;
        }
        if (pending[other] != null) close(other,
                e.kind() == ContactEvent.Kind.REGULAR || e.kind() == ContactEvent.Kind.AIR_SET ? Outcome.SAVED : Outcome.OTHER,
                PointReason.NONE, null, zone(x));
        if (pending[side] != null) close(side, pending[side].blocked ? Outcome.BLOCK_RETURNED : Outcome.OTHER,
                PointReason.NONE, null, zone(x));
        closeBlock(0, false, false); closeBlock(1, false, false);
        if (e.kind() == ContactEvent.Kind.SERVE) {
            for (Scene scene : scenes) scene.clear();
            revision++; predictionWaiting = predictionQueued = false;
        }
        if (e.kind() == ContactEvent.Kind.REGULAR && e.touch() == 1) {
            s.clear(); s.preparedMask = e.preparedMask();
            if (side == 0) stats.finishPlans(); else { revision++; predictionWaiting = false; }
        }
        s.preparedMask |= e.preparedMask();
        if (e.kind() == ContactEvent.Kind.AIR_SET || e.kind() == ContactEvent.Kind.REGULAR && e.role() == 1 && e.touch() == 2) {
            s.setTick = tick;
            s.setting = e.kind() == ContactEvent.Kind.AIR_SET ? Setting.AIR : e.jumping() ? Setting.JUMP : Setting.STANDING;
            if (s.setting == Setting.AIR || s.setting == Setting.JUMP) {
                Tactic tactic = s.setting == Setting.AIR ? Tactic.AIR_SET : Tactic.JUMP_SET;
                stats.tactic(side == 0, tactic);
                if (side == 0) stats.complete(tactic); else { habits.recordTactic(s.setting, false); revision++; }
            }
        }
        boolean serve = e.kind() == ContactEvent.Kind.SERVE;
        boolean shot = serve || e.kind() == ContactEvent.Kind.ATTACK
                || e.kind() == ContactEvent.Kind.REGULAR && e.touch() == 3;
        if (!shot) return;
        Context context = context(o, side, e.role(), new BallForecast(x, e.y(), redSide ? e.vx() : -e.vx(), e.vy()));
        var key = new Key(side == 0, e.role(), serve ? 0 : e.touch(), e.route(), serve);
        stats.attempt(key, context);
        pending[side] = new Trial(key, context);
        Tactic tactic = serve ? Tactic.SERVE : e.kind() == ContactEvent.Kind.REGULAR ? Tactic.FREE_BALL
                : e.touch() == 2 ? Tactic.SECOND_ATTACK : Tactic.ATTACK;
        stats.tactic(side == 0, tactic);
        if (side == 0) {
            stats.complete(tactic);
            if (context.participants() >= 2) stats.complete(Tactic.MULTI);
            stats.finishPlans();
        }
        if (context.participants() >= 2 && !s.countedMulti && !serve) {
            stats.tactic(side == 0, Tactic.MULTI); s.countedMulti = true;
            if (side == 1) habits.recordTactic(Setting.NONE, true);
        }
        if (side == 1 && e.kind() == ContactEvent.Kind.ATTACK) {
            habits.recordAttack(e.role() == 0 ? 0 : e.role() == 2 ? 1 : 2,
                    context.tempo() == Tempo.UNKNOWN ? -1 : context.tempo().ordinal(), routeIndex(e.route()));
            if (predictionWaiting) {
                stats.checkPrediction(guessedRole, e.role(), guessedTempo, context.tempo()); predictionWaiting = false;
            }
        } else if (side == 1 && serve) habits.recordServe(serveIndex(e.route()));
    }
    private void finishPoint(AiObservation o, boolean ownWon) {
        boolean ordinary = "IN".equals(o.judgment()) || "OUT".equals(o.judgment()) || "TOUCH OUT".equals(o.judgment());
        var landing = o.landing() ? zone(o.ball().x()) : null;
        for (int side = 0; side < 2; side++) {
            var p = pending[side];
            if (p != null) {
                boolean won = side == 0 ? ownWon : !ownWon;
                Outcome result = won ? ordinary ? Outcome.DIRECT_POINT : Outcome.OTHER
                        : p.blocked ? Outcome.BLOCKED_LOSS : Outcome.SELF_ERROR;
                PointReason reason = result != Outcome.DIRECT_POINT ? PointReason.NONE : p.key.serve() ? PointReason.SERVE
                        : p.blocked ? PointReason.TOUCH_OUT : PointReason.LANDING;
                if (side == 1 && o.landing() && !p.blocked && !p.hitNet) {
                    if (p.key.serve()) habits.recordLanding(true, o.ball().x());
                    else if (p.key.route() != ContactEvent.Route.FREE_BALL) habits.recordAttackLanding(
                            p.key.role() == 0 ? 0 : p.key.role() == 2 ? 1 : 2,
                            p.context.tempo() == Tempo.UNKNOWN ? -1 : p.context.tempo().ordinal(), o.ball().x());
                }
                close(side, result, reason, landing, null);
            }
            closeBlock(side, ordinary && (side == 0 ? ownWon : !ownWon),
                    ordinary && !(side == 0 ? ownWon : !ownWon) && "TOUCH OUT".equals(o.judgment()));
        }
        stats.finishPlans(); predictionWaiting = predictionQueued = false;
    }
    private void close(int side, Outcome outcome, PointReason reason, Zone landing, Zone reception) {
        Trial p = pending[side];
        stats.result(p.key, p.context, outcome, reason, landing, reception); pending[side] = null;
    }
    private void closeBlock(int side, boolean point, boolean out) {
        if (pendingBlock[side] && side == 0) stats.blockResult(point, out);
        pendingBlock[side] = false;
    }
    static Tempo classify(int takeoff, int setTick) {
        if (takeoff < 0 || setTick < 0) return Tempo.UNKNOWN;
        int difference = takeoff - setTick;
        return difference < -AiTuning.SIMULTANEOUS_TAKEOFF_WINDOW_FRAMES ? Tempo.NEGATIVE
                : difference <= AiTuning.SIMULTANEOUS_TAKEOFF_WINDOW_FRAMES ? Tempo.FIRST : Tempo.SECOND;
    }
    static int routeIndex(ContactEvent.Route route) {
        return switch (route) { case NORMAL -> 0; case FLAT -> 1; case SHORT -> 2; case LOB -> 3; case LONG_LOB -> 4; default -> 0; };
    }
    private static int serveIndex(ContactEvent.Route route) {
        return switch (route) { case SERVE_NORMAL -> 0; case SERVE_LOW -> 1; case SERVE_SHORT -> 2;
            case JUMP_NORMAL -> 3; case SERVE_CEILING -> 5; default -> 4; };
    }
    void reset() {
        stats.reset(); for (Scene scene : scenes) scene.clear(); Arrays.fill(pending, null); Arrays.fill(pendingBlock, false);
        previous = null; lastContactId = revision = 0; predictionWaiting = predictionQueued = false;
    }
}
