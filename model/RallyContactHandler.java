/*
處理來回中球與球員的一般碰撞、攻擊碰撞、傳球目標、觸球動畫與觸球紀錄。
一般 hitBox 會依角色狀態決定是否啟用；MB block2 會用反彈，attackHitBox 會用扣球。
*/
package model;

public class RallyContactHandler {
    private static final double SETTER_PASS_POWER = 13.5;
    private static final double ATTACK_PASS_POWER = 15.5;
    private static final double BALL_UNSTUCK_DISTANCE = 6.0;

    private final GameModel model;

    public RallyContactHandler(GameModel model) {
        this.model = model;
    }

    public void collideTeam(Team team, boolean redSide, TeamInput input) {
        int hitCount = model.getHitCount(redSide);
        Player lastHitter = model.getLastHitter(redSide);

        if (trySpikeContact(team, redSide, input, lastHitter)) {
            return;
        }

        for (Player player : team.getPlayers()) {
            if (player == lastHitter) {
                continue;
            }

            if (tryBlockRebound(player)) {
                break;
            }

            BallTarget target = BallTarget.forPlayer(team, redSide, hitCount, model.ball.x, player);
            if (collidePlayer(player, target, redSide)) {
                handleTouchAnimation(player, hitCount, redSide);
                model.recordHit(redSide, player);
                break;
            }
        }
    }

    private boolean trySpikeContact(Team team, boolean redSide, TeamInput input, Player lastHitter) {
        for (Player player : team.getPlayers()) {
            if (player == lastHitter || !canSpike(player, input)) {
                continue;
            }

            if (player.attackHitBox.intersectsBall(model.ball)) {
                performSpike(createAttackContext(player, redSide), input);
                model.recordHit(redSide, player);
                return true;
            }
        }

        return false;
    }

    private boolean canSpike(Player player, TeamInput input) {
        boolean inAttackMode = player.isAttackReady() || player.isAttackSwinging();
        return inAttackMode && player.jumping && isAttackKeyPressed(player, input);
    }

    private boolean isAttackKeyPressed(Player player, TeamInput input) {
        if (player instanceof WingSpiker) {
            return input.wingAttack;
        }

        if (player instanceof QuickAttacker) {
            return input.quickAttack;
        }

        if (player instanceof BackPlayer) {
            return input.backJump;
        }

        return false;
    }

    private void performSpike(AttackContext context, TeamInput input) {
        Player attacker = context.attacker;

        pushBallOutsideAttackHitBox(attacker);
        attacker.startAttackSwingAnimation();
        setSpikeVelocity(context.redSide, input);

        // 命中一次後立刻關閉攻擊 hitBox，避免同一次起跳落地前再次影響球。
        attacker.attackHitBox.disable();
    }

    private void setSpikeVelocity(boolean redSide, TeamInput input) {
        double speedX = GameConfig.SPIKE_SPEED_X;
        double speedY = GameConfig.SPIKE_SPEED_Y;

        if (input.spikeLob && input.spikeFlat) {
            speedX = GameConfig.LONG_LOB_SPIKE_SPEED_X;
            speedY = GameConfig.LONG_LOB_SPIKE_SPEED_Y;
        } else if (input.spikeLob) {
            speedX = GameConfig.LOB_SPIKE_SPEED_X;
            speedY = GameConfig.LOB_SPIKE_SPEED_Y;
        } else if (input.spikeShort && input.spikeFlat) {
            speedX = GameConfig.LONG_SPIKE_SPEED_X;
            speedY = GameConfig.LONG_SPIKE_SPEED_Y;
        } else if (input.spikeFlat) {
            speedX = GameConfig.FLAT_SPIKE_SPEED_X;
            speedY = GameConfig.FLAT_SPIKE_SPEED_Y;
        } else if (input.spikeShort) {
            speedX = GameConfig.SHORT_SPIKE_SPEED_X;
            speedY = GameConfig.SHORT_SPIKE_SPEED_Y;
        }

        model.ball.vx = SideRules.directionTowardOpponent(redSide) * speedX;
        model.ball.vy = speedY;
        model.ball.setRotationSpeed(spikeSpinSpeed(redSide, input));

        if (input.spikeLob) {
            model.ball.useSlowFloorBounceSpin();
        } else {
            model.ball.useFastFloorBounceSpin();
        }
    }

    private double spikeSpinSpeed(boolean redSide, TeamInput input) {
        double spinSpeed = input.spikeLob
                ? GameConfig.LOB_SPIKE_SPIN_SPEED
                : GameConfig.SPIKE_SPIN_SPEED;

        return redSide ? spinSpeed : -spinSpeed;
    }

    private boolean tryBlockRebound(Player player) {
        if (!(player instanceof QuickAttacker) || !player.isBlockHitBoxActive()) {
            return false;
        }

        if (!player.intersectsBall(model.ball)) {
            return false;
        }

        pushBallOutsidePlayer(player);
        reflectBallFromBlock(player);
        model.ball.useFastFloorBounceSpin();
        return true;
    }

    private boolean collidePlayer(Player player, BallTarget target, boolean redSide) {
        if (!player.intersectsBall(model.ball)) {
            return false;
        }

        pushBallOutsidePlayer(player);
        setBallVelocity(target);
        setRotationForRegularTouch(player, redSide);
        return true;
    }

    private void setRotationForRegularTouch(Player player, boolean redSide) {
        if (player instanceof BackPlayer && player.diving) {
            double diveSpin = redSide
                    ? -GameConfig.DIVE_RECEIVE_SPIN_SPEED
                    : GameConfig.DIVE_RECEIVE_SPIN_SPEED;

            model.ball.setRotationSpeed(diveSpin);
            model.ball.useFastFloorBounceSpin();
            return;
        }

        model.ball.useSlowFloorBounceSpin();

        if (player instanceof Setter) {
            model.ball.stopRotation();
            return;
        }

        if (player instanceof BackPlayer || player instanceof WingSpiker) {
            double receiveSpin = redSide
                    ? -GameConfig.RECEIVE_SPIN_SPEED
                    : GameConfig.RECEIVE_SPIN_SPEED;

            model.ball.setRotationSpeed(receiveSpin);
        }
    }

    private void handleTouchAnimation(Player player, int hitCountBeforeTouch, boolean redSide) {
        if (player instanceof Setter) {
            playSetterAnimationIfAllowed(player, hitCountBeforeTouch);
            return;
        }

        if (player.isAttackReady() || player.isAttackSwinging()) {
            createAttackContext(player, redSide);
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

    private void playSetterAnimationIfAllowed(Player player, int hitCountBeforeTouch) {
        if (hitCountBeforeTouch <= 2) {
            player.playSettingAnimation();
        }
    }

    private AttackContext createAttackContext(Player player, boolean redSide) {
        return new AttackContext(player, redSide);
    }

    private void pushBallOutsidePlayer(Player player) {
        double dx = model.ball.x - player.getHitBoxCenterX();
        double dy = model.ball.y - player.getHitBoxCenterY();
        pushBall(dx, dy);
    }

    private void pushBallOutsideAttackHitBox(Player player) {
        double dx = model.ball.x - player.attackHitBox.getCenterX();
        double dy = model.ball.y - player.attackHitBox.getCenterY();
        pushBall(dx, dy);
    }

    private void pushBall(double dx, double dy) {
        double length = Math.max(1, Math.sqrt(dx * dx + dy * dy));

        model.ball.x += dx / length * BALL_UNSTUCK_DISTANCE;
        model.ball.y += dy / length * BALL_UNSTUCK_DISTANCE;
    }

    private void reflectBallFromBlock(Player player) {
        double normalX = model.ball.x - player.getHitBoxCenterX();
        double normalY = model.ball.y - player.getHitBoxCenterY();
        double normalLength = Math.sqrt(normalX * normalX + normalY * normalY);

        if (normalLength < 0.001) {
            normalX = model.ball.vx == 0
                    ? SideRules.directionTowardOpponent(player.redSide)
                    : Math.signum(model.ball.vx);
            normalY = -0.2;
            normalLength = Math.sqrt(normalX * normalX + normalY * normalY);
        }

        normalX /= normalLength;
        normalY /= normalLength;

        double dot = model.ball.vx * normalX + model.ball.vy * normalY;
        double reflectedVx;
        double reflectedVy;

        if (dot < 0) {
            reflectedVx = model.ball.vx - 2 * dot * normalX;
            reflectedVy = model.ball.vy - 2 * dot * normalY;
        } else {
            double currentSpeed = Math.sqrt(model.ball.vx * model.ball.vx + model.ball.vy * model.ball.vy);
            double speed = Math.max(currentSpeed, GameConfig.BLOCK_HITBOX_MIN_SPEED);
            reflectedVx = normalX * speed;
            reflectedVy = normalY * speed;
        }

        reflectedVx *= GameConfig.BLOCK_HITBOX_BOUNCE;
        reflectedVy *= GameConfig.BLOCK_HITBOX_BOUNCE;

        double reflectedSpeed = Math.sqrt(reflectedVx * reflectedVx + reflectedVy * reflectedVy);
        if (reflectedSpeed < GameConfig.BLOCK_HITBOX_MIN_SPEED) {
            double scale = GameConfig.BLOCK_HITBOX_MIN_SPEED / Math.max(0.001, reflectedSpeed);
            reflectedVx *= scale;
            reflectedVy *= scale;
        }

        model.ball.vx = reflectedVx;
        model.ball.vy = reflectedVy;
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
                return setterTarget(team, ballX, player);
            }

            return attackTarget(redSide);
        }

        private static BallTarget setterTarget(Team team, double ballX, Player player) {
            double setterX = team.setter.x + team.setter.imageWidth / 2.0;
            double targetX = player == team.setter ? ballX : setterX;
            return new BallTarget(targetX, team.setter.y + 30, SETTER_PASS_POWER);
        }

        private static BallTarget attackTarget(boolean redSide) {
            return new BallTarget(
                    SideRules.thirdTouchTargetX(redSide),
                    GameConfig.FLOOR_Y - 50,
                    ATTACK_PASS_POWER
            );
        }
    }
}