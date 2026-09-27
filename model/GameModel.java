/*
遊戲模型主入口，負責每幀更新流程的總調度。
發球、球員更新、球更新、碰撞、得分與特效更新都由這裡依序呼叫。
*/
package model;

import model.ball.Ball;
import model.ball.BallSideTracker;
import model.ball.NetHitBox;
import model.effect.EffectManager;
import model.effect.SpikeEffect;
import model.player.BackActionResolver;
import model.player.Player;
import model.player.Setter;
import model.player.Team;
import model.rally.RallyContactHandler;
import model.rally.RallyScorer;
import model.rally.RallyState;
import model.serve.ServeHandler;

public class GameModel {
    private final boolean practiceMode;

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
    private boolean ballLandedThisFrame;
    private boolean setterContactThisFrame;
    private boolean firstServeReceptionThisFrame;
    private boolean serveReceptionComplete;
    private final ActionReleaseGate redActionReleaseGate = new ActionReleaseGate();
    private final ActionReleaseGate blueActionReleaseGate = new ActionReleaseGate();

    // Client 本地預測時不可自行裁決得分或下一次發球位置。
    private boolean resolvingRallyOutcomes = true;
    private boolean predictionAwaitingAuthority;
    private boolean networkSmokeShownOnFloor;

    // 短暫訊息（例如違規提示），每幀遞減
    public String transientMessage = null;
    public int transientMessageTimer = 0;
    // 若非 null，代表暫時訊息要以該隊顏色顯示：true=紅隊, false=藍隊, null=無顏色
    public Boolean transientMessageIsRed = null;

    // 預期的攔網造成 out（等待落地再顯示與給分）
    public boolean pendingTouchOut = false;
    public Boolean pendingTouchOutWinner = null;

    public GameModel() {
        this(false);
    }

    public GameModel(boolean practiceMode) {
        this.practiceMode = practiceMode;
        if (practiceMode) {
            serveHandler.setRedServing(false);
        }
        serveHandler.setWaitingForServe(true);
    }

    public boolean isPracticeMode() {
        return practiceMode;
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
        networkSmokeShownOnFloor = false;
        serveReceptionComplete = false;
        redActionReleaseGate.reset();
        blueActionReleaseGate.reset();
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

    /** Client 只預測本機球員；球、對手與一切碰撞／裁決都等 Server。 */
    public void updateForNetworkPrediction(TeamInput localInput, boolean localRedSide) {
        TeamInput input = localInput.copy();
        ActionReleaseGate releaseGate = localRedSide ? redActionReleaseGate : blueActionReleaseGate;
        if (!matchOver && scorer.isLockedPhase()) {
            releaseGate.observeLocked(input);
            return;
        }

        releaseGate.filter(input);
        if (scorer.isRallyOver() || matchOver || serveHandler.shouldUseGameBackPlayerAction(localRedSide)) {
            BackActionResolver.apply(input, getHitCount(localRedSide));
        }
        serveHandler.filterNetworkPredictionInput(input, localRedSide, localInput.servePressed);
        input.hasFirstRegularTouch = getHitCount(localRedSide) > 0;
        (localRedSide ? redTeam : blueTeam).update(input);
        effects.update();
        if (spikeEffect.isSpikeTrailActive()) {
            spikeEffect.addTrailPoint(ball.x, ball.y);
        }
        spikeEffect.update();
    }

    /** 快照只同步視覺特效的開關，不讓 Client 自行判定扣球或落地。 */
    public void syncNetworkVisualEffects(boolean trailActive, boolean trailRedSide) {
        boolean wasActive = spikeEffect.isSpikeTrailActive();
        boolean onFloor = ball.y + ball.radius >= GameConfig.FLOOR_Y;
        if (!onFloor) {
            networkSmokeShownOnFloor = false;
        }
        if (trailActive) {
            if (!wasActive || spikeEffect.getCurrentSpikeIsRed() != trailRedSide) {
                spikeEffect.startSpikeTrail(trailRedSide);
            }
        } else if (wasActive) {
            if (onFloor && !networkSmokeShownOnFloor) {
                spikeEffect.spawnSmoke(ball.x, GameConfig.FLOOR_Y);
                networkSmokeShownOnFloor = true;
            }
            spikeEffect.stopSpikeTrail();
        }
    }

    private void updateFrame(TeamInput redInput, TeamInput blueInput, boolean resolveRallyOutcomes) {
        ballHitNetThisFrame = false;
        ballLandedThisFrame = false;
        setterContactThisFrame = false;
        firstServeReceptionThisFrame = false;
        resolvingRallyOutcomes = resolveRallyOutcomes;

        try {
            // 賽末只繼續物理、特效與雙方操作，不再碰撞、計分或準備發球。
            if (matchOver) {
                scorer.updateFinishedMatch(redInput, blueInput);
                return;
            }

            // Client 收到 Server 的得分狀態後，前 60 幀仍可操作角色；
            // 後 30 幀鎖住操作。階段切換與下一球準備一律由 Server 快照決定。
            if (!resolveRallyOutcomes && (predictionAwaitingAuthority || scorer.isRallyOver())) {
                if (scorer.isLockedPhase()) {
                    observeLockedActions(redInput, blueInput);
                } else if (scorer.isRallyOver()) {
                    updateDeadBallPlayers(redInput, blueInput);
                }
                updateNetworkWaitingFrame();
                return;
            }

            BallSideTracker.updateInputs(ball, redInput, blueInput);

            if (scorer.isRallyOver()) {
                scorer.updateDeadBall(redInput, blueInput);
                return;
            }

            updateActiveFrame(redInput, blueInput, resolveRallyOutcomes);

        } finally {
            resolvingRallyOutcomes = true;
        }
    }

    public void resetCounters() {
        rallyState.resetCounters();
        syncPublicHitCounters();
    }

    public int getHitCount(boolean redSide) {
        return rallyState.getHitCount(redSide);
    }

    public Player getLastHitter(boolean redSide) {
        return rallyState.getLastHitter(redSide);
    }

    public void recordHit(boolean redSide, Player hitter) {
        recordContact(redSide, hitter, true);
    }

    public void recordRegularHit(boolean redSide, Player hitter) {
        if (matchOver) return;

        if (!serveReceptionComplete && redSide != serveHandler.isRedServing()) {
            serveReceptionComplete = true;
            firstServeReceptionThisFrame = true;
        }

        recordContact(redSide, hitter, serveReceptionComplete);
    }

    private void recordContact(boolean redSide, Player hitter, boolean counts) {
        if (matchOver) return;

        rallyState.recordHit(redSide, hitter, counts);
        if (hitter instanceof Setter) {
            setterContactThisFrame = true;
        }
        syncPublicHitCounters();

        // 四連擊只能由 Server 最終裁決；Client 預測到時先停止本地回合演算。
        if (counts && rallyState.getHitCount(redSide) > 3) {
            if (resolvingRallyOutcomes) {
                scorer.awardPointWithMessage(!redSide, "四觸違規");
            } else {
                awaitAuthoritativeRallyResult();
            }
        }
    }

    public boolean isServeReceptionComplete() {
        return serveReceptionComplete;
    }

    public void resetServeReception() {
        serveReceptionComplete = false;
    }

    public void recordBlock(boolean redSide, Player blocker) {
        if (matchOver) return;

        rallyState.recordBlock(redSide, blocker);
    }

    private void updateActiveFrame(TeamInput redInput, TeamInput blueInput, boolean resolveRallyOutcomes) {
        lastBallX = ball.x;

        redActionReleaseGate.filter(redInput);
        blueActionReleaseGate.filter(blueInput);
        serveHandler.updateBeforeTeams(redInput, blueInput);
        configureBackActions(redInput, blueInput);
        redInput.hasFirstRegularTouch = redHitCount > 0;
        blueInput.hasFirstRegularTouch = blueHitCount > 0;
        updateTeams(redInput, blueInput);
        serveHandler.updateAfterTeams();

        updateBallIfNeeded(resolveRallyOutcomes);
        collideTeamsIfAllowed(redInput, blueInput);

        serveHandler.finishFrame();
        effects.update();
        spikeEffect.update();

        if (!scorer.isRallyOver()) {
            updateTransientMessage();
        }
    }

    public void updateDeadBallPlayers(TeamInput redInput, TeamInput blueInput) {
        BackActionResolver.apply(redInput, redHitCount);
        BackActionResolver.apply(blueInput, blueHitCount);
        redInput.hasFirstRegularTouch = redHitCount > 0;
        blueInput.hasFirstRegularTouch = blueHitCount > 0;
        updateTeams(redInput, blueInput);
    }

    /** 得分後球仍在移動時保留撞網；不重新判定觸球、落地得分或球權。 */
    public void updatePostPointBall() {
        ball.update();
        ballHitNetThisFrame = ball.collideWithNet(netHitBox);
    }

    public void observeLockedActions(TeamInput redInput, TeamInput blueInput) {
        redActionReleaseGate.observeLocked(redInput);
        blueActionReleaseGate.observeLocked(blueInput);
    }

    /**
     * Client 等待 Server 的 SCORE 快照或下一次發球準備快照時使用。
     * 不做碰撞、得分或階段切換；得分後前 60 幀沿用本地物理與特效。
     */
    private void updateNetworkWaitingFrame() {
        if (!scorer.isLockedPhase()) {
            if (scorer.isRallyOver()) {
                updatePostPointBall();
                if (spikeEffect.isSpikeTrailActive()) {
                    spikeEffect.addTrailPoint(ball.x, ball.y);
                }
                if (ball.y + ball.radius >= GameConfig.FLOOR_Y
                        && spikeEffect.isSpikeTrailActive()) {
                    spikeEffect.spawnSmoke(ball.x, GameConfig.FLOOR_Y);
                    spikeEffect.stopSpikeTrail();
                }
            } else {
                redTeam.updateWhileAwaitingAuthority();
                blueTeam.updateWhileAwaitingAuthority();
            }
            effects.update();
            spikeEffect.update();
        }
    }

    public void updateTransientMessage() {
        if (transientMessageTimer <= 0) {
            return;
        }

        transientMessageTimer--;
        if (transientMessageTimer == 0) {
            transientMessage = null;
            transientMessageIsRed = null;
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
        ballLandedThisFrame = ball.y + ball.radius >= GameConfig.FLOOR_Y;
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

        if (scorer.isRallyOver() || predictionAwaitingAuthority) {
            return;
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

    /** Server 用：本 tick 球是否首次到達地板高度。 */
    public boolean didBallLandThisFrame() {
        return ballLandedThisFrame;
    }

    /** Server 用：本 tick 是否由 Setter 完成一般觸球。 */
    public boolean didSetterContactThisFrame() {
        return setterContactThisFrame;
    }

    public boolean didFirstServeReceptionThisFrame() {
        return firstServeReceptionThisFrame;
    }

    public boolean isLockedScorePhase() {
        return scorer.isLockedPhase();
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
            boolean serveReceptionComplete,
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
        this.serveReceptionComplete = serveReceptionComplete;
        scorer.applyNetworkState(rallyOver, deadBallTimer);
        if (scorer.isLockedPhase()) {
            effects.clear();
            spikeEffect.clear();
        }
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
