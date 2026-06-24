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

    public int getDeadBallTimer() {
        return deadBallTimer;
    }

    public void applyNetworkState(boolean rallyOver, int deadBallTimer) {
        this.rallyOver = rallyOver;
        this.deadBallTimer = Math.max(0, deadBallTimer);
    }

    public void updateDeadBall(TeamInput redInput, TeamInput blueInput) {
        deadBallTimer--;
        // 更新球與特效：在 dead-ball 階段也要更新球的位置與扣球軌跡，
        // 以便像是後排違規直接給點時仍能顯示完整軌跡直到落地。
        model.ball.update();
        model.effects.update();

        // 若扣球軌跡仍在，額外在 dead-ball 階段加入軌跡點
        if (model.spikeEffect.isSpikeTrailActive()) {
            model.spikeEffect.addTrailPoint(model.ball.x, model.ball.y);
        }

        model.spikeEffect.update();
        model.redTeam.update(redInput);
        model.blueTeam.update(blueInput);

        // 若球在 dead-ball 階段落地，產生落地煙霧並停止軌跡（不再次改變得分流程）
        if (model.ball.y + model.ball.radius >= GameConfig.FLOOR_Y) {
            if (model.spikeEffect.isSpikeTrailActive()) {
                model.spikeEffect.spawnSmoke(model.ball.x, GameConfig.FLOOR_Y);
                model.spikeEffect.stopSpikeTrail();
            }
        }

        if (deadBallTimer <= 0) {
            prepareNextServe();
        }
    }

    public void checkBallLanding() {
        if (!rallyOver && model.ball.y + model.ball.radius >= GameConfig.FLOOR_Y) {
            if (model.spikeEffect.isSpikeTrailActive()) {
                model.spikeEffect.spawnSmoke(model.ball.x, GameConfig.FLOOR_Y);
                model.spikeEffect.stopSpikeTrail();
            }
            finishRally();
        }
    }

    private void finishRally() {
        // 若之前標記為 pending touch out，等落地後再決定是否為 TOUCH OUT
        if (model.pendingTouchOut) {
            boolean isInNow = model.ball.x >= GameConfig.COURT_LEFT_X && model.ball.x <= GameConfig.COURT_RIGHT_X;
            Boolean winner = model.pendingTouchOutWinner;
            // 先清除 pending
            model.pendingTouchOut = false;
            model.pendingTouchOutWinner = null;

            if (!isInNow) {
                // 確認為 TOUCH OUT（落地仍在界外）
                model.transientMessage = "TOUCH OUT";
                model.transientMessageTimer = 42; // 0.7s
                model.transientMessageIsRed = winner;
                handlePoint(winner != null && winner);
                return;
            }
            // 若實際落地為 IN，則繼續正常判定
        }

        // 原先結束來回時的得分流程，改用 handlePoint 以便重用
        boolean isIn = model.ball.x >= GameConfig.COURT_LEFT_X && model.ball.x <= GameConfig.COURT_RIGHT_X;
        boolean redWins = ScoringLogic.determineWinner(
                model.ball.x,
                model.getLastHitTeam(),
                model.getServeHandler().isRedServing()
        );
        // 顯示得分方式 IN / OUT，並且以得分隊配色顯示
        model.transientMessage = isIn ? "IN" : "OUT";
        model.transientMessageTimer = 42; // 0.7s
        model.transientMessageIsRed = redWins;

        handlePoint(redWins);
    }

    // 公開 API：直接給點（故障、四連擊等）
    public void awardPoint(boolean redWins) {
        handlePoint(redWins);
    }

    // 公開 API：給點並顯示中央暫時訊息（例如 IN/OUT/四連擊）
    public void awardPointWithMessage(boolean redWins, String message) {
        // 設置顯示文字與顏色（由 model 存放，由 MatchDisplay 繪製）
        model.transientMessage = message;
        model.transientMessageTimer = 42; // 0.7s
        model.transientMessageIsRed = redWins;
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
            // 設定延遲倒數（1.5 秒）在 stop 更新前繼續畫面/動畫
            model.matchOverCountdownFrames = GameConfig.MATCH_OVER_DELAY_FRAMES;
        }
    }

    private void prepareNextServe() {
        rallyOver = false;
        model.getServeHandler().setWaitingForServe(true);
        model.resetCounters();
        model.setLastHitTeam(null);
    }
}
