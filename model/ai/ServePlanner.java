package model.ai;

import java.util.random.RandomGenerator;
import model.ControlInput;
import model.serve.ServeState;
import model.serve.ServeType;
import model.player.PlayerAction;
import model.GameConfig;
import model.player.BackPlayer;

/** 發球、拋球、放開、再次起跳、再次擊球共用現有三次按鍵流程。 */
final class ServePlanner {
    private final RandomGenerator random;
    private int serveAt;
    private boolean jump;
    private int jumpMode;
    private int jumpedAt = -1;
    private int jumpAt = -1;
    private ServeType type = ServeType.NORMAL;
    ServePlanner(RandomGenerator random) { this.random = random; }
    void begin(int tick, AiDifficulty difficulty) {
        double skill = AiDifficulty.ability(difficulty.attack());
        serveAt = tick + AiTuning.SERVE_WAIT_FRAMES + AiDifficulty.reactionFrames(difficulty.attack(), random);
        jumpedAt = -1;
        jumpAt = -1;
        double advanced = skill * AiTuning.MAX_ADVANCED_SERVE_PROBABILITY;
        jump = random.nextDouble() < AiTuning.unlockedProgress(difficulty.attack(), AiTuning.MIN_JUMP_SERVE_ATTACK)
                * AiTuning.MAX_ADVANCED_SERVE_PROBABILITY * AiTuning.ADVANCED_JUMP_SERVE_SHARE;
        jumpMode = random.nextDouble() < skill ? random.nextInt(4) : 0;
        if (jump) type = ServeType.NORMAL;
        else if (random.nextDouble() < advanced) type = random.nextBoolean() ? ServeType.SHORT : ServeType.CEILING;
        else type = random.nextBoolean() ? ServeType.NORMAL : ServeType.LOW_NET;
    }
    void update(AiObservation o, ControlInput input, int tick) {
        input.serveType = type;
        if (o.serveState() == ServeState.WAITING_FOR_SERVE) {
            if (tick >= serveAt) { input.servePressed = true; input.spikeFlat = jump; }
        } else if (o.serveState() == ServeState.JUMP_TOSS) {
            var back = o.own().get(0);
            if (jumpAt < 0) jumpAt = tick + estimateJumpDelay(o);
            if (jumpedAt < 0 && tick > serveAt + 2 && tick >= jumpAt) {
                input.servePressed = true;
                input.backJump = true;
                jumpedAt = tick;
            } else if (jumpedAt >= 0 && tick > jumpedAt + 1 && back.jumping()
                    && back.action() == PlayerAction.ATTACK_READY) {
                double dx = Math.max(0, Math.abs(o.ball().x() - back.x() - back.attackOffsetX()
                        - back.attackWidth() / 2) - back.attackWidth() / 2);
                double dy = Math.max(0, Math.abs(o.ball().y() - back.y() - back.attackOffsetY()
                        - back.attackHeight() / 2) - back.attackHeight() / 2);
                if (dx * dx + dy * dy > GameConfig.BALL_RADIUS * GameConfig.BALL_RADIUS) return;
                int viable = viableJumpMode(o);
                if (viable < 0) return;
                input.servePressed = true;
                input.backJump = true;
                input.spikeShort = viable == 1;
                input.backLeft = viable == 2;
                input.backRight = viable == 3;
            }
        }
    }

    private int viableJumpMode(AiObservation o) {
        for (int mode : new int[]{jumpMode, 0, 3, 2, 1}) {
            double speed = mode == 2 ? GameConfig.SERVE_JUMP_SLOW_HORIZONTAL_SPEED
                    : mode == 1 ? GameConfig.SERVE_JUMP_SHORT_HORIZONTAL_SPEED : GameConfig.SERVE_JUMP_HORIZONTAL_SPEED;
            double vy = mode == 1 ? GameConfig.SERVE_JUMP_SHORT_VELOCITY_Y
                    : mode == 2 ? GameConfig.SERVE_JUMP_SLOW_VELOCITY_Y
                    : mode == 3 ? GameConfig.SERVE_JUMP_LONG_VELOCITY_Y : GameConfig.SERVE_JUMP_VELOCITY_Y;
            if (AttackOptions.isUsableFlight(o.ball().x(), o.ball().y(), speed, vy)) return mode;
        }
        return -1;
    }

    private static int estimateJumpDelay(AiObservation o) {
        var back = o.own().get(0);
        int bestDelay = 0;
        double best = Double.POSITIVE_INFINITY;
        int flight = (int) Math.ceil(-2 * GameConfig.BACK_JUMP_VELOCITY_Y / GameConfig.GRAVITY);
        for (int delay = 1; delay < AiTuning.FORECAST_FRAMES - flight; delay++) {
            for (int age = 2; age < flight; age++) {
                int t = delay + age;
                double x = back.x() + BackPlayer.BACK_JUMP_MAX_HORIZONTAL_SPEED * age
                        + back.attackOffsetX() + back.attackWidth() / 2;
                double y = back.y() + GameConfig.BACK_JUMP_VELOCITY_Y * age
                        + GameConfig.GRAVITY * age * (age + 1) / 2
                        + back.attackOffsetY() + back.attackHeight() / 2;
                double dx = Math.max(0, Math.abs(o.ball().xAt(t) - x) - back.attackWidth() / 2);
                double dy = Math.max(0, Math.abs(o.ball().yAt(t) - y) - back.attackHeight() / 2);
                double error = dx * dx + dy * dy;
                if (error <= GameConfig.BALL_RADIUS * GameConfig.BALL_RADIUS) {
                    double score = Math.abs(age + GameConfig.BACK_JUMP_VELOCITY_Y / GameConfig.GRAVITY);
                    if (score < best) { best = score; bestDelay = delay; }
                }
            }
        }
        return bestDelay;
    }
}
