package model;

import java.util.Random;

public class ServeBallController {
    private final GameModel model;
    private final Random random = new Random();

    public ServeBallController(GameModel model) {
        this.model = model;
    }

    public void prepareServe(boolean redSide) {
        Team team = redSide ? model.redTeam : model.blueTeam;
        Player server = team.backPlayer;

        server.x = redSide ? GameConfig.RED_BACK_SERVE_X : GameConfig.BLUE_BACK_SERVE_X;
        server.y = redSide ? GameConfig.RED_BACK_SERVE_Y : GameConfig.BLUE_BACK_SERVE_Y;
        PlayerPhysics.clearMotionAndActions(server);

        placeBallForServe(server, redSide);
    }

    public void launchServe(ServeType serveType, boolean redSide) {
        double direction = SideRules.directionTowardOpponent(redSide);
        model.setLastHitTeam(redSide);
        setBallVelocity(serveType.baseVx * direction, serveType.baseVy);
    }

    private void placeBallForServe(Player server, boolean redSide) {
        Ball ball = model.ball;

        ball.x = redSide
                ? server.x + GameConfig.RED_SERVE_BALL_OFFSET_X
                : server.x + server.imageWidth - GameConfig.BLUE_SERVE_BALL_OFFSET_X;

        ball.y = server.y + serveBallOffsetY(redSide);
        ball.vx = 0;
        ball.vy = 0;
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
