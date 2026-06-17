package model;

import java.util.Random;

public class ServeHandler {
    private final GameModel model;
    private final Random random = new Random();

    private boolean waitingForServe = true;
    private boolean redServing = true;
    private boolean lastServePressed = false;

    public ServeHandler(GameModel model) {
        this.model = model;
    }

    public boolean isWaitingForServe() {
        return waitingForServe;
    }

    public boolean isRedServing() {
        return redServing;
    }

    public void setWaitingForServe(boolean waiting) {
        this.waitingForServe = waiting;
    }

    public void setRedServing(boolean redServing) {
        this.redServing = redServing;
    }

    public void reset() {
        this.waitingForServe = true;
        this.redServing = true;
        this.lastServePressed = false;
    }

    public void update(TeamInput redInput, TeamInput blueInput) {
        TeamInput currentInput = redServing ? redInput : blueInput;
        
        // 在等待發球期間，限制球員動作（例如不能隨意移動或跳躍，除非是發球動作的一部分）
        currentInput.backLeft = false;
        currentInput.backRight = false;
        currentInput.backJump = false;
        currentInput.backDive = false;

        boolean justPressedServe = currentInput.servePressed && !lastServePressed;

        if (justPressedServe) {
            prepareServe(redServing);
            launchServe(currentInput.serveType, redServing);
            waitingForServe = false;
            model.resetCounters();
        }

        if (waitingForServe) {
            prepareServe(redServing);
        }

        lastServePressed = currentInput.servePressed;
    }

    public void prepareServe(boolean redSide) {
        Team team = redSide ? model.redTeam : model.blueTeam;
        Player server = team.backPlayer;

        // 發球方 backPlayer 使用指定發球站位
        server.x = redSide ? GameConfig.RED_BACK_SERVE_X : GameConfig.BLUE_BACK_SERVE_X;
        server.y = redSide ? GameConfig.RED_BACK_SERVE_Y : GameConfig.BLUE_BACK_SERVE_Y;
        server.vx = 0;
        server.vy = 0;
        server.jumping = false;
        server.diving = false;
        server.attacking = false;
        server.blocking = false;

        placeBallForServe(redSide);
    }

    private void placeBallForServe(boolean redSide) {
        Team team = redSide ? model.redTeam : model.blueTeam;
        Player server = team.backPlayer;
        Ball ball = model.ball;

        if (redSide) {
            ball.x = server.x + GameConfig.RED_SERVE_BALL_OFFSET_X;
        } else {
            ball.x = server.x + server.imageWidth - GameConfig.BLUE_SERVE_BALL_OFFSET_X;
        }

        ball.y = server.y + (redSide ? GameConfig.RED_SERVE_BALL_OFFSET_Y : GameConfig.BLUE_SERVE_BALL_OFFSET_Y);
        ball.vx = 0;
        ball.vy = 0;
    }

    private void launchServe(ServeType serveType, boolean redSide) {
        Team team = redSide ? model.redTeam : model.blueTeam;
        Player server = team.backPlayer;
        Ball ball = model.ball;

        placeBallForServe(redSide);

        double direction = redSide ? 1.0 : -1.0;

        if (serveType == ServeType.JUMP) {
            server.vy = GameConfig.PLAYER_JUMP_SPEED;
            server.jumping = true;
            ball.y -= 36;
        }

        setServeVelocity(serveType.baseVx * direction, serveType.baseVy);
    }

    private void setServeVelocity(double baseVx, double baseVy) {
        model.ball.vx = baseVx + randomRange(GameConfig.SERVE_RANDOM_VX_RANGE);
        model.ball.vy = baseVy + randomRange(GameConfig.SERVE_RANDOM_VY_RANGE);
    }

    private double randomRange(double range) {
        return (random.nextDouble() * 2.0 - 1.0) * range;
    }
}