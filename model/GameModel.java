package model;

public class GameModel {
    public Ball ball = new Ball(GameConfig.SCREEN_WIDTH / 2.0, 130);
    public Team leftTeam = new Team(true);
    public Team rightTeam = new Team(false);

    public int leftScore = 0;
    public int rightScore = 0;

    public void update(TeamInput leftInput, TeamInput rightInput) {
        leftTeam.update(leftInput);
        rightTeam.update(rightInput);

        ball.update();

        collideNet();
        collideTeam(leftTeam, true);
        collideTeam(rightTeam, false);

        // TODO: 之後你可以在這裡接得分規則。
    }

    private void collideTeam(Team team, boolean leftSide) {
        collidePlayer(team.backPlayer, leftSide, 8.5);
        collidePlayer(team.setter, leftSide, 7.5);
        collidePlayer(team.quickAttacker, leftSide, team.quickAttacker.attacking ? 13.0 : 8.0);
        collidePlayer(team.wingSpiker, leftSide, team.wingSpiker.attacking ? 13.5 : 7.5);
    }

    private void collidePlayer(Player player, boolean leftSide, double power) {
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

        ball.vx = dx / len * power + (leftSide ? 2.4 : -2.4);
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