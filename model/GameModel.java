/*
遊戲模型主入口，負責每幀更新流程的總調度。
發球、球員更新、球更新、碰撞、得分與特效更新都由這裡依序呼叫。
*/
package model;

import model.ball.Ball;
import model.ball.NetHitBox;
import model.effect.SpikeEffect;
import model.effect.BlockTouchEffect;
import model.player.BackActionResolver;
import model.player.Player;
import model.player.Setter;
import model.player.Team;
import model.rally.RallyContactHandler;
import model.rally.RallyScorer;
import model.rally.RallyState;
import model.rally.RallyContactSnapshot;
import model.serve.ServeHandler;

public final class GameModel {
    private static final double INITIAL_BALL_CENTER_Y = 130;

    public Ball ball = new Ball(GameConfig.SCREEN_WIDTH / 2.0, INITIAL_BALL_CENTER_Y);
    public final NetHitBox netHitBox = new NetHitBox();

    public Team redTeam = new Team(true);
    public Team blueTeam = new Team(false);

    public int redScore = 0;
    public int blueScore = 0;

    public final SpikeEffect spikeEffect = new SpikeEffect();
    public final BlockTouchEffect blockTouchEffect = new BlockTouchEffect();

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
    private boolean airSetContactThisFrame;
    private boolean firstServeReceptionThisFrame;
    private boolean ballContactThisFrame;
    private boolean serveReceptionComplete;
    private final ActionReleaseGate redActionReleaseGate = new ActionReleaseGate();
    private final ActionReleaseGate blueActionReleaseGate = new ActionReleaseGate();
    private final TeamInput redFrameInput = new TeamInput();
    private final TeamInput blueFrameInput = new TeamInput();

    public boolean redAttackLocked;
    public boolean blueAttackLocked;
    public int netFlashFrames;
    public boolean landingSmokeThisFrame;

    // 短暫訊息（例如違規提示），每幀遞減
    public String transientMessage = null;
    public int transientMessageTimer = 0;
    // 若非 null，代表暫時訊息要以該隊顏色顯示：true=紅隊, false=藍隊, null=無顏色
    public Boolean transientMessageIsRed = null;

    // 預期的攔網造成 out（等待落地再顯示與給分）
    public boolean pendingTouchOut = false;
    public Boolean pendingTouchOutWinner = null;

    public GameModel() {
        GameConfig.validate();
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
        redScore = 0;
        blueScore = 0;
        matchOver = false;
        matchWinnerRed = null;
        ball = new Ball(GameConfig.SCREEN_WIDTH / 2.0, INITIAL_BALL_CENTER_Y);
        redTeam = new Team(true);
        blueTeam = new Team(false);
        serveHandler.reset();
        transientMessage = null;
        transientMessageTimer = 0;
        transientMessageIsRed = null;
        pendingTouchOut = false;
        pendingTouchOutWinner = null;
        scorer.reset();
        rallyState.resetAll();
        spikeEffect.clear();
        blockTouchEffect.clear();
        redAttackLocked = false;
        blueAttackLocked = false;
        netFlashFrames = 0;
        landingSmokeThisFrame = false;
        serveReceptionComplete = false;
        redActionReleaseGate.reset();
        blueActionReleaseGate.reset();
        lastBallX = ball.x;
        ballHitNetThisFrame = false;
        ballLandedThisFrame = false;
        setterContactThisFrame = false;
        airSetContactThisFrame = false;
        firstServeReceptionThisFrame = false;
        ballContactThisFrame = false;
    }

    // 查詢本回合是否該隊已由舉球員接觸過
    public boolean hasSetterTouched(boolean redSide) {
        return rallyState.hasSetterTouched(redSide);
    }

    public boolean wasSetterTouchedFirst(boolean redSide) {
        return rallyState.wasSetterTouchedFirst(redSide);
    }

    public boolean canSetterTouch(boolean redSide) {
        return rallyState.canSetterTouch(redSide);
    }

    public boolean hasBlocked(boolean redSide) {
        return rallyState.hasBlocked(redSide);
    }

    public void resetTeamContacts(boolean redSide) {
        rallyState.resetHitCount(redSide);
    }

    // 由外部呼叫：給分並顯示訊息（轉送至 RallyScorer）
    public void awardPointWithMessage(boolean redWins, String message) {
        scorer.awardPointWithMessage(redWins, message);
    }

    public void update(ControlInput redInput, ControlInput blueInput) {
        redFrameInput.resetFrom(redInput);
        blueFrameInput.resetFrom(blueInput);
        updateFrame(redFrameInput, blueFrameInput);
    }

    /** Client 只預測本機球員；球、對手與一切碰撞／裁決都等 Server。 */
    public void updateForNetworkPrediction(ControlInput localInput, boolean localRedSide) {
        blockTouchEffect.beginFrame();
        if (netFlashFrames > 0) netFlashFrames--;
        if (matchOver) updateTransientMessage();
        TeamInput input = localRedSide ? redFrameInput : blueFrameInput;
        input.resetFrom(localInput);
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
        input.canResolveAttack = false;
        input.hasFirstRegularTouch = getHitCount(localRedSide) > 0;
        input.opponentHasFirstRegularTouch = getHitCount(!localRedSide) > 0;
        configureAttackInput(input, localRedSide ? redTeam : blueTeam, localRedSide);
        (localRedSide ? redTeam : blueTeam).update(input);
        if (spikeEffect.isSpikeTrailActive()) {
            spikeEffect.addTrailPoint(ball.x, ball.y);
        }
        spikeEffect.update();
    }

    /** 快照只同步視覺特效的開關，不讓 Client 自行判定扣球或落地。 */
    public void syncNetworkVisualEffects(boolean trailActive, boolean trailRedSide) {
        boolean wasActive = spikeEffect.isSpikeTrailActive();
        if (trailActive) {
            if (!wasActive || spikeEffect.getCurrentSpikeIsRed() != trailRedSide) {
                spikeEffect.startSpikeTrail(trailRedSide);
            }
        } else if (wasActive) {
            spikeEffect.stopSpikeTrail();
        }
    }

    public boolean isAttackLocked(boolean redSide) {
        return redSide ? redAttackLocked : blueAttackLocked;
    }

    public void lockAttack(boolean redSide) {
        if (redSide) redAttackLocked = true;
        else blueAttackLocked = true;
    }

    /** 僅權威物理落地流程呼叫；Client 以可靠 LANDING 事件播放。 */
    public void spawnLandingSmoke() {
        spikeEffect.spawnSmoke(ball.x, GameConfig.FLOOR_Y);
        landingSmokeThisFrame = true;
    }

    private void collideNet() {
        ballHitNetThisFrame = ball.collideWithNet(netHitBox);
        if (ballHitNetThisFrame) netFlashFrames = GameConfig.NET_FLASH_FRAMES;
    }

    private void updateFrame(TeamInput redInput, TeamInput blueInput) {
        blockTouchEffect.beginFrame();
        if (netFlashFrames > 0) netFlashFrames--;
        landingSmokeThisFrame = false;
        ballHitNetThisFrame = false;
        ballLandedThisFrame = false;
        setterContactThisFrame = false;
        airSetContactThisFrame = false;
        firstServeReceptionThisFrame = false;
        ballContactThisFrame = false;
        if (matchOver) {
            scorer.updateFinishedMatch(redInput, blueInput);
        } else if (scorer.isRallyOver()) {
            scorer.updateDeadBall(redInput, blueInput);
        } else {
            updateActiveFrame(redInput, blueInput);
        }
    }

    public void resetCounters() {
        rallyState.resetCounters();
    }

    /** 新發球清除兩隊攔網紀錄；來回中的重新計次由對手第一顆一般接球觸發。 */
    public void resetBlockJumpCounts() {
        redTeam.quickAttacker.resetBlockJumpCount();
        blueTeam.quickAttacker.resetBlockJumpCount();
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
        preparePossession(redSide);
        redAttackLocked = false;
        blueAttackLocked = false;

        if (!serveReceptionComplete && redSide != serveHandler.isRedServing()) {
            serveReceptionComplete = true;
            firstServeReceptionThisFrame = true;
        }

        boolean firstRegularTouch = serveReceptionComplete && getHitCount(redSide) == 0;
        recordContact(redSide, hitter, serveReceptionComplete);
        if (firstRegularTouch) {
            (redSide ? blueTeam : redTeam).quickAttacker.beginOpponentReceiveCycle();
        }
    }

    private void recordContact(boolean redSide, Player hitter, boolean counts) {
        if (matchOver) return;

        preparePossession(redSide);
        pendingTouchOut = false;
        pendingTouchOutWinner = null;
        resetTeamContacts(!redSide);

        rallyState.recordHit(redSide, hitter, counts);
        ballContactThisFrame = true;
        if (hitter instanceof Setter) {
            setterContactThisFrame = true;
        }

        if (counts && rallyState.getHitCount(redSide) > 3) {
            scorer.awardPointWithMessage(!redSide, "四觸違規");
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
        blockTouchEffect.show(redSide);
        ballContactThisFrame = true;
    }

    private void preparePossession(boolean redSide) {
        Boolean previousSide = getLastHitTeam();
        // 接球／攻擊造成球權交替時也重設，涵蓋碰撞推出球體跨網的情況。
        if (previousSide != null && previousSide != redSide && !rallyState.wasLastTouchBlock()) {
            resetCounters();
        }
    }

    private void updateActiveFrame(TeamInput redInput, TeamInput blueInput) {
        lastBallX = ball.x;

        redActionReleaseGate.filter(redInput);
        blueActionReleaseGate.filter(blueInput);
        serveHandler.updateBeforeTeams(redInput, blueInput);
        configureBackActions(redInput, blueInput);
        redInput.hasFirstRegularTouch = getHitCount(true) > 0;
        blueInput.hasFirstRegularTouch = getHitCount(false) > 0;
        redInput.opponentHasFirstRegularTouch = getHitCount(false) > 0;
        blueInput.opponentHasFirstRegularTouch = getHitCount(true) > 0;
        configureAttackInput(redInput, redTeam, true);
        configureAttackInput(blueInput, blueTeam, false);
        updateTeams(redInput, blueInput);
        boolean airSetContact = contactHandler.tryAirSetContact(redTeam, true)
                || contactHandler.tryAirSetContact(blueTeam, false);
        airSetContactThisFrame = airSetContact;
        for (Player player : redTeam.getPlayers()) {
            player.captureAttackAttemptBallOverlap(ball);
        }
        for (Player player : blueTeam.getPlayers()) {
            player.captureAttackAttemptBallOverlap(ball);
        }
        serveHandler.updateAfterTeams();

        updateBallIfNeeded();
        if (!airSetContact) {
            collideTeamsIfAllowed(redInput, blueInput);
        }

        if (ballLandedThisFrame) {
            if (ballContactThisFrame) {
                ballLandedThisFrame = false;
            } else if (!scorer.isRallyOver()) {
                scorer.checkBallLanding();
            }
        }

        serveHandler.finishFrame();
        spikeEffect.update();

        if (!scorer.isRallyOver()) {
            updateTransientMessage();
        }
    }

    public void updateDeadBallPlayers(TeamInput redInput, TeamInput blueInput) {
        BackActionResolver.apply(redInput, getHitCount(true));
        BackActionResolver.apply(blueInput, getHitCount(false));
        redInput.hasFirstRegularTouch = getHitCount(true) > 0;
        blueInput.hasFirstRegularTouch = getHitCount(false) > 0;
        redInput.opponentHasFirstRegularTouch = getHitCount(false) > 0;
        blueInput.opponentHasFirstRegularTouch = getHitCount(true) > 0;
        configureAttackInput(redInput, redTeam, true);
        configureAttackInput(blueInput, blueTeam, false);
        redInput.canResolveAttack = false;
        blueInput.canResolveAttack = false;
        updateTeams(redInput, blueInput);
    }

    /** 得分後球仍在移動時保留撞網；不重新判定觸球、落地得分或球權。 */
    public void updatePostPointBall() {
        boolean aboveFloor = ball.y + ball.radius < GameConfig.FLOOR_Y;
        ball.update();
        ballLandedThisFrame = aboveFloor && ball.y + ball.radius >= GameConfig.FLOOR_Y;
        collideNet();
    }

    public void observeLockedActions(TeamInput redInput, TeamInput blueInput) {
        redActionReleaseGate.observeLocked(redInput);
        blueActionReleaseGate.observeLocked(blueInput);
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
            BackActionResolver.apply(redInput, getHitCount(true));
        }

        if (serveHandler.shouldUseGameBackPlayerAction(false)) {
            BackActionResolver.apply(blueInput, getHitCount(false));
        }
    }

    private void configureAttackInput(TeamInput input, Team team, boolean redSide) {
        input.attackLocked = isAttackLocked(redSide);
        input.ball = ball;
        boolean secondTouch = getHitCount(redSide) == 1 && serveReceptionComplete;
        Player lastHitter = getLastHitter(redSide);
        input.lastHitter = lastHitter;
        input.canBackAirSet = secondTouch && lastHitter != team.backPlayer;
        input.canWingAirSet = secondTouch && lastHitter != team.wingSpiker;
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
        ballLandedThisFrame = ball.y + ball.radius >= GameConfig.FLOOR_Y;
        collideNet();

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

        if (scorer.isRallyOver()) {
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

    public boolean didAirSetContactThisFrame() {
        return airSetContactThisFrame;
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

    public RallyContactSnapshot captureRallyContacts() {
        return new RallyContactSnapshot(
                rallyState.captureTeamContacts(true, redTeam, redAttackLocked),
                rallyState.captureTeamContacts(false, blueTeam, blueAttackLocked),
                rallyState.getLastHitTeam(), rallyState.wasLastTouchBlock(), serveReceptionComplete);
    }

    public void applyNetworkRallyContactState(RallyContactSnapshot snapshot) {
        rallyState.applySnapshot(snapshot, redTeam, blueTeam);
        serveReceptionComplete = snapshot.serveReceptionComplete();
        redAttackLocked = snapshot.red().attackLocked();
        blueAttackLocked = snapshot.blue().attackLocked();
    }

    public void applyNetworkRallyPhase(boolean rallyOver, int deadBallTimer) {
        scorer.applyNetworkState(rallyOver, deadBallTimer);
        if (scorer.isLockedPhase()) {
            spikeEffect.clear();
            blockTouchEffect.clear();
            redTeam.backPlayer.resetRunAcceleration();
            blueTeam.backPlayer.resetRunAcceleration();
        }
    }

    // 外部呼叫：直接給點（例如四連擊、後排三米線違規）
    public void awardPoint(boolean redWins) {
        scorer.awardPoint(redWins);
    }
}
