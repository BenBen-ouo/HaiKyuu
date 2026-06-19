package model;

public class GameModel {
    public Ball ball = new Ball(GameConfig.SCREEN_WIDTH / 2.0, 130);
    public Team redTeam = new Team(true);
    public Team blueTeam = new Team(false);

    public int redScore = 0;
    public int blueScore = 0;

    // 擊球計數器。保留 public 欄位，避免其他舊程式碼需要改。
    public int redHitCount = 0;
    public int blueHitCount = 0;

    private final ServeHandler serveHandler = new ServeHandler(this);
    private final RallyContactHandler contactHandler = new RallyContactHandler(this);

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
        lastHitTeam = null;
        lastTouchWasBlock = false;
    }

    public void update(TeamInput redInput, TeamInput blueInput) {
        updateBallSideInfo(redInput, blueInput);

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

        updateTeams(redInput, blueInput);
        serveHandler.updateAfterTeams();

        if (serveHandler.shouldUpdateBall()) {
            ball.update();
            serveHandler.updateAfterBall();
            processScoringIfBallLanded();

            if (!isRallyOver) {
                resetCountersIfBallCrossesNet();
            }
        }

        ball.collideWithNet();

        if (!isRallyOver && serveHandler.canTeamCollideWithBall(true)) {
            contactHandler.collideTeam(redTeam, true);
        }

        if (!isRallyOver && serveHandler.canTeamCollideWithBall(false)) {
            contactHandler.collideTeam(blueTeam, false);
        }

        serveHandler.finishFrame();
    }

    public void resetCounters() {
        redHitCount = 0;
        blueHitCount = 0;
        redLastHitter = null;
        blueLastHitter = null;
        lastTouchWasBlock = false;
    }

    int getHitCount(boolean redSide) {
        return redSide ? redHitCount : blueHitCount;
    }

    Player getLastHitter(boolean redSide) {
        if (lastTouchWasBlock) {
            return null;
        }

        return redSide ? redLastHitter : blueLastHitter;
    }

    void recordHit(boolean redSide, Player hitter) {
        lastHitTeam = redSide;
        lastTouchWasBlock = hitter.blocking;

        if (redSide) {
            redLastHitter = hitter;
            if (!hitter.blocking) {
                redHitCount++;
            }
        } else {
            blueLastHitter = hitter;
            if (!hitter.blocking) {
                blueHitCount++;
            }
        }
    }

    private void updateDeadBall(TeamInput redInput, TeamInput blueInput) {
        deadBallTimer--;
        ball.update();
        updateTeams(redInput, blueInput);

        if (deadBallTimer <= 0) {
            isRallyOver = false;
            serveHandler.setWaitingForServe(true);
            resetCounters();
            lastHitTeam = null;
        }
    }

    private void processScoringIfBallLanded() {
        if (ball.y + ball.radius >= GameConfig.FLOOR_Y) {
            processScoring();
        }
    }

    private void processScoring() {
        if (isRallyOver) {
            return;
        }

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

    private void updateTeams(TeamInput redInput, TeamInput blueInput) {
        redTeam.update(redInput);
        blueTeam.update(blueInput);
    }

    private void updateBallSideInfo(TeamInput redInput, TeamInput blueInput) {
        fillBallSideInfo(redInput, true);
        fillBallSideInfo(blueInput, false);
    }

    private void fillBallSideInfo(TeamInput input, boolean redSide) {
        boolean ownSide = SideRules.isBallOnOwnSide(redSide, ball.x);
        input.ballOnOwnSide = ownSide;
        input.ballOnOpponentSide = !ownSide;
    }

    private void configureBackPlayerAction(TeamInput input, int hitCount) {
        boolean actionPressed = input.backJump || input.backDive;
        boolean firstTouchNotCompleted = hitCount == 0;

        input.backJump = actionPressed && !firstTouchNotCompleted;
        input.backDive = actionPressed && firstTouchNotCompleted;
    }

    private void resetCountersIfBallCrossesNet() {
        double netX = GameConfig.NET_X;
        boolean crossedLeftToRight = lastBallX < netX && ball.x >= netX;
        boolean crossedRightToLeft = lastBallX > netX && ball.x <= netX;

        if (crossedLeftToRight || crossedRightToLeft) {
            resetCounters();
        }
    }
}