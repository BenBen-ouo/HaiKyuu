package model;

import java.util.Random;

public class GameModel {
    public Ball ball = new Ball(GameConfig.SCREEN_WIDTH / 2.0, 130);
    public Team redTeam = new Team(true);
    public Team blueTeam = new Team(false);

    public int redScore = 0;
    public int blueScore = 0;

    private boolean waitingForServe = true;
    private boolean redServing = true;
    private boolean lastRedServePressed = false;

    private final Random random = new Random();

    public GameModel() {
        prepareRedBackServe();
    }

    public void update(TeamInput redInput, TeamInput blueInput) {
        if (waitingForServe) {
            handleServeWaiting(redInput);

            redTeam.update(redInput);
            blueTeam.update(blueInput);

            if (waitingForServe) {
                prepareRedBackServe();
                lastRedServePressed = redInput.servePressed;
                return;
            }
        } else {
            redTeam.update(redInput);
            blueTeam.update(blueInput);
            ball.update();
        }

        collideNet();
        collideTeam(redTeam, true);
        collideTeam(blueTeam, false);

        lastRedServePressed = redInput.servePressed;

        // TODO: 之後你可以在這裡接得分規則。
        // TODO: 之後得分後可以把 waitingForServe 改回 true，並判斷下一球誰發球。
    }

    private void handleServeWaiting(TeamInput redInput) {
        if (!redServing) {
            return;
        }

        // 發球等待中，WASD 只拿來決定球種，不讓 backPlayer 移動或跳。
        redInput.backLeft = false;
        redInput.backRight = false;
        redInput.backJump = false;
        redInput.backDive = false;

        boolean justPressedServe = redInput.servePressed && !lastRedServePressed;

        if (justPressedServe) {
            prepareRedBackServe();
            launchRedBackServe(redInput.serveType);
            waitingForServe = false;
        }
    }

    private void prepareRedBackServe() {
        Player server = redTeam.backPlayer;

        // 發球方 backPlayer 使用指定發球站位，不使用原本預設站位
        server.x = GameConfig.RED_BACK_SERVE_X;
        server.y = GameConfig.RED_BACK_SERVE_Y;
        server.vx = 0;
        server.vy = 0;
        server.jumping = false;
        server.diving = false;
        server.attacking = false;
        server.blocking = false;

        placeBallForRedBackServe();
    }

    private void placeBallForRedBackServe() {
        Player server = redTeam.backPlayer;

        ball.x = server.x + GameConfig.RED_SERVE_BALL_OFFSET_X;
        ball.y = server.y + GameConfig.RED_SERVE_BALL_OFFSET_Y;

        ball.vx = 0;
        ball.vy = 0;
    }

    private void launchRedBackServe(int serveType) {
        Player server = redTeam.backPlayer;

        ball.x = server.x + GameConfig.RED_SERVE_BALL_OFFSET_X;
        ball.y = server.y + GameConfig.RED_SERVE_BALL_OFFSET_Y;

        switch (serveType) {
            case TeamInput.SERVE_CEILING -> {
                setServeVelocity(
                        GameConfig.SERVE_CEILING_VX,
                        GameConfig.SERVE_CEILING_VY
                );
            }

            case TeamInput.SERVE_LOW_NET -> {
                setServeVelocity(
                        GameConfig.SERVE_LOW_NET_VX,
                        GameConfig.SERVE_LOW_NET_VY
                );
            }

            case TeamInput.SERVE_SHORT -> {
                setServeVelocity(
                        GameConfig.SERVE_SHORT_VX,
                        GameConfig.SERVE_SHORT_VY
                );
            }

            case TeamInput.SERVE_JUMP -> {
                server.vy = GameConfig.PLAYER_JUMP_SPEED;
                server.jumping = true;

                ball.y -= 36;

                setServeVelocity(
                        GameConfig.SERVE_JUMP_VX,
                        GameConfig.SERVE_JUMP_VY
                );
            }

            default -> {
                setServeVelocity(
                        GameConfig.SERVE_NORMAL_VX,
                        GameConfig.SERVE_NORMAL_VY
                );
            }
        }
    }

    private void setServeVelocity(double baseVx, double baseVy) {
        ball.vx = baseVx + randomRange(GameConfig.SERVE_RANDOM_VX_RANGE);
        ball.vy = baseVy + randomRange(GameConfig.SERVE_RANDOM_VY_RANGE);
    }

    private double randomRange(double range) {
        return (random.nextDouble() * 2.0 - 1.0) * range;
    }

    private void collideTeam(Team team, boolean redSide) {
        collidePlayer(team.backPlayer, redSide, 8.5);
        collidePlayer(team.setter, redSide, 7.5);
        collidePlayer(team.quickAttacker, redSide, team.quickAttacker.attacking ? 13.0 : 8.0);
        collidePlayer(team.wingSpiker, redSide, team.wingSpiker.attacking ? 13.5 : 7.5);
    }

    private void collidePlayer(Player player, boolean redSide, double power) {
        if (!player.intersectsBall(ball)) {
            return;
        }

        double centerX = player.getHitBoxCenterX();
        double centerY = player.getHitBoxCenterY();

        double dx = ball.x - centerX;
        double dy = ball.y - centerY;

        double len = Math.max(1, Math.sqrt(dx * dx + dy * dy));

        ball.x += dx / len * 6;
        ball.y += dy / len * 6;

        ball.vx = dx / len * power + (redSide ? 2.4 : -2.4);
        ball.vy = Math.min(-5.5, dy / len * power - 4.0);
    }

    private void collideNet() {
        double netLeft = GameConfig.NET_X - GameConfig.NET_WIDTH / 2.0;
        double netRight = GameConfig.NET_X + GameConfig.NET_WIDTH / 2.0;

        boolean hitX = ball.x + ball.radius > netLeft && ball.x - ball.radius < netRight;
        boolean hitY = ball.y + ball.radius > GameConfig.NET_TOP_Y && ball.y - ball.radius < GameConfig.FLOOR_Y;

        if (hitX && hitY) {
            if (ball.x < GameConfig.NET_X) {
                ball.x = netLeft - ball.radius;
                ball.vx = -Math.abs(ball.vx) * GameConfig.NET_BOUNCE;
            } else {
                ball.x = netRight + ball.radius;
                ball.vx = Math.abs(ball.vx) * GameConfig.NET_BOUNCE;
            }
        }
    }
}