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
    private double lastBallX;

    public ServeHandler getServeHandler() {
        return serveHandler;
    }

    public void restart() {
        GameResetter.reset(this);
    }

    public void update(TeamInput redInput, TeamInput blueInput) {
        updateBallSideInfo(redInput, blueInput);

        if (serveHandler.isWaitingForServe()) {
            updateWaitingServeFrame(redInput, blueInput);

            if (serveHandler.isWaitingForServe()) {
                return;
            }
        } else {
            updateRallyFrame(redInput, blueInput);
        }

        ball.collideWithNet();
        contactHandler.collideTeam(redTeam, true);
        contactHandler.collideTeam(blueTeam, false);
    }

    public void resetCounters() {
        redHitCount = 0;
        blueHitCount = 0;
        redLastHitter = null;
        blueLastHitter = null;
    }

    int getHitCount(boolean redSide) {
        return redSide ? redHitCount : blueHitCount;
    }

    Player getLastHitter(boolean redSide) {
        return redSide ? redLastHitter : blueLastHitter;
    }

    void recordHit(boolean redSide, Player hitter) {
        if (redSide) {
            redHitCount++;
            redLastHitter = hitter;
        } else {
            blueHitCount++;
            blueLastHitter = hitter;
        }
    }

    private void updateWaitingServeFrame(TeamInput redInput, TeamInput blueInput) {
        serveHandler.update(redInput, blueInput);
        configureBackActions(redInput, blueInput);
        updateTeams(redInput, blueInput);
    }

    private void updateRallyFrame(TeamInput redInput, TeamInput blueInput) {
        lastBallX = ball.x;
        configureBackActions(redInput, blueInput);
        updateTeams(redInput, blueInput);
        ball.update();
        resetCountersIfBallCrossesNet();
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

    private void configureBackActions(TeamInput redInput, TeamInput blueInput) {
        configureBackPlayerAction(redInput, redHitCount);
        configureBackPlayerAction(blueInput, blueHitCount);
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