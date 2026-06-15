package model;

public class GameModel {
    public Ball ball = new Ball(GameConfig.SCREEN_WIDTH / 2.0, 130);
    public Team redTeam = new Team(true);
    public Team blueTeam = new Team(false);

    public int redScore = 0;
    public int blueScore = 0;

    // 擊球計數器
    public int redHitCount = 0;
    public int blueHitCount = 0;
    
    // 記錄最後一次觸球者，用於防止連擊
    private Player redLastHitter = null;
    private Player blueLastHitter = null;
    
    private double lastBallX;

    public void update(TeamInput redInput, TeamInput blueInput) {
        lastBallX = ball.x;
        redTeam.update(redInput);
        blueTeam.update(blueInput);

        ball.update();

        // 偵測球是否過網，過網則重置兩隊的計數器與最後觸球者
        double netX = GameConfig.NET_X;
        if ((lastBallX < netX && ball.x >= netX) || (lastBallX > netX && ball.x <= netX)) {
            resetCounters();
        }

        collideNet();
        collideTeam(redTeam, true);
        collideTeam(blueTeam, false);

        // TODO: 得分規則
    }

    private void resetCounters() {
        redHitCount = 0;
        blueHitCount = 0;
        redLastHitter = null;
        blueLastHitter = null;
    }

    private void collideTeam(Team team, boolean redSide) {
        int currentHitCount = redSide ? redHitCount : blueHitCount;
        Player lastHitter = redSide ? redLastHitter : blueLastHitter;

        // 定義目標
        Object targetObj = null;
        double power = 14.0;

        if (currentHitCount == 0 || currentHitCount == 1) {
            // 第一、二球：固定接到舉球員 (Setter) 頭上
            targetObj = team.setter;
            power = 13.5;
        } else {
            // 第三球及以後：傳到對面場地
            double targetX = redSide ? (GameConfig.SCREEN_WIDTH * 0.8) : (GameConfig.SCREEN_WIDTH * 0.2);
            targetObj = new double[]{targetX, GameConfig.FLOOR_Y - 50};
            power = 15.5;
        }

        // 判定各個球員 (同一幀內一隊只能有一人觸球，避免計數器跳號)
        boolean hitOccurred = false;
        if (!hitOccurred) hitOccurred = checkPlayerCollision(team.backPlayer, redSide, power, targetObj, lastHitter);
        if (!hitOccurred) hitOccurred = checkPlayerCollision(team.setter, redSide, power, targetObj, lastHitter);
        if (!hitOccurred) hitOccurred = checkPlayerCollision(team.quickAttacker, redSide, power, targetObj, lastHitter);
        if (!hitOccurred) hitOccurred = checkPlayerCollision(team.wingSpiker, redSide, power, targetObj, lastHitter);
    }

    private boolean checkPlayerCollision(Player player, boolean redSide, double power, Object targetObj, Player lastHitter) {
        // 防止連擊：同一個人不能連碰兩次
        if (player == lastHitter) {
            return false;
        }

        if (collidePlayer(player, redSide, power, targetObj)) {
            if (redSide) {
                redHitCount++;
                redLastHitter = player;
            } else {
                blueHitCount++;
                blueLastHitter = player;
            }
            return true;
        }
        return false;
    }

    private boolean collidePlayer(Player player, boolean redSide, double power, Object targetObj) {
        if (!player.intersectsBall(ball)) {
            return false;
        }

        double centerX = player.getHitBoxCenterX();
        double centerY = player.getHitBoxCenterY();
        double dx = ball.x - centerX;
        double dy = ball.y - centerY;
        double len = Math.max(1, Math.sqrt(dx * dx + dy * dy));

        // 避免球卡在人物身上
        ball.x += dx / len * 6;
        ball.y += dy / len * 6;

        if (targetObj != null) {
            double targetX, targetY;
            if (targetObj instanceof Player) {
                Player p = (Player) targetObj;
                targetX = p.x + p.imageWidth / 2.0;
                // 如果是舉球員本人接到，傳到自己正上方
                if (player == p) {
                    targetX = ball.x;
                }
                targetY = p.y - 20;
            } else {
                double[] coords = (double[]) targetObj;
                targetX = coords[0];
                targetY = coords[1];
            }

            double g = GameConfig.GRAVITY;
            double C = power;
            double deltaX = targetX - ball.x;
            double deltaY = targetY - ball.y;
            // 避免 deltaX 太小導致物理公式崩潰
            if (Math.abs(deltaX) < 1) deltaX = (deltaX >= 0 ? 1 : -1);
            
            double x2 = deltaX * deltaX;
            double A = deltaY * deltaY + x2;
            double B = -x2 * (g * deltaY + C * C);
            double D = 0.25 * g * g * x2 * x2;
            double discriminant = B * B - 4 * A * D;

            if (discriminant >= 0) {
                double U = (-B - Math.sqrt(discriminant)) / (2.0 * A);
                if (U > 0) {
                    ball.vx = Math.sqrt(U) * Math.signum(deltaX);
                    ball.vy = -Math.sqrt(Math.max(0, C * C - U));
                } else {
                    fallbackPass(deltaX, C);
                }
            } else {
                fallbackPass(deltaX, C);
            }
        } else {
            ball.vx = dx / len * power + (redSide ? 2.5 : -2.5);
            ball.vy = Math.min(-6.0, dy / len * power - 4.5);
        }
        return true;
    }

    private void fallbackPass(double deltaX, double C) {
        ball.vx = Math.sqrt(C * C / 2.0) * Math.signum(deltaX);
        ball.vy = -Math.sqrt(C * C / 2.0);
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
