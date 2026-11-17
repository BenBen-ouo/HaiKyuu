import model.GameConfig;
import model.GameModel;
import model.SideRules;
import model.TeamInput;
import model.ball.Ball;
import model.ball.BallTrajectory;
import model.ball.NetClearanceTrajectory;
import model.ball.NetHitBox;
import model.rally.RallyContactHandler;

public final class ThirdTouchTrajectoryTest {
    public static void main(String[] args) {
        testClearanceAndTarget();
        testRegularThirdContacts();
        testImpossibleTrajectoryRetainsPhysics();
        System.out.println("ThirdTouchTrajectoryTest passed");
    }

    private static void testClearanceAndTarget() {
        for (boolean red : new boolean[]{true, false}) {
            for (double distance : new double[]{30, 45, 70}) {
                double x = GameConfig.NET_X + (red ? -distance : distance), y = 580;
                double target = SideRules.thirdTouchTargetX(red);
                var old = BallTrajectory.estimateVelocityToTargetAtSpeed(x, y, target,
                        GameConfig.FLOOR_Y - 50, 16, GameConfig.GRAVITY);
                var net = new NetHitBox();
                boolean blocked = hitsNet(x, y, old, net);
                var velocity = NetClearanceTrajectory.correctIfBlocked(x, y, GameConfig.BALL_RADIUS,
                        target, GameConfig.FLOOR_Y - 50, old, net);
                if (blocked) {
                    check(!velocity.equals(old) && !hitsNet(x, y, velocity, net), "左右近網第三球修正後可越網");
                    double time = (target - x) / velocity.x();
                    double targetY = y + velocity.y() * time + GameConfig.GRAVITY * time * (time + 1) / 2;
                    check(Math.abs(targetY - (GameConfig.FLOOR_Y - 50)) < 1e-7, "重算後仍通過原目標點");
                } else check(velocity.equals(old), "原本可過網的球路保持原速度");
            }
            double farX = red ? 200 : 1000;
            var old = new BallTrajectory.Velocity(red ? 14 : -14, -6);
            check(NetClearanceTrajectory.correctIfBlocked(farX, 580, GameConfig.BALL_RADIUS,
                    SideRules.thirdTouchTargetX(red), 575, old, new NetHitBox()).equals(old), "遠網球路不調整");
        }
    }

    private static boolean hitsNet(double x, double y, BallTrajectory.Velocity v, NetHitBox net) {
        Ball ball = new Ball(x, y); ball.vx = v.x(); ball.vy = v.y();
        for (int frame = 0; frame < 500; frame++) {
            ball.update();
            if (ball.collideWithNet(net)) return true;
            if (ball.y + ball.radius >= GameConfig.FLOOR_Y) return false;
        }
        throw new AssertionError("Trajectory never reached the floor");
    }

    private static void testImpossibleTrajectoryRetainsPhysics() {
        for (boolean red : new boolean[]{true, false}) {
            double x = red ? 584.5 : 615.5;
            double target = SideRules.thirdTouchTargetX(red);
            var net = new NetHitBox();
            var old = BallTrajectory.estimateVelocityToTargetAtSpeed(x, 580, target, 575, 16, GameConfig.GRAVITY);
            var velocity = NetClearanceTrajectory.correctIfBlocked(x, 580, GameConfig.BALL_RADIUS, target, 575, old, net);
            check(velocity.equals(old) && hitsNet(x, 580, velocity, net),
                    "已確認無解時保留原球路與正常觸網，不移球或更改目標");
            var high = new BallTrajectory.Velocity(red ? 6 : -6, -3);
            check(NetClearanceTrajectory.correctIfBlocked(red ? 550 : 650, 200, GameConfig.BALL_RADIUS,
                    target, 575, high, net).equals(high), "近網但原本可過網時不修正");
        }
    }

    private static void testRegularThirdContacts() {
        for (boolean red : new boolean[]{true, false}) {
            for (int role : new int[]{0, 1, 3}) {
                var game = new GameModel();
                game.getServeHandler().setRedServing(!red);
                game.getServeHandler().setWaitingForServe(true);
                game.getServeHandler().setWaitingForServe(false);
                var team = red ? game.redTeam : game.blueTeam;
                game.recordRegularHit(red, role == 1 ? team.setter : team.backPlayer);
                game.recordRegularHit(red, role == 1 ? team.wingSpiker : team.setter);
                var player = team.getPlayers()[role];
                double desiredCenter = red ? 570 : 630;
                player.x += desiredCenter - player.hitBox.getCenterX();
                game.ball.x = player.hitBox.getCenterX();
                game.ball.y = player.hitBox.getCenterY();
                game.ball.syncPreviousPosition();
                new RallyContactHandler(game).collideTeam(team, red, new TeamInput());
                check(game.getLastHitter(red) == player && game.getHitCount(red) == 3, "一般第三球仍正常計次");
                check(!hitsNet(game.ball.x, game.ball.y,
                        new BallTrajectory.Velocity(game.ball.vx, game.ball.vy), game.netHitBox),
                        "後排／WS／Setter 近網第三球均能越網：role=" + role);
            }
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
