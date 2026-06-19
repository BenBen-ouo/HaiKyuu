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
    private Boolean lastHitTeam = null; // true: red, false: blue, null: none
    private boolean lastTouchWasBlock = false;

    private boolean isRallyOver = false;
    private int deadBallTimer = 0;

    private double lastBallX;

    public GameModel() {
        serveHandler.setWaitingForServe(true);
    }

    public ServeHandler getServeHandler() {
        return serveHandler;
    }

    public Boolean getLastHitTeam() {
        return lastHitTeam;
    }

    public void setLastHitTeam(Boolean team) {
        this.lastHitTeam = team;
    }

    public void restart() {
        GameResetter.reset(this);
        isRallyOver = false;
        deadBallTimer = 0;
    }

    public void update(TeamInput redInput, TeamInput blueInput) {
        if (isRallyOver) {
            updateDeadBall(redInput, blueInput);
            return;
        }

        lastBallX = ball.x;

        serveHandler.updateBeforeTeams(redInput, blueInput);

        if (serveHandler.shouldUseGameBackPlayerAction(true)) {
            configureBackPlayerAction(redInput, redHitCount);
        }

        if (serveHandler.shouldUseGameBackPlayerAction(false)) {
            configureBackPlayerAction(blueInput, blueHitCount);
        }

        redTeam.update(redInput);
        blueTeam.update(blueInput);

        serveHandler.updateAfterTeams();

        if (serveHandler.shouldUpdateBall()) {
            ball.update();
            serveHandler.updateAfterBall();
            processScoringIfBallLanded();

            if (!isRallyOver) {
                resetCountersWhenBallCrossesNet();
            }
        }

        ball.collideWithNet();

        if (!isRallyOver && serveHandler.canTeamCollideWithBall(true)) {
            collideTeam(redTeam, true);
        }

        if (!isRallyOver && serveHandler.canTeamCollideWithBall(false)) {
            collideTeam(blueTeam, false);
        }

        serveHandler.finishFrame();
    }

    private void processScoringIfBallLanded() {
        if (ball.y + ball.radius >= GameConfig.FLOOR_Y) {
            processScoring();
        }
    }

    private void processScoring() {
        if (isRallyOver) return;

        isRallyOver = true;
        deadBallTimer = 60;

        boolean redWins = ScoringLogic.determineWinner(ball.x, lastHitTeam, serveHandler.isRedServing());

        if (redWins) {
            redScore++;
            serveHandler.setRedServing(true);
        } else {
            blueScore++;
            serveHandler.setRedServing(false);
        }
    }

    private void updateDeadBall(TeamInput redInput, TeamInput blueInput) {
        deadBallTimer--;
        ball.update(); // 讓球繼續彈跳
        redTeam.update(redInput); // 球員仍可移動但不能擊球
        blueTeam.update(blueInput);

        if (deadBallTimer <= 0) {
            isRallyOver = false;
            serveHandler.setWaitingForServe(true);
            resetCounters();
        }
    }

    public void resetCounters() {
        redHitCount = 0;
        blueHitCount = 0;
        redLastHitter = null;
        blueLastHitter = null;
        lastHitTeam = null;
        lastTouchWasBlock = false;
    }

    private void configureBackPlayerAction(TeamInput input, int hitCount) {
        boolean actionPressed = input.backJump || input.backDive;

        if (hitCount == 0) {
            input.backJump = false;
            input.backDive = actionPressed;
        } else {
            input.backJump = actionPressed;
            input.backDive = false;
        }
    }

    private void resetCountersWhenBallCrossesNet() {
        double netX = GameConfig.NET_X;

        if ((lastBallX < netX && ball.x >= netX) || (lastBallX > netX && ball.x <= netX)) {
            resetCounters();
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
            // 如果是上一次觸球者，且上一次「不是」攔網，則跳過 (防止非法連擊)
            if (player == lastHitter && !lastTouchWasBlock) continue;

            // 如果是舉球員本人接到，傳到自己正上方
            double currentTargetX = (player == team.setter && (currentHitCount == 0 || currentHitCount == 1))
                    ? ball.x
                    : targetX;

            if (collidePlayer(player, power, currentTargetX, targetY)) {
                lastHitTeam = redSide; // 更新最後觸球隊伍 (用於出界判定)

                if (redSide) {
                    redLastHitter = player;
                    if (!player.blocking) redHitCount++; // 攔網不計次
                } else {
                    blueLastHitter = player;
                    if (!player.blocking) blueHitCount++; // 攔網不計次
                }

                lastTouchWasBlock = player.blocking; // 紀錄本次觸球是否為攔網
                break;
            }
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