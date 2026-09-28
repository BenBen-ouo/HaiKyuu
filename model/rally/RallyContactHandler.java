/*
處理來回中球與球員的一般碰撞、攻擊碰撞、傳球目標、觸球動畫與觸球紀錄。
一般 hitBox 會依角色狀態決定是否啟用；MB block2 會用反彈，attackHitBox 會用扣球。
*/
package model.rally;

import model.GameConfig;
import model.GameModel;
import model.SideRules;
import model.TeamInput;
import model.ball.PhysicsUtils;
import model.player.BackPlayer;
import model.player.HitBox;
import model.player.Player;
import model.player.QuickAttacker;
import model.player.Setter;
import model.player.Team;
import model.player.WingSpiker;

public class RallyContactHandler {
    private static final double TO_SETTER_PASS_POWER = 13;
    private static final double THIRD_TOUCH_PASS_POWER = 16;
    private static final double Setter_THIRD_TOUCH_PASS_POWER = 10;
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

            BallTarget target = BallTarget.forPlayer(
                    team,
                    redSide,
                    hitCount,
                    model.ball.x,
                    player
            );

            if (tryBlockRebound(player)) {
                break;
            }

            // 如果是舉球員且本回合已經碰過一次舉球，第二次不應干預球（passed through）
            if (player instanceof Setter && model.hasSetterTouched(redSide)) {
                continue;
            }

            if (collidePlayer(player, target, redSide, hitCount, input)) {
                // 一般接球成功後，扣球軌跡結束。
                model.spikeEffect.stopSpikeTrail();

                handleTouchAnimation(player, hitCount, redSide);
                model.recordRegularHit(redSide, player);
                break;
            }
        }
    }

    private boolean trySpikeContact(Team team, boolean redSide, TeamInput input, Player lastHitter) {
        for (Player player : team.getPlayers()) {
            if (player == lastHitter) {
                continue;
            }

            if (!canSpike(player)) {
                continue;
            }

            if (!player.attackHitBox.intersectsBall(model.ball)) {
                continue;
            }

            // 接發方第一次一般觸球前，任一方用攻擊框碰到發球都屬發球犯規。
            if (!model.isServeReceptionComplete()) {
                performSpike(createAttackContext(player, redSide), input);
                model.awardPointWithMessage(!redSide, "發球犯規");
                return true;
            }

            // 後排球員從三米線內起跳並完成攻擊時，判定後排違規。
            if (isBackRowAttackFault(player, redSide)) {
                AttackContext ctx = createAttackContext(player, redSide);
                performSpike(ctx, input);
                model.recordHit(redSide, player);

                boolean awardRed = !redSide;
                if (model.isResolvingRallyOutcomes()) {
                    model.transientMessage = "後排三米線";
                    model.transientMessageTimer = 42;
                    model.transientMessageIsRed = awardRed;
                    model.awardPoint(awardRed);
                    // 確保扣球軌跡顯示（即使已給分，也要保留軌跡直到落地）
                    model.spikeEffect.startSpikeTrail(ctx.redSide);
                } else {
                    model.awaitAuthoritativeRallyResult();
                }
                return true;
            }

            // 合法扣球：保留 attack_way 的球路與旋轉邏輯。
            performSpike(createAttackContext(player, redSide), input);
            model.recordHit(redSide, player);
            return true;
        }

        return false;
    }

    private boolean isBackRowAttackFault(Player player, boolean redSide) {
        if (!(player instanceof BackPlayer)) {
            return false;
        }

        double jumpStartX = ((BackPlayer) player).jumpStartX;
        if (Double.isNaN(jumpStartX)) {
            return false;
        }

        if (redSide) {
            // 紅隊在左側：起跳位置越過左側三米線、靠近網子時違規。
            return jumpStartX > GameConfig.NET_X - GameConfig.THREE_METER_PX;
        }

        // 藍隊在右側：起跳位置越過右側三米線、靠近網子時違規。
        return jumpStartX < GameConfig.NET_X + GameConfig.THREE_METER_PX;
    }

    private boolean canSpike(Player player) {
        return player.isAttackSwinging() && player.jumping && player.hasValidAttackAttemptThisFrame();
    }

    private void performSpike(AttackContext context, TeamInput input) {
        Player attacker = context.attacker;

        pushBallOutsideAttackHitBox(attacker);
        attacker.startAttackSwingAnimation();
        setSpikeVelocity(context.redSide, input);

        model.spikeEffect.startSpikeTrail(context.redSide);

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
        if (!(player instanceof QuickAttacker blocker) || !blocker.isBlockHitBoxActive()) {
            return false;
        }

        if (!blocker.blockHitBox.intersectsBall(model.ball)) {
            return false;
        }

        pushBallOutsideHitBox(blocker.blockHitBox);
        reflectBallFromBlock(blocker);

        // 攔網屬於高旋轉碰球，保留 attack_way 的高速落地旋轉。
        model.ball.useFastFloorBounceSpin();

        // 發球犯規也要先產生與正常攔網相同的球體反彈，再結束這一球。
        if (!model.isServeReceptionComplete()) {
            model.awardPointWithMessage(!blocker.redSide, "發球犯規");
            return true;
        }

        Boolean attackingTeam = model.getLastHitTeam();

        /*
        * 若攔網成功，且攔網方不是最後攻擊方，
        * 表示球被攔回攻擊方場內。
        *
        * 依照排球規則：
        * 攻擊方三次觸球重新計算。
        */
        if (attackingTeam != null && attackingTeam != blocker.redSide) {
            model.resetTeamContacts(attackingTeam);
        }

        // 攔網後保留扣球軌跡，直到落地或下一次一般接球才停止。
        // 攔網接觸獨立記錄，固定不占用球隊的三次觸球次數。
        model.recordBlock(blocker.redSide, blocker);

        // 攻擊方最後觸球、且反彈球預計出界時，延後到實際落地再依 touch out 給分。
        if (attackingTeam != null
                && attackingTeam != blocker.redSide
                && BallLandingPredictor.willLandOutsideCourt(model.ball)) {
            model.pendingTouchOut = true;
            model.pendingTouchOutWinner = attackingTeam;
        }

        return true;
    }

    private boolean collidePlayer(Player player, BallTarget target, boolean redSide,
                                  int hitCountBeforeTouch, TeamInput input) {
        if (!player.intersectsBall(model.ball)) {
            return false;
        }

        pushBallOutsidePlayer(player);
        if (player instanceof Setter && hitCountBeforeTouch == 1 && input.spikeShort) {
            setQuickAttackBallVelocity(redSide);
        } else if (player instanceof Setter && hitCountBeforeTouch < 2) {
            setSetterBallVelocity(target);
        } else {
            setBallVelocity(target);
        }
        setRotationForRegularTouch(player, redSide);
        return true;
    }

    private void setRotationForRegularTouch(Player player, boolean redSide) {
        if (player instanceof BackPlayer && player.diving) {
            double diveSpin = redSide
                    ? -GameConfig.DIVE_RECEIVE_SPIN_SPEED
                    : GameConfig.DIVE_RECEIVE_SPIN_SPEED;

            model.ball.setRotationSpeed(diveSpin);
            model.ball.useSlowFloorBounceSpin();
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
        pushBallOutsideHitBox(player.hitBox);
    }

    private void pushBallOutsideHitBox(HitBox hitBox) {
        double dx = model.ball.x - hitBox.getCenterX();
        double dy = model.ball.y - hitBox.getCenterY();
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

    private void reflectBallFromBlock(QuickAttacker blocker) {
        double normalX = model.ball.x - blocker.blockHitBox.getCenterX();
        double normalY = model.ball.y - blocker.blockHitBox.getCenterY();
        double normalLength = Math.sqrt(normalX * normalX + normalY * normalY);

        if (normalLength < 0.001) {
            normalX = model.ball.vx == 0
                    ? SideRules.directionTowardOpponent(blocker.redSide)
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

    private void setSetterBallVelocity(BallTarget target) {
        double gravity = GameConfig.GRAVITY;
        int framesToApex = framesToApex(GameConfig.SETTER_SET_APEX_Y);
        double initialVy = initialVyForApex(GameConfig.SETTER_SET_APEX_Y, framesToApex);

        // 依新的飛行時間重算 vx，使球下降時仍通過原本的預定目標點。
        double verticalLinearTerm = initialVy + gravity / 2.0;
        double discriminant = verticalLinearTerm * verticalLinearTerm
                - 2.0 * gravity * (model.ball.y - target.y);
        double timeToTarget = (-verticalLinearTerm + Math.sqrt(Math.max(0.0, discriminant))) / gravity;

        model.ball.vx = (target.x - model.ball.x) / Math.max(1.0, timeToTarget);
        model.ball.vy = initialVy;
    }

    private void setQuickAttackBallVelocity(boolean redSide) {
        double apexX = redSide
                ? GameConfig.RED_QUICK_SET_APEX_X
                : GameConfig.BLUE_QUICK_SET_APEX_X;
        int framesToApex = framesToApex(GameConfig.QUICK_SET_APEX_Y);

        // 以當次 Setter 觸球後的球心位置重算，令整數幀的最高點通過指定座標。
        model.ball.vx = (apexX - model.ball.x) / framesToApex;
        model.ball.vy = initialVyForApex(GameConfig.QUICK_SET_APEX_Y, framesToApex);
    }

    private int framesToApex(double apexY) {
        double heightToApex = model.ball.y - apexY;
        double gravity = GameConfig.GRAVITY;

        // Ball.update() 先加重力再移動；取最高點所在的整數幀。
        return Math.max(1, (int) Math.ceil(
                (-1.0 + Math.sqrt(1.0 + 8.0 * heightToApex / gravity)) / 2.0
        ));
    }

    private double initialVyForApex(double apexY, int framesToApex) {
        return (apexY - model.ball.y) / framesToApex
                - GameConfig.GRAVITY * (framesToApex + 1) / 2.0;
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

            if (player instanceof Setter) {
                return setterThirdTouchTarget(redSide);
            }

            return attackTarget(redSide);
        }

        private static BallTarget setterTarget(Team team, double ballX, Player player) {
            double setterX = team.setter.x + team.setter.imageWidth / 2.0;
            double targetX = player == team.setter ? ballX : setterX;
            return new BallTarget(targetX, team.setter.y + 15, TO_SETTER_PASS_POWER);
        }

        private static BallTarget attackTarget(boolean redSide) {
            return new BallTarget(
                    SideRules.thirdTouchTargetX(redSide),
                    GameConfig.FLOOR_Y - 50,
                    THIRD_TOUCH_PASS_POWER
            );
        }

        private static BallTarget setterThirdTouchTarget(boolean redSide) {
            return new BallTarget(
                    SideRules.setterThirdTouchTargetX(redSide),
                    GameConfig.FLOOR_Y - 50,
                    Setter_THIRD_TOUCH_PASS_POWER
            );
        }
    }
}
