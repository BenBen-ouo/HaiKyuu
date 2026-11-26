package model.ai;

import model.GameConfig;
import model.player.DiveController;
import model.player.PlayerAction;
import model.player.WingSpiker;
import model.rally.ScoringLogic;

/** 由公開位置估計防守窗口；不執行碰撞，也不讀取對手輸入。X 為本隊在左。 */
final class DefenseForecast {
    private DefenseForecast() {}

    static boolean leaveOutBall(AiObservation o) {
        int arrival = o.ball().descendingArrival(GameConfig.FLOOR_Y - GameConfig.BALL_RADIUS);
        double x = o.ball().x();
        double vx = o.ball().velocityX();
        boolean leaving = x < GameConfig.COURT_LEFT_X && vx <= 0
                || x > GameConfig.COURT_RIGHT_X && vx >= 0;
        // 只放掉已離場且持續遠離球場的球；不預告分數，也不忽略自身最後觸球。
        return !o.lastTouchOwn() && leaving
                && !ScoringLogic.isBallInCourt(x, GameConfig.BALL_RADIUS)
                && !ScoringLogic.isBallInCourt(o.ball().xAt(arrival), GameConfig.BALL_RADIUS);
    }

    static boolean opponentCanAttack(AiObservation o) {
        if (!o.receptionComplete() || o.lastTouchOwn() || o.opponentAttackLocked()
                || o.opponentTouches() == 0 || o.opponentTouches() > 2) return false;
        for (int role : new int[]{0, 2, 3}) {
            var p = o.opponent().get(role);
            if (p.diving() || p.action() == PlayerAction.BLOCK || p.action() == PlayerAction.RECEIVING
                    || p.action() == PlayerAction.ATTACK_SWING || p.action() == PlayerAction.AIR_SETTING
                    || p.action() == PlayerAction.RUN_RETURN) continue;
            if (!p.jumping() && !p.canStart() && p.action() != PlayerAction.RUN_APPROACH) continue;
            int approach = !p.jumping() && role == 3 ? WingSpiker.approachDurationFrames() : 0;
            double jumpVy = p.jumping() ? p.velocityY() : role == 0 ? GameConfig.BACK_JUMP_VELOCITY_Y
                    : role == 2 ? GameConfig.MB_JUMP_VELOCITY_Y : GameConfig.WS_JUMP_VELOCITY_Y;
            for (int t = Math.max(1, approach); t <= AiTuning.FORECAST_FRAMES; t++) {
                double bx = o.ball().xAt(t);
                if (bx < GameConfig.NET_X - GameConfig.BALL_RADIUS) break;
                int age = t - approach;
                double py = p.y() + jumpVy * age + GameConfig.GRAVITY * age * (age + 1) / 2;
                if (age > 0 && py > GameConfig.FLOOR_Y - p.height()) break;
                double px = p.x() + p.velocityX() * t;
                if (!p.jumping() && role == 3) px = p.x() - WingSpiker.approachDistance();
                if (overlaps(bx, o.ball().yAt(t), px + p.attackOffsetX(), py + p.attackOffsetY(),
                        p.attackWidth(), p.attackHeight())) return true;
            }
        }
        return false;
    }

    static boolean wingStandingCanReceive(AiObservation o, double error) {
        var p = o.own().get(3);
        if (!p.canStart()) return false;
        int arrival = o.ball().descendingArrival(GameConfig.FLOOR_Y - GameConfig.BALL_RADIUS);
        for (int t = 0; t <= arrival; t++) {
            if (overlaps(o.ball().xAt(t) + error, o.ball().yAt(t), p.x() + p.receiveOffsetX(),
                    p.y() + p.receiveOffsetY(), p.receiveWidth(), p.receiveHeight())) return true;
        }
        return false;
    }

    /** 回傳最晚可啟動撲球的等待幀；-1 表示目前看不到有效窗口。 */
    static int wingDiveDelay(AiObservation o, double error) {
        var p = o.own().get(3);
        if (!p.canStart()) return -1;
        int arrival = o.ball().descendingArrival(GameConfig.FLOOR_Y - GameConfig.BALL_RADIUS);
        var box = DiveController.geometryFor(p.width(), 1);
        for (int delay = arrival - 1; delay >= 0; delay--) {
            for (int t = delay + 1; t <= Math.min(arrival, delay + DiveController.TOTAL_FRAMES); t++) {
                int age = t - delay;
                double travel = GameConfig.DIVE_SPEED * (age - age * (age - 1.0) / (2 * DiveController.TOTAL_FRAMES));
                double x = Math.min(GameConfig.NET_X + GameConfig.PLAYER_NET_OVERLAP_X - p.width(), p.x() + travel);
                if (overlaps(o.ball().xAt(t) + error, o.ball().yAt(t), x + box.offsetX(),
                        GameConfig.FLOOR_Y - p.height() + box.offsetY(), box.width(), box.height())) return delay;
            }
        }
        return -1;
    }

    static boolean overlaps(double bx, double by, double x, double y, double width, double height) {
        double dx = Math.max(0, Math.abs(bx - x - width / 2) - width / 2);
        double dy = Math.max(0, Math.abs(by - y - height / 2) - height / 2);
        return dx * dx + dy * dy <= GameConfig.BALL_RADIUS * GameConfig.BALL_RADIUS;
    }
}
