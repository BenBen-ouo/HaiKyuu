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
    private boolean ballHitNetThisFrame;

    // Client 本地預測時不可自行裁決得分或下一次發球位置。
    private boolean resolvingRallyOutcomes = true;
    private boolean predictionAwaitingAuthority;

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
        predictionAwaitingAuthority = false;
    }

    // 查詢本回合是否該隊已由舉球員接觸過
    public boolean hasSetterTouched(boolean redSide) {
        return rallyState.hasSetterTouched(redSide);
    }

    public void resetTeamContacts(boolean redSide) {
        rallyState.resetHitCount(redSide);
        syncPublicHitCounters();
    }

    // 由外部呼叫：給分並顯示訊息（轉送至 RallyScorer）
    public void awardPointWithMessage(boolean redWins, String message) {
        if (resolvingRallyOutcomes) {
            scorer.awardPointWithMessage(redWins, message);
        } else {
            awaitAuthoritativeRallyResult();
        }
    }

    public void update(TeamInput redInput, TeamInput blueInput) {
        updateFrame(redInput, blueInput, true);
    }

    /**
     * Client 專用的本地預測更新。
     * 角色與球仍可預測，但得分、違規結束與下一次發球準備只接受 Server 快照。
     */
    public void updateForNetworkPrediction(TeamInput redInput, TeamInput blueInput) {
        updateFrame(redInput, blueInput, false);
    }

    private void updateFrame(TeamInput redInput, TeamInput blueInput, boolean resolveRallyOutcomes) {
        ballHitNetThisFrame = false;
        resolvingRallyOutcomes = resolveRallyOutcomes;

        try {
            // 當比賽結束且延遲倒數結束時，停止遊戲更新（仍由 controller 捕捉重開鍵）
            if (matchOver && matchOverCountdownFrames <= 0) {
                return;
            }

            // Client 已預測到回合結束時，等待 Server 的 SCORE 快照，不自行加分或準備下一次發球。
            if (!resolveRallyOutcomes && (predictionAwaitingAuthority || scorer.isRallyOver())) {
                return;
            }

            BallSideTracker.updateInputs(ball, redInput, blueInput);

            if (scorer.isRallyOver()) {
                scorer.updateDeadBall(redInput, blueInput);
                return;
            }

            updateActiveFrame(redInput, blueInput, resolveRallyOutcomes);

            // 若處於 matchOver 的顯示倒數中，遞減計時器
            if (matchOver && matchOverCountdownFrames > 0) {
                matchOverCountdownFrames--;
            }
        } finally {
            resolvingRallyOutcomes = true;
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

        // 四連擊只能由 Server 最終裁決；Client 預測到時先停止本地回合演算。
        if (rallyState.getHitCount(redSide) > 3) {
            if (resolvingRallyOutcomes) {
                scorer.awardPointWithMessage(!redSide, "四觸違規");
            } else {
                awaitAuthoritativeRallyResult();
            }
        }
    }

    private void updateActiveFrame(TeamInput redInput, TeamInput blueInput, boolean resolveRallyOutcomes) {
        lastBallX = ball.x;

        serveHandler.updateBeforeTeams(redInput, blueInput);
        configureBackActions(redInput, blueInput);
        updateTeams(redInput, blueInput);
        serveHandler.updateAfterTeams();

        updateBallIfNeeded(resolveRallyOutcomes);
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

    private void updateBallIfNeeded(boolean resolveRallyOutcomes) {
        if (!serveHandler.shouldUpdateBall()) {
            return;
        }

        ball.update();
        spikeEffect.addTrailPoint(ball.x, ball.y);
        ballHitNetThisFrame = ball.collideWithNet(netHitBox);

        serveHandler.updateAfterBall();
        if (resolveRallyOutcomes) {
            scorer.checkBallLanding();
        } else if (ball.y + ball.radius >= GameConfig.FLOOR_Y) {
            // 不在 Client 顯示本地得分／違規結果；等待 Server 的 SCORE 快照。
            ball.vx = 0;
            ball.vy = 0;
            awaitAuthoritativeRallyResult();
        }

        if (!scorer.isRallyOver() && !predictionAwaitingAuthority) {
            resetCountersIfBallCrossesNet();
        }
    }

    private void collideTeamsIfAllowed(TeamInput redInput, TeamInput blueInput) {
        if (scorer.isRallyOver() || predictionAwaitingAuthority) {
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

    /* 以下入口只供網路快照與事件判定使用。 */
    public boolean didBallHitNetThisFrame() {
        return ballHitNetThisFrame;
    }

    public boolean isRallyOverForNetwork() {
        return scorer.isRallyOver();
    }

    public int getDeadBallTimerForNetwork() {
        return scorer.getDeadBallTimer();
    }

    public int getLastHitterIndexForNetwork(boolean redSide) {
        return rallyState.getLastHitterIndex(redSide, redSide ? redTeam : blueTeam);
    }

    public boolean wasLastTouchBlockForNetwork() {
        return rallyState.wasLastTouchBlock();
    }

    public void applyNetworkRallyState(
            int redHitCount,
            int blueHitCount,
            Boolean lastHitTeam,
            boolean lastTouchWasBlock,
            int redLastHitterIndex,
            int blueLastHitterIndex,
            boolean rallyOver,
            int deadBallTimer
    ) {
        rallyState.applyNetworkState(
                redHitCount,
                blueHitCount,
                lastHitTeam,
                lastTouchWasBlock,
                redLastHitterIndex,
                blueLastHitterIndex,
                redTeam,
                blueTeam
        );
        syncPublicHitCounters();
        scorer.applyNetworkState(rallyOver, deadBallTimer);
    }

    public boolean isResolvingRallyOutcomes() {
        return resolvingRallyOutcomes;
    }

    public void awaitAuthoritativeRallyResult() {
        if (!resolvingRallyOutcomes) {
            predictionAwaitingAuthority = true;
        }
    }

    /* 每次 Server 完整快照套用後，允許 Client 從最新權威狀態繼續預測。 */
    public void resumeNetworkPrediction() {
        predictionAwaitingAuthority = false;
    }

    // 外部呼叫：直接給點（例如四連擊、後排三米線違規）
    public void awardPoint(boolean redWins) {
        if (resolvingRallyOutcomes) {
            scorer.awardPoint(redWins);
        } else {
            awaitAuthoritativeRallyResult();
        }
    }
}