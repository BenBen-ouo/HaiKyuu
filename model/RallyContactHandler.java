package model;

public class RallyContactHandler {
    private static final double SETTER_PASS_POWER = 10.5;
    private static final double ATTACK_PASS_POWER = 12.5;
    private static final double BALL_UNSTUCK_DISTANCE = 5.0;

    private final GameModel model;

    public RallyContactHandler(GameModel model) {
        this.model = model;
    }

    public void collideTeam(Team team, boolean redSide) {
        int hitCount = model.getHitCount(redSide);
        Player lastHitter = model.getLastHitter(redSide);

        for (Player player : team.getPlayers()) {
            if (player == lastHitter) {
                continue;
            }

            BallTarget target = BallTarget.forPlayer(team, redSide, hitCount, model.ball.x, player);
            if (collidePlayer(player, target)) {
                playTouchAnimation(player, hitCount);
                model.recordHit(redSide, player);
                break;
            }
        }
    }

    private boolean collidePlayer(Player player, BallTarget target) {
        if (!player.intersectsBall(model.ball)) {
            return false;
        }

        pushBallOutsidePlayer(player);
        setBallVelocity(target);
        return true;
    }

    private void pushBallOutsidePlayer(Player player) {
        double dx = model.ball.x - player.getHitBoxCenterX();
        double dy = model.ball.y - player.getHitBoxCenterY();
        double length = Math.max(1, Math.sqrt(dx * dx + dy * dy));

        model.ball.x += dx / length * BALL_UNSTUCK_DISTANCE;
        model.ball.y += dy / length * BALL_UNSTUCK_DISTANCE;
    }

    private void setBallVelocity(BallTarget target) {
        double[] velocity = PhysicsUtils.calculateVelocityToTarget(
                model.ball.x,
                model.ball.y,
                target.x,
                target.y,
                target.power,
                GameConfig.GRAVITY
        );

        model.ball.vx = velocity[0];
        model.ball.vy = velocity[1];
    }

    private void playTouchAnimation(Player player, int hitCountBeforeTouch) {
        if (player instanceof Setter) {
            if (hitCountBeforeTouch <= 2) {
                player.playSettingAnimation();
            }
            return;
        }

        if (player.isAttackReady() || player.isAttackSwinging()) {
            // 之後真正扣球邏輯可接在這裡：
            // if (player.isAttackSwinging()) { 改成扣球速度 / 方向 }
            return;
        }

        if (player instanceof WingSpiker) {
            player.playReceiveAnimation();
            return;
        }

        if (player instanceof BackPlayer && !player.diving) {
            player.playReceiveAnimation();
        }
    }

    private static class BallTarget {
        final double x;
        final double y;
        final double power;

        private BallTarget(double x, double y, double power) {
            this.x = x;
            this.y = y;
            this.power = power;
        }

        static BallTarget forPlayer(Team team, boolean redSide, int hitCount, double ballX, Player player) {
            if (hitCount == 0 || hitCount == 1) {
                double setterTargetX = team.setter.x + team.setter.imageWidth / 2.0;
                double targetX = player == team.setter ? ballX : setterTargetX;
                return new BallTarget(targetX, team.setter.y - 20, SETTER_PASS_POWER);
            }

            return new BallTarget(
                    SideRules.thirdTouchTargetX(redSide),
                    GameConfig.FLOOR_Y - 50,
                    ATTACK_PASS_POWER
            );
        }
    }
}