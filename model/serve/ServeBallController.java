/*
專門處理發球時球的位置與速度。
包含發球前擺球、一般發球出球與發球速度隨機誤差。
*/
package model.serve;

import model.GameConfig;
import model.GameModel;
import model.SideRules;
import model.ball.Ball;
import model.player.BackPlayer;
import model.player.Player;
import model.player.PlayerPhysics;
import model.player.Team;

import java.util.Random;

public class ServeBallController {
    private final GameModel model;
    private final Random random = new Random();

    public ServeBallController(GameModel model) {
        this.model = model;
    }

    public void prepareServe(boolean redSide) {
        Player server = positionServerForServe(redSide);
        placeBallForServe(server, redSide);
    }

    /** 只讓下一球發球員就位，不移動或重設仍在場上的球。 */
    public Player positionServerForServe(boolean redSide) {
        Team team = redSide ? model.redTeam : model.blueTeam;
        Player server = team.backPlayer;

        server.x = redSide ? GameConfig.RED_BACK_SERVE_X : GameConfig.BLUE_BACK_SERVE_X;
        server.y = redSide ? GameConfig.RED_BACK_SERVE_Y : GameConfig.BLUE_BACK_SERVE_Y;
        PlayerPhysics.clearMotionAndActions(server);
        return server;
    }

    public void launchServe(ServeType serveType, boolean redSide) {
        double direction = SideRules.directionTowardOpponent(redSide);
        model.setLastHitTeam(redSide);
        model.ball.stopRotation();
        model.ball.useSlowFloorBounceSpin();
        setBallVelocity(serveType.baseVx * direction, serveType.baseVy);
    }

    public void tossJumpServe(boolean redSide) {
        Ball ball = model.ball;
        double apexY = GameConfig.SETTER_SET_APEX_Y;
        double gravity = GameConfig.GRAVITY;
        int framesToApex = Math.max(1, (int) Math.ceil(
                (-1 + Math.sqrt(1 + 8 * (ball.y - apexY) / gravity)) / 2));
        ball.vy = (apexY - ball.y) / framesToApex
                - gravity * (framesToApex + 1) / 2;
        double linearTerm = ball.vy + gravity / 2;
        double landingFrames = (-linearTerm + Math.sqrt(linearTerm * linearTerm
                + 2 * gravity * (GameConfig.JUMP_SERVE_TOSS_LANDING_Y - ball.y))) / gravity;
        BackPlayer server = redSide ? model.redTeam.backPlayer : model.blueTeam.backPlayer;
        double targetX = server.plannedAttackHitBoxCenterAtApex();
        ball.vx = (targetX - ball.x) / Math.max(1, landingFrames);
        ball.stopRotation();
        ball.useSlowFloorBounceSpin();
    }

    public void hitJumpServe(boolean redSide, boolean shortArc, boolean slowHorizontal,
                             boolean longArc) {
        double speedX = slowHorizontal ? GameConfig.SERVE_JUMP_SLOW_VX : GameConfig.SERVE_JUMP_VX;
        double speedY = shortArc ? GameConfig.SERVE_JUMP_SHORT_VY
                : longArc ? GameConfig.SERVE_JUMP_LONG_VY : GameConfig.SERVE_JUMP_VY;
        model.ball.vx = SideRules.directionTowardOpponent(redSide) * speedX;
        model.ball.vy = speedY;
        model.ball.setRotationSpeed(redSide ? GameConfig.SPIKE_SPIN_SPEED : -GameConfig.SPIKE_SPIN_SPEED);
        model.ball.useFastFloorBounceSpin();
        model.setLastHitTeam(redSide);
        model.spikeEffect.startSpikeTrail(redSide);
    }

    private void placeBallForServe(Player server, boolean redSide) {
        Ball ball = model.ball;

        ball.x = redSide ? GameConfig.RED_SERVE_BALL_X : GameConfig.BLUE_SERVE_BALL_X;

        ball.y = server.y + serveBallOffsetY(redSide);
        ball.vx = 0;
        ball.vy = 0;
        ball.stopRotation();
        ball.useSlowFloorBounceSpin();
    }

    private double serveBallOffsetY(boolean redSide) {
        return redSide ? GameConfig.RED_SERVE_BALL_OFFSET_Y : GameConfig.BLUE_SERVE_BALL_OFFSET_Y;
    }

    private void setBallVelocity(double baseVx, double baseVy) {
        model.ball.vx = baseVx + randomRange(GameConfig.SERVE_RANDOM_VX_RANGE);
        model.ball.vy = baseVy + randomRange(GameConfig.SERVE_RANDOM_VY_RANGE);
    }

    private double randomRange(double range) {
        return (random.nextDouble() * 2.0 - 1.0) * range;
    }
}
