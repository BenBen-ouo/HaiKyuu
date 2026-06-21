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
        model.spikeEffect.update();
        model.redTeam.update(redInput);
        model.blueTeam.update(blueInput);

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
        rallyOver = true;
        deadBallTimer = DEAD_BALL_FRAMES;

        boolean redWins = ScoringLogic.determineWinner(
                model.ball.x,
                model.getLastHitTeam(),
                model.getServeHandler().isRedServing()
        );

        if (redWins) {
            model.redScore++;
            model.getServeHandler().setRedServing(true);
        } else {
            model.blueScore++;
            model.getServeHandler().setRedServing(false);
        }
    }

    private void prepareNextServe() {
        rallyOver = false;
        model.getServeHandler().setWaitingForServe(true);
        model.resetCounters();
        model.setLastHitTeam(null);
    }
}
