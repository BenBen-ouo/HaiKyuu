/*
遊戲模型主入口，負責每幀更新流程的總調度。
發球、球員更新、球更新、碰撞、得分與特效更新都由這裡依序呼叫。
*/
package model;

public class GameModel {
    public Ball ball = new Ball(GameConfig.SCREEN_WIDTH / 2.0, 130);
    public final NetHitBox netHitBox = new NetHitBox();

    public Team redTeam = new Team(true);
    public Team blueTeam = new Team(false);

    public int redScore = 0;
    public int blueScore = 0;

    // 保留 public 欄位，避免舊程式碼需要改。
    public int redHitCount = 0;
    public int blueHitCount = 0;

    public final EffectManager effects = new EffectManager();
    public final SpikeEffect spikeEffect = new SpikeEffect();

    // 比賽狀態
    public boolean matchOver = false;
    public Boolean matchWinnerRed = null;

    private final RallyState rallyState = new RallyState();
    private final ServeHandler serveHandler = new ServeHandler(this);
    private final RallyScorer scorer = new RallyScorer(this);
    private final RallyContactHandler contactHandler = new RallyContactHandler(this);

    private double lastBallX;

    // 短暫訊息（例如違規提示），每幀遞減
    public String transientMessage = null;
    public int transientMessageTimer = 0;
    // 若非 null，代表暫時訊息要以該隊顏色顯示：true=紅隊, false=藍隊, null=無顏色
    public Boolean transientMessageIsRed = null;

    // 預期的攔網造成 out（等待落地再顯示與給分）
    public boolean pendingTouchOut = false;
    public Boolean pendingTouchOutWinner = null;

    // matchOver 決定後，延遲幾幀才停止遊戲更新（用於顯示勝利動畫/效果）
    public int matchOverCountdownFrames = 0;

    public GameModel() {
        serveHandler.setWaitingForServe(true);
    }

    public ServeHandler getServeHandler() {
        return serveHandler;
    }

    public Boolean getLastHitTeam() {
        return rallyState.getLastHitTeam();
    }

    public void setLastHitTeam(Boolean team) {
        rallyState.setLastHitTeam(team);
    }

    public void restart() {
        GameResetter.reset(this);
        scorer.reset();
        rallyState.resetAll();
        syncPublicHitCounters();
        effects.clear();
        spikeEffect.clear();
    }

    // 查詢本回合是否該隊已由舉球員接觸過
    public boolean hasSetterTouched(boolean redSide) {
        return rallyState.hasSetterTouched(redSide);
    }

    // 由外部呼叫：給分並顯示訊息（轉送至 RallyScorer）
    public void awardPointWithMessage(boolean redWins, String message) {
        // Delegate to RallyScorer via scorer (private) -- add public wrapper
        scorer.awardPointWithMessage(redWins, message);
    }

    public void update(TeamInput redInput, TeamInput blueInput) {
        // 當比賽結束且延遲倒數結束時，停止遊戲更新（仍由 controller 捕捉重開鍵）
        if (matchOver && matchOverCountdownFrames <= 0) return;

        BallSideTracker.updateInputs(ball, redInput, blueInput);

        if (scorer.isRallyOver()) {
            scorer.updateDeadBall(redInput, blueInput);
            return;
        }

        updateActiveFrame(redInput, blueInput);

        // 若處於 matchOver 的顯示倒數中，遞減計時器
        if (matchOver && matchOverCountdownFrames > 0) {
            matchOverCountdownFrames--;
        }
    }

    public void resetCounters() {
        rallyState.resetCounters();
        syncPublicHitCounters();
    }

    int getHitCount(boolean redSide) {
        return rallyState.getHitCount(redSide);
    }

    Player getLastHitter(boolean redSide) {
        return rallyState.getLastHitter(redSide);
    }

    void recordHit(boolean redSide, Player hitter) {
        // 若比賽結束，忽略後續擊球
        if (matchOver) return;

        rallyState.recordHit(redSide, hitter);
        syncPublicHitCounters();

        // 四連擊判定：若本隊擊球數超過 3，則對方得分
        if (rallyState.getHitCount(redSide) > 3) {
            // 使用 RallyScorer 的 API 顯示訊息並給分（保持 IN/OUT 顯示邏輯在同一檔案）
            scorer.awardPointWithMessage(!redSide, "四觸違規");
        }
    }

    private void updateActiveFrame(TeamInput redInput, TeamInput blueInput) {
        lastBallX = ball.x;

        serveHandler.updateBeforeTeams(redInput, blueInput);
        configureBackActions(redInput, blueInput);
        updateTeams(redInput, blueInput);
        serveHandler.updateAfterTeams();

        updateBallIfNeeded();
        collideTeamsIfAllowed(redInput, blueInput);

        serveHandler.finishFrame();
        effects.update();
        spikeEffect.update();

        // 遞減暫時訊息計時器
        if (transientMessageTimer > 0) {
            transientMessageTimer--;
            if (transientMessageTimer == 0) {
                transientMessage = null;
                transientMessageIsRed = null;
            }
        }
    }

    private void configureBackActions(TeamInput redInput, TeamInput blueInput) {
        if (serveHandler.shouldUseGameBackPlayerAction(true)) {
            BackActionResolver.apply(redInput, redHitCount);
        }

        if (serveHandler.shouldUseGameBackPlayerAction(false)) {
            BackActionResolver.apply(blueInput, blueHitCount);
        }
    }

    private void updateTeams(TeamInput redInput, TeamInput blueInput) {
        redTeam.update(redInput);
        blueTeam.update(blueInput);
    }

    private void updateBallIfNeeded() {
        if (!serveHandler.shouldUpdateBall()) {
            return;
        }

        ball.update();
        spikeEffect.addTrailPoint(ball.x, ball.y);
        ball.collideWithNet(netHitBox);

        serveHandler.updateAfterBall();
        scorer.checkBallLanding();

        if (!scorer.isRallyOver()) {
            resetCountersIfBallCrossesNet();
        }
    }

    private void collideTeamsIfAllowed(TeamInput redInput, TeamInput blueInput) {
        if (scorer.isRallyOver()) {
            return;
        }

        if (serveHandler.canTeamCollideWithBall(true)) {
            contactHandler.collideTeam(redTeam, true, redInput);
        }

        if (serveHandler.canTeamCollideWithBall(false)) {
            contactHandler.collideTeam(blueTeam, false, blueInput);
        }
    }

    private void resetCountersIfBallCrossesNet() {
        boolean crossedNet = (lastBallX < GameConfig.NET_X && ball.x >= GameConfig.NET_X)
                || (lastBallX > GameConfig.NET_X && ball.x <= GameConfig.NET_X);

        if (crossedNet) {
            resetCounters();
        }
    }

    private void syncPublicHitCounters() {
        redHitCount = rallyState.getHitCount(true);
        blueHitCount = rallyState.getHitCount(false);
    }

    // 外部呼叫：直接給點（例如四連擊、後排三米線違規）
    public void awardPoint(boolean redWins) {
        scorer.awardPoint(redWins);
    }
}