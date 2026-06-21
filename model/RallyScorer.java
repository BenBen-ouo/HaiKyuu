/*
處理球落地後的得分、死球等待時間與下一球發球方準備。
目前依照落點、最後觸球隊伍與發球方決定得分隊伍。
*/
package model;

public class RallyScorer {
    private static final int DEAD_BALL_FRAMES = 60;

    private final GameModel model;
    private boolean rallyOver = false;
    private int deadBallTimer = 0;

    public RallyScorer(GameModel model) {
        this.model = model;
    }

    public boolean isRallyOver() {
        return rallyOver;
    }

    public void reset() {
        rallyOver = false;
        deadBallTimer = 0;
    }

    public void updateDeadBall(TeamInput redInput, TeamInput blueInput) {
        deadBallTimer--;
        model.ball.update();
        model.effects.update();
        model.redTeam.update(redInput);
        model.blueTeam.update(blueInput);

        if (deadBallTimer <= 0) {
            prepareNextServe();
        }
    }

    public void checkBallLanding() {
        if (!rallyOver && model.ball.y + model.ball.radius >= GameConfig.FLOOR_Y) {
            finishRally();
        }
    }

    private void finishRally() {
        // 原先結束來回時的得分流程，改用 handlePoint 以便重用
        boolean redWins = ScoringLogic.determineWinner(
                model.ball.x,
                model.getLastHitTeam(),
                model.getServeHandler().isRedServing()
        );
        handlePoint(redWins);
    }

    // 公開 API：直接給點（故障、四連擊等）
    public void awardPoint(boolean redWins) {
        handlePoint(redWins);
    }

    private void handlePoint(boolean redWins) {
        if (rallyOver) return; // already ended
        rallyOver = true;
        deadBallTimer = DEAD_BALL_FRAMES;

        if (redWins) {
            model.redScore++;
            model.getServeHandler().setRedServing(true);
        } else {
            model.blueScore++;
            model.getServeHandler().setRedServing(false);
        }

        // 檢查比賽勝利（25 分制，需領先 2 分）
        if ((model.redScore >= 25 || model.blueScore >= 25) && Math.abs(model.redScore - model.blueScore) >= 2) {
            model.matchOver = true;
            model.matchWinnerRed = model.redScore > model.blueScore;
        }
    }

    private void prepareNextServe() {
        rallyOver = false;
        model.getServeHandler().setWaitingForServe(true);
        model.resetCounters();
        model.setLastHitTeam(null);
    }
}
