package model;

public class GameModel {
    public Ball ball = new Ball(GameConfig.SCREEN_WIDTH / 2.0, 130);
    public Team redTeam = new Team(true);
    public Team blueTeam = new Team(false);

    public int redScore = 0;
    public int blueScore = 0;

    private final ServeHandler serveHandler = new ServeHandler(this);

    // 擊球計數器
    public int redHitCount = 0;
    public int blueHitCount = 0;

    // 記錄最後一次觸球者，用於防止連擊
    private Player redLastHitter = null;
    private Player blueLastHitter = null;

    private double lastBallX;

    public GameModel() {
        // serveHandler 預設就會準備好第一次發球
    }

    public ServeHandler getServeHandler() {
        return serveHandler;
    }

    public void restart() {
        GameResetter.reset(this);
    }

    public void update(TeamInput redInput, TeamInput blueInput) {
        fillBallSideInfo(redInput, true);
        fillBallSideInfo(blueInput, false);

        if (serveHandler.isWaitingForServe()) {
            serveHandler.update(redInput, blueInput);
            configureBackPlayerAction(redInput, redHitCount);
            configureBackPlayerAction(blueInput, blueHitCount);

            redTeam.update(redInput);
            blueTeam.update(blueInput);

            if (serveHandler.isWaitingForServe()) {
                return;
            }
        } else {
            lastBallX = ball.x;
            configureBackPlayerAction(redInput, redHitCount);
            configureBackPlayerAction(blueInput, blueHitCount);

            redTeam.update(redInput);
            blueTeam.update(blueInput);
            ball.update();

            // 偵測球是否過網，過網則重置兩隊的計數器與最後觸球者
            double netX = GameConfig.NET_X;
            if ((lastBallX < netX && ball.x >= netX) || (lastBallX > netX && ball.x <= netX)) {
                resetCounters();
            }
        }

        ball.collideWithNet();
        collideTeam(redTeam, true);
        collideTeam(blueTeam, false);
    }

    public void resetCounters() {
        redHitCount = 0;
        blueHitCount = 0;
        redLastHitter = null;
        blueLastHitter = null;
    }

    private void fillBallSideInfo(TeamInput input, boolean redSide) {
        boolean ownSide = redSide ? ball.x <= GameConfig.NET_X : ball.x >= GameConfig.NET_X;
        input.ballOnOwnSide = ownSide;
        input.ballOnOpponentSide = !ownSide;
    }

    private void configureBackPlayerAction(TeamInput input, int hitCount) {
        boolean actionPressed = input.backJump || input.backDive;

        if (hitCount == 0) {
            // 還沒有接起本隊第一球：Space / 0 是撲球，不是起跳攻擊。
            input.backJump = false;
            input.backDive = actionPressed;
        } else {
            // 已經接起第一球後：Space / 0 才進入 back 攻擊起跳與空中揮臂判斷。
            input.backJump = actionPressed;
            input.backDive = false;
        }
    }

    private void collideTeam(Team team, boolean redSide) {
        int currentHitCount = redSide ? redHitCount : blueHitCount;
        Player lastHitter = redSide ? redLastHitter : blueLastHitter;

        double targetX;
        double targetY;
        double power;

        if (currentHitCount == 0 || currentHitCount == 1) {
            // 第一、二球：固定接到舉球員頭上
            targetX = team.setter.x + team.setter.imageWidth / 2.0;
            targetY = team.setter.y - 20;
            power = 13.5;
        } else {
            // 第三球及以後：傳到對面場地
            targetX = redSide ? (GameConfig.SCREEN_WIDTH * 0.8) : (GameConfig.SCREEN_WIDTH * 0.2);
            targetY = GameConfig.FLOOR_Y - 50;
            power = 15.5;
        }

        for (Player player : team.getPlayers()) {
            if (player == lastHitter) continue;

            // 如果是舉球員本人接到，傳到自己正上方
            double currentTargetX = (player == team.setter && (currentHitCount == 0 || currentHitCount == 1))
                    ? ball.x
                    : targetX;

            if (collidePlayer(player, power, currentTargetX, targetY)) {
                playTouchAnimation(player, currentHitCount);

                if (redSide) {
                    redHitCount++;
                    redLastHitter = player;
                } else {
                    blueHitCount++;
                    blueLastHitter = player;
                }
                break;
            }
        }
    }

    private void playTouchAnimation(Player player, int currentHitCount) {
        if (player instanceof Setter) {
            if (currentHitCount <= 2) {
                player.playSettingAnimation();
            }
            return;
        }

        if (player.isAttackReady() || player.isAttackSwinging()) {
            // 之後真正扣球邏輯可以從這裡接：
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

    private boolean collidePlayer(Player player, double power, double targetX, double targetY) {
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

        double[] vel = PhysicsUtils.calculateVelocityToTarget(
                ball.x,
                ball.y,
                targetX,
                targetY,
                power,
                GameConfig.GRAVITY
        );
        ball.vx = vel[0];
        ball.vy = vel[1];

        return true;
    }
}