/*
處理來回中球與球員的一般碰撞、攻擊碰撞、傳球目標、觸球動畫與觸球紀錄。
一般 hitBox 會依角色狀態決定是否啟用；MB block2 會用反彈，attackHitBox 會用扣球。
*/
package model.rally;

import model.GameConfig;
import model.GameModel;
import model.SideRules;
import model.TeamInput;
import model.ball.BallTrajectory;
import model.player.BackPlayer;
import model.player.HitBox;
import model.player.Player;
import model.player.QuickAttacker;
import model.player.Setter;
import model.player.Team;
import model.player.WingSpiker;

public class RallyContactHandler {
    private static final double TO_SETTER_PASS_SPEED = 13;
    private static final double THIRD_TOUCH_PASS_SPEED = 16;
    private static final double SETTER_THIRD_TOUCH_PASS_SPEED = 13;
    private static final double BALL_UNSTUCK_DISTANCE = 6.0;
    private static final double MIN_NORMAL_LENGTH = 0.001;
    private static final double CENTER_CONTACT_NORMAL_Y = -0.2;
    private static final double SETTER_TARGET_OFFSET_Y = 10;
    private static final double THIRD_TOUCH_TARGET_HEIGHT = 50;

    private final GameModel model;

    public RallyContactHandler(GameModel model) {
        this.model = model;
    }

    /** 攻擊鍵按下的當幀處理空中舉球，避免球在本幀移動後才碰撞造成漏接。 */
    public boolean tryAirSetContact(Team team, boolean redSide) {
        if (model.getHitCount(redSide) != 1 || !model.isServeReceptionComplete()) {
            return false;
        }
        for (Player player : new Player[]{team.backPlayer, team.wingSpiker}) {
            if (!player.hasAirSetAttemptThisFrame() || player == model.getLastHitter(redSide)) {
                continue;
            }
            model.ball.vx = 0;
            model.ball.vy = BallTrajectory.toApex(model.ball.y, GameConfig.SETTER_SET_APEX_Y,
                    GameConfig.GRAVITY).velocityY();
            model.ball.stopRotation();
            model.ball.useSlowFloorBounceSpin();
            model.spikeEffect.stopSpikeTrail();
            model.recordRegularHit(redSide, player);
            return true;
        }
        return false;
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

            // Setter 接第一球後可再接第三球；接第二球後不可緊接第三球。
            if (player instanceof Setter && !model.canSetterTouch(redSide)) {
                continue;
            }

            if (collidePlayer(player, target, redSide, hitCount, input)) {
                if (redSide == model.getServeHandler().isRedServing()
                        && !model.isServeReceptionComplete()
                        && model.getServeHandler().hasLaunchedServe()) {
                    model.awardPointWithMessage(!redSide, "發球犯規");
                    break;
                }

                // 一般接球成功後，扣球軌跡結束。
                model.spikeEffect.stopSpikeTrail();

                handleTouchAnimation(player, hitCount);
                model.recordRegularHit(redSide, player);
                break;
            }
        }
    }

    private boolean trySpikeContact(Team team, boolean redSide, TeamInput input, Player lastHitter) {
        if (model.isAttackLocked(redSide)) return false;
        for (Player player : team.getPlayers()) {
            if (player == lastHitter) {
                continue;
            }

            if (!canSpike(player)) {
                continue;
            }

            // 接發方第一次一般觸球前，任一方用攻擊框碰到發球都屬發球犯規。
            if (!model.isServeReceptionComplete()) {
                performSpike(player, redSide, input);
                model.awardPointWithMessage(!redSide, "發球犯規");
                return true;
            }

            // 後排球員從三米線內起跳並完成攻擊時，判定後排違規。
            if (isBackRowThreeMeterFault(player, redSide)) {
                performSpike(player, redSide, input);
                model.recordHit(redSide, player);

                boolean awardRed = !redSide;
                model.transientMessage = "後排三米線";
                model.transientMessageTimer = RallyScorer.MESSAGE_OVERRIDE_FRAMES;
                model.transientMessageIsRed = awardRed;
                model.awardPoint(awardRed);
                // 給分後保留已確認的扣球軌跡直到落地。
                model.spikeEffect.startSpikeTrail(redSide);
                return true;
            }

            // 合法扣球：保留 attack_way 的球路與旋轉邏輯。
            performSpike(player, redSide, input);
            model.recordHit(redSide, player);
            return true;
        }

        return false;
    }

    private boolean isBackRowThreeMeterFault(Player player, boolean redSide) {
        if (!(player instanceof BackPlayer)) {
            return false;
        }

        double jumpStartCenterX = player.getAttackTakeoffCenterX();
        if (Double.isNaN(jumpStartCenterX)) {
            return false;
        }

        if (redSide) {
            // 紅隊在左側：起跳中心在線上或更靠近網子時違規。
            return jumpStartCenterX >= GameConfig.NET_X - GameConfig.THREE_METER_PX;
        }

        // 藍隊在右側：起跳中心在線上或更靠近網子時違規。
        return jumpStartCenterX <= GameConfig.NET_X + GameConfig.THREE_METER_PX;
    }

    private boolean canSpike(Player player) {
        // 本幀落地仍允許落地前已在空中、且球已進框的攻擊。
        return player.hasValidAttackAttemptThisFrame();
    }

    private void performSpike(Player attacker, boolean redSide, TeamInput input) {
        model.lockAttack(redSide);

        pushBallOutsideAttackHitBox(attacker);
        attacker.startAttackSwingAnimation();
        setSpikeVelocity(redSide, input);

        model.spikeEffect.startSpikeTrail(redSide);

        // 命中一次後立刻關閉攻擊 hitBox，避免同一次起跳落地前再次影響球。
        attacker.attackHitBox.disable();
    }

    private void setSpikeVelocity(boolean redSide, TeamInput input) {
        double horizontalSpeed = GameConfig.SPIKE_HORIZONTAL_SPEED;
        double verticalVelocity = GameConfig.SPIKE_VELOCITY_Y;

        if (input.spikeLob && input.spikeFlat) {
            horizontalSpeed = GameConfig.LONG_LOB_SPIKE_HORIZONTAL_SPEED;
            verticalVelocity = GameConfig.LONG_LOB_SPIKE_VELOCITY_Y;
        } else if (input.spikeLob) {
            horizontalSpeed = GameConfig.LOB_SPIKE_HORIZONTAL_SPEED;
            verticalVelocity = GameConfig.LOB_SPIKE_VELOCITY_Y;
        } else if (input.spikeFlat) {
            horizontalSpeed = GameConfig.FLAT_SPIKE_HORIZONTAL_SPEED;
            verticalVelocity = GameConfig.FLAT_SPIKE_VELOCITY_Y;
        } else if (input.spikeShort) {
            horizontalSpeed = GameConfig.SHORT_SPIKE_HORIZONTAL_SPEED;
            verticalVelocity = GameConfig.SHORT_SPIKE_VELOCITY_Y;
        }

        model.ball.vx = SideRules.directionTowardOpponent(redSide) * horizontalSpeed;
        model.ball.vy = verticalVelocity;
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
        if (!(player instanceof QuickAttacker blocker)) {
            return false;
        }
        HitBox blockBox = blocker.activeBlockHitBox();
        if (blockBox == null) return false;

        if (model.hasBlocked(blocker.redSide)) {
            return false;
        }

        if (!blockBox.intersectsBall(model.ball)) {
            return false;
        }

        pushBallOutsideHitBox(blockBox);
        reflectBallFromBlock(blocker, blockBox);
        model.spikeEffect.clearSpikeTrail();

        // 攔網屬於高旋轉碰球，保留 attack_way 的高速落地旋轉。
        model.ball.useFastFloorBounceSpin();

        // 發球犯規也要先產生與正常攔網相同的球體反彈，再結束這一球。
        if (!model.isServeReceptionComplete()) {
            model.recordBlock(blocker.redSide, blocker);
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

        // 兩種攔網框共用同一份不計次的攔網紀錄。
        // 攔網接觸獨立記錄，固定不占用球隊的三次觸球次數。
        model.recordBlock(blocker.redSide, blocker);

        // 最後碰球為攔網時，依實際落地判 TOUCH OUT；下一次一般觸球／扣球會清除。
        model.pendingTouchOut = true;
        model.pendingTouchOutWinner = !blocker.redSide;

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

    private void handleTouchAnimation(Player player, int hitCountBeforeTouch) {
        if (player instanceof Setter) {
            playSetterAnimationIfAllowed(player, hitCountBeforeTouch);
            return;
        }

        if (player.isAttackReady() || player.isAttackSwinging()) {
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

    private void reflectBallFromBlock(QuickAttacker blocker, HitBox box) {
        double normalX = model.ball.x - box.getCenterX();
        double normalY = model.ball.y - box.getCenterY();
        double normalLength = Math.sqrt(normalX * normalX + normalY * normalY);

        if (normalLength < MIN_NORMAL_LENGTH) {
            normalX = model.ball.vx == 0
                    ? SideRules.directionTowardOpponent(blocker.redSide)
                    : Math.signum(model.ball.vx);
            normalY = CENTER_CONTACT_NORMAL_Y;
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
            double speed = Math.hypot(model.ball.vx, model.ball.vy);
            reflectedVx = normalX * speed;
            reflectedVy = normalY * speed;
        }

        double bounce = box == blocker.headBlockHitBox
                ? GameConfig.BLOCK_HEAD_BOUNCE : GameConfig.BLOCK_HITBOX_BOUNCE;
        reflectedVx *= bounce;
        reflectedVy *= bounce;

        model.ball.vx = reflectedVx;
        model.ball.vy = reflectedVy;
    }

    private void setBallVelocity(BallTarget target) {
        BallTrajectory.Velocity velocity = BallTrajectory.estimateVelocityToTargetAtSpeed(
                model.ball.x,
                model.ball.y,
                target.x,
                target.y,
                target.launchSpeed,
                GameConfig.GRAVITY
        );

        model.ball.vx = velocity.x();
        model.ball.vy = velocity.y();
    }

    private void setSetterBallVelocity(BallTarget target) {
        BallTrajectory.Velocity velocity = BallTrajectory.throughTarget(model.ball.x, model.ball.y,
                target.x, target.y, GameConfig.SETTER_SET_APEX_Y, GameConfig.GRAVITY);
        model.ball.vx = velocity.x();
        model.ball.vy = velocity.y();
    }

    private void setQuickAttackBallVelocity(boolean redSide) {
        double apexX = redSide ? GameConfig.RED_QUICK_SET_APEX_X : GameConfig.BLUE_QUICK_SET_APEX_X;
        BallTrajectory.ApexLaunch launch = BallTrajectory.toApex(model.ball.y,
                GameConfig.QUICK_SET_APEX_Y, GameConfig.GRAVITY);
        model.ball.vx = (apexX - model.ball.x) / launch.durationFrames();
        model.ball.vy = launch.velocityY();
    }

    private static class BallTarget {
        final double x;
        final double y;
        final double launchSpeed;

        private BallTarget(double x, double y, double launchSpeed) {
            this.x = x;
            this.y = y;
            this.launchSpeed = launchSpeed;
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
            return new BallTarget(targetX, team.setter.y + SETTER_TARGET_OFFSET_Y, TO_SETTER_PASS_SPEED);
        }

        private static BallTarget attackTarget(boolean redSide) {
            return new BallTarget(
                    SideRules.thirdTouchTargetX(redSide),
                    GameConfig.FLOOR_Y - THIRD_TOUCH_TARGET_HEIGHT,
                    THIRD_TOUCH_PASS_SPEED
            );
        }

        private static BallTarget setterThirdTouchTarget(boolean redSide) {
            return new BallTarget(
                    SideRules.setterThirdTouchTargetX(redSide),
                    GameConfig.FLOOR_Y - THIRD_TOUCH_TARGET_HEIGHT,
                    SETTER_THIRD_TOUCH_PASS_SPEED
            );
        }
    }
}
