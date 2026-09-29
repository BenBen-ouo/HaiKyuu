/*
處理球落地後的得分、死球等待時間與下一球發球方準備。
目前依照落點、最後觸球隊伍與發球方決定得分隊伍。
*/
package model.rally;

import model.GameConfig;
import model.GameModel;
import model.TeamInput;

public class RallyScorer {
    private static final int MOVABLE_SCORE_FRAMES = 60;
    private static final int LOCKED_SCORE_FRAMES = 30;
    private static final int SCORE_FRAMES = MOVABLE_SCORE_FRAMES + LOCKED_SCORE_FRAMES;

    private final GameModel model;
    private boolean rallyOver = false;
    private int deadBallTimer = 0;

    public RallyScorer(GameModel model) {
        this.model = model;
    }

    public boolean isRallyOver() {
        return rallyOver;
    }

    public boolean isLockedPhase() {
        return rallyOver && !model.matchOver && deadBallTimer > 0
                && deadBallTimer <= LOCKED_SCORE_FRAMES;
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
        if (deadBallTimer > LOCKED_SCORE_FRAMES) {
            // 得分後前 60 幀保留角色操作與落地特效，但不再判定觸球或得分。
            updatePostPointMotion(redInput, blueInput);

            deadBallTimer--;
            if (deadBallTimer == LOCKED_SCORE_FRAMES) {
                if (model.getServeHandler().isRedServing()) {
                    model.redTeam.resetPlayersExceptBack();
                    model.blueTeam.resetAllPlayers();
                    model.redTeam.backPlayer.prepareForServe();
                } else {
                    model.redTeam.resetAllPlayers();
                    model.blueTeam.resetPlayersExceptBack();
                    model.blueTeam.backPlayer.prepareForServe();
                }
                model.getServeHandler().positionServerForServe();
                model.ball.vx = 0;
                model.ball.vy = 0;
                model.effects.clear();
                model.spikeEffect.clear();
                model.observeLockedActions(redInput, blueInput);
            }
        } else {
            // 後 30 幀只保留得分原因；球與特效已在階段切換時移除。
            model.observeLockedActions(redInput, blueInput);
            deadBallTimer--;
        }

        model.updateTransientMessage();
        if (deadBallTimer <= 0) {
            prepareNextServe();
        }
    }

    /** 賽末得分後沿用相同物理與操作，直到重新開始；不進入下一球。 */
    public void updateFinishedMatch(TeamInput redInput, TeamInput blueInput) {
        updatePostPointMotion(redInput, blueInput);
    }

    private void updatePostPointMotion(TeamInput redInput, TeamInput blueInput) {
        model.updatePostPointBall();
        model.effects.update();
        if (model.spikeEffect.isSpikeTrailActive()) {
            model.spikeEffect.addTrailPoint(model.ball.x, model.ball.y);
        }
        model.spikeEffect.update();
        model.updateDeadBallPlayers(redInput, blueInput);

        if (model.ball.y + model.ball.radius >= GameConfig.FLOOR_Y
                && model.spikeEffect.isSpikeTrailActive()) {
            model.spikeEffect.spawnSmoke(model.ball.x, GameConfig.FLOOR_Y);
            model.spikeEffect.stopSpikeTrail();
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
        deadBallTimer = SCORE_FRAMES;

        if (model.isPracticeMode()) {
            // 練習模式仍裁決回合，但不計分；每球都由藍隊發球。
            model.getServeHandler().setRedServing(false);
        } else {
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
                deadBallTimer = 0;
            }
        }

        if (!model.matchOver && model.transientMessage != null) {
            model.transientMessageTimer = SCORE_FRAMES;
        }
    }

    private void prepareNextServe() {
        rallyOver = false;
        model.getServeHandler().setWaitingForServe(true);
        model.resetCounters();
        model.resetServeReception();
        model.setLastHitTeam(null);
    }
}
