package model.ai;

import java.util.random.RandomGenerator;
import model.ControlInput;
import model.GameConfig;
import model.player.PlayerAction;
import model.player.DiveController;

/** 防守分工、帶誤差的來球估計、公開動作預判及落地後補攔。 */
final class DefensePlanner {
    private final RandomGenerator random;
    private double receiveError;
    private double blockError;
    private int predictedAttacker;
    private int predictedTempo;
    private int blockAt = Integer.MAX_VALUE;
    private int readyAt;
    private int nextTrack;
    private double targetX;
    private boolean wingAssigned;
    private int lastVisibleAttacker = -1;
    private boolean targetValid;
    private boolean assignReceiver;
    private boolean coverOwnAttack;

    DefensePlanner(RandomGenerator random) { this.random = random; }

    void reset() { targetValid = false; assignReceiver = true; wingAssigned = false; coverOwnAttack = false; }

    void beginOwnAttack(AiDifficulty difficulty) {
        coverOwnAttack = random.nextDouble() < AiTuning.MAX_ATTACK_COVER_PROBABILITY
                * AiTuning.unlockedProgress(difficulty.defense(), AiTuning.MIN_ATTACK_COVER_DEFENSE);
    }

    void coverOwnAttack(AiObservation o, ControlInput input) {
        if (!coverOwnAttack) return;
        var back = o.own().get(0);
        double middle = (GameConfig.COURT_LEFT_X + GameConfig.NET_X - GameConfig.THREE_METER_PX) / 2;
        if (back.canStart() && back.centerX() < middle - AiTuning.MOVEMENT_DEAD_ZONE_PX) {
            move(input, back, middle + back.receiveOffsetX() + back.receiveWidth() / 2 - back.width() / 2);
        }
    }

    void waitForServe(AiObservation o, ControlInput input, AiDifficulty difficulty, OpponentHabits habits) {
        moveToReadyPosition(o, input, difficulty, habits, true);
    }

    private void moveToReadyPosition(AiObservation o, ControlInput input, AiDifficulty difficulty,
            OpponentHabits habits, boolean serve) {
        double skill = AiDifficulty.ability(difficulty.defense());
        var back = o.own().get(0);
        double front = GameConfig.NET_X - GameConfig.THREE_METER_PX;
        double centerOffset = back.receiveOffsetX() + back.receiveWidth() / 2 - back.width() / 2;
        double middle = (GameConfig.COURT_LEFT_X + front) / 2;
        double bias = serve ? (habits.serveWeight(2) - habits.serveWeight(5))
                * AiTuning.SERVE_HABIT_POSITION_BIAS_PX * skill : 0;
        double expected = habits.expectedLanding(serve, middle + centerOffset + bias);
        if (!serve && habits.shouldKeepBackForReturn(difficulty.defense())) {
            expected = habits.expectedFastDeepLanding(middle + centerOffset);
        }
        double desiredCenter = middle + (expected - centerOffset - middle) * skill;
        double margin = (front - GameConfig.COURT_LEFT_X) * AiTuning.SERVE_POSITION_EDGE_MARGIN_RATIO;
        if (!serve) margin = margin * (1 - skill * habits.fastDeepRisk())
                + GameConfig.BALL_RADIUS * skill * habits.fastDeepRisk();
        desiredCenter = Math.max(GameConfig.COURT_LEFT_X + margin, Math.min(front - margin, desiredCenter));
        targetX = desiredCenter + centerOffset;
        targetValid = true;
        move(input, back, targetX);
    }

    void react(int tick, AiDifficulty difficulty, OpponentHabits habits, AiObservation o) {
        double skill = AiDifficulty.ability(difficulty.defense());
        readyAt = tick + AiDifficulty.reactionFrames(difficulty.defense(), random);
        double uncertainty = (1 - skill) * (1 - skill);
        receiveError = signedError(AiTuning.DEFENSE_ERROR_HIGH_PX
                + uncertainty * (AiTuning.DEFENSE_ERROR_LOW_PX - AiTuning.DEFENSE_ERROR_HIGH_PX));
        blockError = signedError(AiTuning.BLOCK_TIMING_ERROR_HIGH_FRAMES
                + uncertainty * (AiTuning.BLOCK_TIMING_ERROR_LOW_FRAMES - AiTuning.BLOCK_TIMING_ERROR_HIGH_FRAMES));
        predictedAttacker = weightedRole(habits, skill);
        predictedTempo = difficulty.defense() < AiTuning.MIN_TEMPO_PREDICTION_DEFENSE
                ? 2 : weightedTempo(habits, skill);
        blockAt = Integer.MAX_VALUE;
        lastVisibleAttacker = -1;
        if (!targetValid) targetX = !o.receptionComplete()
                ? habits.expectedLanding(true, GameConfig.NET_X - AiTuning.DEFAULT_DEFENSE_DISTANCE_FROM_NET_PX)
                : habits.expectedAttackLanding(predictedAttacker, predictedTempo,
                        GameConfig.NET_X - AiTuning.DEFAULT_DEFENSE_DISTANCE_FROM_NET_PX);
        // 反彈重新反應前保留原接球責任與目標，不立即退回習慣站位。
        assignReceiver = true;
        nextTrack = readyAt;
    }

    void update(AiObservation o, ControlInput input, int tick, AiDifficulty difficulty, OpponentHabits habits) {
        var back = o.own().get(0);
        int floorArrival = o.ball().descendingArrival(GameConfig.FLOOR_Y - GameConfig.BALL_RADIUS);
        boolean organizing = o.receptionComplete() && !o.lastTouchOwn() && o.opponentTouches() > 0
                && o.ball().x() > GameConfig.NET_X + GameConfig.BALL_RADIUS
                && o.ball().xAt(floorArrival) > GameConfig.NET_X + GameConfig.BALL_RADIUS;
        boolean ownShotOutgoing = o.attackLocked() && o.lastTouchOwn()
                && o.ball().velocityX() > 0
                && o.ball().xAt(floorArrival) > GameConfig.NET_X + GameConfig.BALL_RADIUS;
        if (organizing || ownShotOutgoing) {
            moveToReadyPosition(o, input, difficulty, habits, false);
            wingAssigned = false;
            assignReceiver = true;
            targetValid = true;
            if (tick >= readyAt) block(o, input, tick, difficulty, habits);
            return;
        }
        if (tick < readyAt) {
            move(input, back, targetX);
            return;
        }
        if (tick >= nextTrack) {
            nextTrack = tick + AiTuning.TRACK_INTERVAL_FRAMES;
            int arrival = o.ball().descendingArrival(GameConfig.PLAYER_BASE_Y + back.receiveOffsetY()
                    + back.receiveHeight() / 2);
            targetX = o.ball().xAt(arrival) + receiveError;
            double shortBoundary = GameConfig.NET_X - AiTuning.SHORT_DEFENSE_DISTANCE_FROM_NET_PX
                    - AiTuning.LOB_HABIT_POSITION_BIAS_PX * AiDifficulty.ability(difficulty.defense())
                        * (habits.directionWeight(2) + habits.lobRate());
            if (assignReceiver || wingAssigned) {
                // 每次追蹤重新查驗已撲球的剩餘滑行；失去接球窗口就交給後排。
                wingAssigned = targetX > shortBoundary && wingCanReceive(o);
                assignReceiver = false;
            }
            targetValid = true;
        }
        if (targetX < GameConfig.NET_X + GameConfig.BALL_RADIUS) {
            if (wingAssigned) {
                // 短球由 WS 接；後排維持後方補位，不一起追到網前。
                move(input, back, Math.min(targetX - AiTuning.RESERVE_GAP_PX,
                        GameConfig.NET_X - AiTuning.SHORT_DEFENSE_DISTANCE_FROM_NET_PX));
                var wing = o.own().get(3);
                if (wing.canStart() && o.ownTouches() == 0) {
                    int lowArrival = o.ball().descendingArrival(GameConfig.FLOOR_Y - AiTuning.LOW_BALL_HEIGHT_FROM_FLOOR_PX);
                    double needed = targetX - wing.receiveX();
                    if (needed > AiTuning.WING_MIN_DIVE_DISTANCE_PX && lowArrival < AiTuning.WING_DIVE_WINDOW_FRAMES
                            && o.ball().x() < GameConfig.NET_X + AiTuning.WING_OBSERVATION_NET_MARGIN_PX) {
                        input.wingActionPressed = true;
                    }
                }
            } else {
                receiveWithBack(o, input, floorArrival);
            }
        }
        block(o, input, tick, difficulty, habits);
    }

    private void receiveWithBack(AiObservation o, ControlInput input, int floorArrival) {
        var back = o.own().get(0);
        if (!back.canStart()) { move(input, back, targetX); return; }
        int horizon = Math.min(floorArrival, AiTuning.FORECAST_FRAMES);
        // 先找仍可跑到的站立接球窗口，避免本來能接到的球因提早撲倒而漏接。
        for (int t = 1; t <= horizon; t++) {
            double bx = o.ball().xAt(t) + receiveError;
            double desiredX = bx - back.receiveOffsetX() - back.receiveWidth() / 2;
            int direction = desiredX < back.x() ? -1 : 1;
            double travel = runningDistance(t, back.velocityX() * direction);
            double x = Math.max(back.x() - travel, Math.min(back.x() + travel, desiredX));
            x = clampBackX(back, x);
            if (overlapsBox(bx, o.ball().yAt(t), x + back.receiveOffsetX(),
                    back.receiveY() - back.receiveHeight() / 2, back.receiveWidth(), back.receiveHeight())) {
                move(input, back, bx);
                return;
            }
        }
        move(input, back, targetX);
        if (o.ownTouches() != 0) return; // 第一球以外按同一鍵會起跳，不能冒充撲救。
        int bestTime = Integer.MAX_VALUE, bestDirection = 0;
        for (int direction : new int[]{-1, 1}) {
            var box = DiveController.geometryFor(back.width(), direction);
            for (int t = 1; t <= Math.min(horizon, DiveController.TOTAL_FRAMES); t++) {
                double travel = GameConfig.DIVE_SPEED * (t - t * (t - 1.0) / (2 * DiveController.TOTAL_FRAMES));
                double x = clampBackX(back, back.x() + direction * travel);
                if (overlapsBox(o.ball().xAt(t) + receiveError, o.ball().yAt(t),
                        x + box.offsetX(), GameConfig.FLOOR_Y - back.height() + box.offsetY(), box.width(), box.height())) {
                    if (t < bestTime) { bestTime = t; bestDirection = direction; }
                    break;
                }
            }
        }
        if (bestDirection != 0) {
            input.backJump = true;
            input.backDive = true;
            input.backLeft = bestDirection < 0;
            input.backRight = bestDirection > 0;
        }
    }

    private static double clampBackX(AiObservation.Pose back, double x) {
        return Math.max(GameConfig.WORLD_LEFT,
                Math.min(GameConfig.NET_X + GameConfig.PLAYER_NET_OVERLAP_X - back.width(), x));
    }

    private static boolean overlapsBox(double bx, double by, double x, double y, double width, double height) {
        double dx = Math.max(0, Math.abs(bx - x - width / 2) - width / 2);
        double dy = Math.max(0, Math.abs(by - y - height / 2) - height / 2);
        return dx * dx + dy * dy <= GameConfig.BALL_RADIUS * GameConfig.BALL_RADIUS;
    }

    private static double runningDistance(int frames, double velocityTowardTarget) {
        int held = velocityTowardTarget >= GameConfig.BACK_RUN_MAX_SPEED ? GameConfig.BACK_RUN_FOURTH_STAGE_END_FRAMES
                : velocityTowardTarget >= GameConfig.BACK_RUN_FOURTH_STAGE_SPEED ? GameConfig.BACK_RUN_THIRD_STAGE_END_FRAMES
                : velocityTowardTarget >= GameConfig.BACK_RUN_THIRD_STAGE_SPEED ? GameConfig.BACK_RUN_SECOND_STAGE_END_FRAMES
                : velocityTowardTarget >= GameConfig.BACK_RUN_SECOND_STAGE_SPEED ? GameConfig.BACK_RUN_FIRST_STAGE_END_FRAMES : 0;
        double distance = 0;
        for (int t = 0; t < frames; t++) {
            int age = held + t;
            distance += age < GameConfig.BACK_RUN_FIRST_STAGE_END_FRAMES ? GameConfig.BACK_RUN_START_SPEED
                    : age < GameConfig.BACK_RUN_SECOND_STAGE_END_FRAMES ? GameConfig.BACK_RUN_SECOND_STAGE_SPEED
                    : age < GameConfig.BACK_RUN_THIRD_STAGE_END_FRAMES ? GameConfig.BACK_RUN_THIRD_STAGE_SPEED
                    : age < GameConfig.BACK_RUN_FOURTH_STAGE_END_FRAMES ? GameConfig.BACK_RUN_FOURTH_STAGE_SPEED : GameConfig.BACK_RUN_MAX_SPEED;
        }
        return distance;
    }

    private void block(AiObservation o, ControlInput input, int tick, AiDifficulty difficulty, OpponentHabits habits) {
        if (!o.receptionComplete() || o.opponentTouches() == 0 || o.blockUsed()) return;
        if (difficulty.defense() < AiTuning.MIN_REPEAT_BLOCK_DEFENSE
                && o.own().get(2).blockJumpCount() > 0) return;
        double skill = AiDifficulty.ability(difficulty.defense());
        // 可見的助跑／起跳會逐步取代習慣猜測；不讀對手按鍵或攻擊方案。
        int[] roles = {0, 2, 3};
        int visible = -1;
        double nearest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < roles.length; i++) {
            var attacker = o.opponent().get(roles[i]);
            if (attacker.jumping() || attacker.action() == PlayerAction.RUN_APPROACH) {
                double gap = Math.hypot(o.ball().x() - attacker.centerX(), o.ball().y() - attacker.y());
                if (gap < nearest) { nearest = gap; visible = i; }
            }
        }
        if (visible >= 0 && visible != lastVisibleAttacker) {
            if (random.nextDouble() < AiTuning.LOW_VISIBLE_READ_PROBABILITY
                    + AiTuning.VISIBLE_READ_SKILL_PROBABILITY * skill) predictedAttacker = visible;
            lastVisibleAttacker = visible;
        }
        var suspect = o.opponent().get(roles[predictedAttacker]);
        int netArrival = o.ball().velocityX() < 0
                ? (int) Math.ceil((GameConfig.NET_X - o.ball().x()) / o.ball().velocityX()) : -1;
        if (difficulty.defense() < AiTuning.MIN_TEMPO_PREDICTION_DEFENSE) {
            // 入門固定等第二節奏；不以近網球或快攻起跳資訊提前覆蓋這個時機。
            if (blockAt == Integer.MAX_VALUE && (visible >= 0
                    || netArrival >= 0 && netArrival < AiTuning.BLOCK_NET_WINDOW_FRAMES)) {
                blockAt = tick + AiTuning.SECOND_BLOCK_GUESS_FRAMES + (int) Math.abs(blockError);
            }
        } else if (netArrival >= 0 && netArrival < AiTuning.BLOCK_NET_WINDOW_FRAMES
                && o.ball().yAt(netArrival) < GameConfig.NET_TOP_Y + AiTuning.BLOCK_NET_HEIGHT_MARGIN_PX) {
            blockAt = tick + Math.max(0, netArrival - AiTuning.BLOCK_NET_LEAD_FRAMES + (int) blockError);
        } else if (suspect.jumping() && blockAt == Integer.MAX_VALUE) {
            int lead = predictedTempo == 0 ? AiTuning.NEGATIVE_BLOCK_GUESS_FRAMES
                    : predictedTempo == 1 ? AiTuning.FIRST_BLOCK_GUESS_FRAMES : AiTuning.SECOND_BLOCK_GUESS_FRAMES;
            double visibleLead = Math.max(0, -suspect.velocityY() / GameConfig.GRAVITY
                    + GameConfig.MB_JUMP_VELOCITY_Y / GameConfig.GRAVITY + AiTuning.VISIBLE_BLOCK_OFFSET_FRAMES);
            lead = (int) Math.round(lead * (1 - skill) + visibleLead * skill);
            blockAt = tick + Math.max(0, lead + (int) blockError
                    - (int) (habits.tempoWeight(predictedTempo) * skill * AiTuning.HABIT_BLOCK_ADJUST_FRAMES));
        }
        if (tick >= blockAt && o.own().get(2).canStart()) {
            input.mbActionPressed = true;
            // 重新觀察後才補攔；角色仍須完整落地，第二次高度由原模型決定。
            blockAt = Integer.MAX_VALUE;
        }
    }

    private int weightedRole(OpponentHabits habits, double skill) {
        double pick = random.nextDouble();
        for (int i = 0; i < 2; i++) {
            pick -= (1 - skill) / 3 + skill * habits.attackerWeight(i);
            if (pick < 0) return i;
        }
        return 2;
    }
    private int weightedTempo(OpponentHabits habits, double skill) {
        double pick = random.nextDouble();
        for (int i = 0; i < 2; i++) {
            pick -= (1 - skill) / 3 + skill * habits.tempoWeight(i);
            if (pick < 0) return i;
        }
        return 2;
    }
    private double signedError(double magnitude) { return (random.nextDouble() * 2 - 1) * magnitude; }
    private boolean wingCanReceive(AiObservation o) {
        var wing = o.own().get(3);
        if (wing.jumping() || wing.action() == PlayerAction.RECEIVING) return false;
        if (wing.diving()) {
            // 滑行速度可從目前公開速度估計；只預測尚未結束的這次撲球。
            double deceleration = GameConfig.DIVE_SPEED / DiveController.TOTAL_FRAMES;
            int remaining = (int) Math.ceil(Math.max(0, wing.velocityX()) / deceleration);
            double x = wing.x();
            for (int t = 0; t <= remaining; t++) {
                if (t > 0) x += Math.max(0, wing.velocityX() - deceleration * t);
                double dx = Math.max(0, Math.abs(o.ball().xAt(t) + receiveError
                        - x - wing.receiveOffsetX() - wing.receiveWidth() / 2) - wing.receiveWidth() / 2);
                double dy = Math.max(0, Math.abs(o.ball().yAt(t) - wing.receiveY()) - wing.receiveHeight() / 2);
                if (dx * dx + dy * dy <= GameConfig.BALL_RADIUS * GameConfig.BALL_RADIUS) return true;
            }
            return false;
        }
        if (!wing.canStart()) return false;
        int lowArrival = o.ball().descendingArrival(GameConfig.FLOOR_Y - AiTuning.LOW_BALL_HEIGHT_FROM_FLOOR_PX);
        int diveFrames = Math.min(lowArrival, DiveController.TOTAL_FRAMES);
        double travel = GameConfig.DIVE_SPEED * diveFrames
                * (1 - diveFrames / (2.0 * DiveController.TOTAL_FRAMES));
        double lowX = o.ball().xAt(lowArrival) + receiveError;
        return lowX >= wing.receiveX() - GameConfig.BALL_RADIUS
                && lowX <= wing.receiveX() + travel + AiTuning.WING_REACH_MARGIN_PX;
    }
    static void move(ControlInput input, AiObservation.Pose back, double receiveX) {
        double delta = receiveX - back.receiveX();
        input.backLeft = delta < -AiTuning.MOVEMENT_DEAD_ZONE_PX;
        input.backRight = delta > AiTuning.MOVEMENT_DEAD_ZONE_PX;
    }
}
