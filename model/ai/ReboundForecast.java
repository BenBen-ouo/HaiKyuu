package model.ai;

import model.GameConfig;
import model.ball.BlockReflection;
import model.player.PlayerAction;
import model.player.WingSpiker;

/** 出手時的可見攔網估計；不呼叫碰撞、不修改模型，也不假設對手之後會起跳。 */
final class ReboundForecast {
    private ReboundForecast() {}
    record Result(boolean blocked, boolean returnsOwnSide, boolean backCanSave, boolean wingCanSave,
            double landingX) {
        double score(boolean bothNegative) {
            if (!blocked) return 0;
            if (!returnsOwnSide) return 0;
            boolean recoverable = backCanSave || wingCanSave;
            if (!recoverable) return -AiTuning.REBOUND_RISK_WEIGHT * (bothNegative ? 1.5 : 1);
            return AiTuning.RECOVERABLE_REBOUND_BONUS;
        }
    }

    static Result evaluate(AiObservation o, double speed, double vy, int striker) {
        var blocker = o.opponent().get(2);
        var view = o.opponentBlock();
        if (view.used() || !blocker.jumping() || blocker.action() != PlayerAction.BLOCK)
            return new Result(false, false, false, false, Double.NaN);
        var incoming = new BallForecast(o.ball().x(), o.ball().y(), speed, vy);
        int horizon = Math.min(AiTuning.FORECAST_FRAMES,
                incoming.descendingArrival(GameConfig.FLOOR_Y - GameConfig.BALL_RADIUS));
        for (int t = 0; t <= horizon; t++) {
            double py = blocker.y() + blocker.velocityY() * t + GameConfig.GRAVITY * t * (t + 1) / 2;
            if (py > GameConfig.FLOOR_Y - blocker.height()) break;
            boolean hands = t >= view.handsStartFrames() && t < view.handsEndFrames();
            var shape = hands ? view.hands() : view.head();
            double cx = blocker.x() + blocker.velocityX() * t + shape.offsetX() + shape.width() / 2;
            double cy = py + shape.offsetY() + shape.height() / 2;
            double bx = incoming.xAt(t), by = incoming.yAt(t);
            double dx = bx - cx, dy = by - cy;
            double angle = Math.toRadians(-shape.rotation());
            double localX = Math.cos(angle) * dx - Math.sin(angle) * dy;
            double localY = Math.sin(angle) * dx + Math.cos(angle) * dy;
            // 手框採旋轉矩形包絡，頭框採圓；是保守預估，正式圓角碰撞仍由原模型判定。
            boolean overlaps = hands
                    ? Math.hypot(Math.max(0, Math.abs(localX) - shape.width() / 2),
                            Math.max(0, Math.abs(localY) - shape.height() / 2)) <= GameConfig.BALL_RADIUS
                    : Math.hypot(dx, dy) <= shape.width() / 2 + GameConfig.BALL_RADIUS;
            if (!overlaps) continue;
            var reflected = BlockReflection.reflect(speed, vy + GameConfig.GRAVITY * t, dx, dy, -1,
                    hands ? GameConfig.BLOCK_HITBOX_BOUNCE : GameConfig.BLOCK_HEAD_BOUNCE,
                    hands ? GameConfig.BLOCK_HITBOX_MIN_REBOUND_SPEED : 0);
            var rebound = new BallForecast(bx, by, reflected.x(), reflected.y());
            int ground = rebound.descendingArrival(GameConfig.FLOOR_Y - GameConfig.BALL_RADIUS);
            double landing = rebound.xAt(ground);
            boolean returns = reflected.x() < 0 && landing < GameConfig.NET_X;
            // 已完整過網才把原扣球者也列入；未過網前反彈仍須避開上一位觸球者。
            boolean crossed = bx > GameConfig.NET_X + GameConfig.BALL_RADIUS;
            boolean back = returns && (striker != 0 || crossed) && canRecover(o.own().get(0), rebound, t, false);
            boolean wing = returns && (striker != 3 || crossed) && canRecover(o.own().get(3), rebound, t, true);
            return new Result(true, returns, back, wing, landing);
        }
        return new Result(false, false, false, false, Double.NaN);
    }

    static boolean canRecover(AiObservation.Pose p, BallForecast rebound, int travelToBlock, boolean wing) {
        if (p.diving() || p.action() == PlayerAction.RECEIVING || p.action() == PlayerAction.SETTING) return false;
        int landingDelay = 0;
        double x = p.x();
        if (p.jumping()) {
            while (landingDelay < AiTuning.FORECAST_FRAMES && p.y() + p.velocityY() * landingDelay
                    + GameConfig.GRAVITY * landingDelay * (landingDelay + 1) / 2 < GameConfig.FLOOR_Y - p.height()) landingDelay++;
            x += p.velocityX() * landingDelay;
        } else if (p.action() == PlayerAction.RUN_APPROACH) {
            // 助跑已啟動不能取消；保守算完整剩餘助跑、跳躍與落地，再考慮回位接球。
            landingDelay = WingSpiker.approachDurationFrames()
                    + (int) Math.ceil(-2 * GameConfig.WS_JUMP_VELOCITY_Y / GameConfig.GRAVITY);
            x += WingSpiker.approachDistance();
        }
        x = Math.max(GameConfig.WORLD_LEFT, Math.min(GameConfig.NET_X + GameConfig.PLAYER_NET_OVERLAP_X - p.width(), x));
        int contact = rebound.descendingArrival(GameConfig.PLAYER_BASE_Y + p.receiveOffsetY() + p.receiveHeight() / 2);
        int available = contact + travelToBlock - landingDelay;
        if (available < 0) return false;
        double target = rebound.xAt(contact);
        double ownCenter = x + p.receiveOffsetX() + p.receiveWidth() / 2;
        double tolerance = p.receiveWidth() / 2 + GameConfig.BALL_RADIUS;
        if (wing) {
            // WS 只能沿原回位路徑奔跑接球，不能像後排自由走位。
            double homeCenter = GameConfig.NET_X + GameConfig.RED_WING_OFFSET_X
                    + p.receiveOffsetX() + p.receiveWidth() / 2;
            double next = ownCenter + Math.signum(homeCenter - ownCenter)
                    * Math.min(Math.abs(homeCenter - ownCenter), WingSpiker.returnSpeed() * available);
            if (Math.abs(target - next) <= tolerance) return true;
            int homeFrames = (int) Math.ceil(Math.abs(homeCenter - ownCenter) / WingSpiker.returnSpeed());
            int diveTime = rebound.descendingArrival(GameConfig.FLOOR_Y - GameConfig.BALL_RADIUS)
                    + travelToBlock - landingDelay - homeFrames;
            if (diveTime <= 0) return false;
            int slideFrames = Math.min(diveTime, model.player.DiveController.TOTAL_FRAMES);
            double slide = GameConfig.DIVE_SPEED * (slideFrames
                    - slideFrames * (slideFrames - 1.0) / (2 * model.player.DiveController.TOTAL_FRAMES));
            double lowTarget = rebound.xAt(rebound.descendingArrival(GameConfig.FLOOR_Y - GameConfig.BALL_RADIUS));
            return lowTarget >= homeCenter - tolerance && lowTarget <= homeCenter + slide + tolerance;
        }
        return Math.abs(target - ownCenter) <= GameConfig.BACK_RUN_START_SPEED * available + tolerance;
    }
}
